package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import java.util.*

/**
 * Lua движок (использует LuaJ)
 * Поддерживает Lua 5.2 синтаксис
 */
class LuaEngine : ScriptEngine {
    override val name: String = "lua"
    override val fileExtensions: List<String> = listOf(".lua")

    private var globals: Globals? = null
    private lateinit var api: ScriptAPI

    override fun isAvailable(): Boolean {
        return try {
            Class.forName("org.luaj.vm2.Globals")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override fun initialize(api: ScriptAPI) {
        this.api = api

        try {
            // Создаем стандартное окружение Lua
            globals = JsePlatform.standardGlobals()

            // Добавляем API в глобальный контекст
            globals?.set("api", CoerceJavaToLua.coerce(api))

            // Добавляем вспомогательные функции
            globals?.load("""
-- Глобальные функции для удобства
function send(command)
    api:send(command)
end

function echo(text)
    api:echo(text)
end

function log(message)
    api:log(message)
end

-- Переменные
function get_var(name)
    return api:getVariable(name)
end

function set_var(name, value)
    api:setVariable(name, tostring(value))
end

-- Триггеры
function add_trigger(pattern, callback)
    return api:addTrigger(pattern, callback)
end

-- Алиасы
function add_alias(pattern, replacement)
    return api:addAlias(pattern, replacement)
end

-- Таймеры
function set_timeout(callback, delay)
    return api:setTimeout(delay, callback)
end

function set_interval(callback, interval)
    return api:setInterval(interval, callback)
end

function clear_timer(timer_id)
    api:clearTimer(timer_id)
end
            """.trimIndent())?.call()
        } catch (e: Exception) {
            println("[LuaEngine] Error initializing LuaJ: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun loadScript(scriptPath: String): Script? {
        val file = File(scriptPath)
        if (!file.exists()) {
            println("[LuaEngine] Script not found: $scriptPath")
            return null
        }

        return try {
            val scriptCode = file.readText()
            globals?.load(scriptCode)?.call()

            // Вызываем on_load если есть
            callFunction("on_load", api)

            Script(
                id = UUID.randomUUID().toString(),
                name = file.nameWithoutExtension,
                path = scriptPath,
                engine = this,
                enabled = true
            )
        } catch (e: Exception) {
            println("[LuaEngine] Error loading script: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override fun execute(code: String) {
        try {
            globals?.load(code)?.call()
        } catch (e: Exception) {
            println("[LuaEngine] Error executing code: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        return try {
            val func = globals?.get(functionName)
            if (func != null && func.isfunction()) {
                // Конвертируем аргументы в Lua значения
                val luaArgs = args.map {
                    when (it) {
                        null -> LuaValue.NIL
                        is String -> LuaValue.valueOf(it)
                        is Number -> LuaValue.valueOf(it.toDouble())
                        is Boolean -> LuaValue.valueOf(it)
                        else -> CoerceJavaToLua.coerce(it)
                    }
                }.toTypedArray()

                func.invoke(luaArgs)?.tojstring()
            } else {
                null
            }
        } catch (e: Exception) {
            // Функция не найдена или ошибка вызова - это нормально
            null
        }
    }

    override fun shutdown() {
        globals = null
    }
}
