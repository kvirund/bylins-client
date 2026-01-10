package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine

/**
 * Lua движок (заглушка, требует LuaJ)
 *
 * Для полной реализации нужно:
 * dependencies { implementation("org.luaj:luaj-jse:3.0.1") }
 */
class LuaEngine : ScriptEngine {
    override val name: String = "lua"
    override val fileExtensions: List<String> = listOf(".lua")

    override fun isAvailable(): Boolean {
        // TODO: Проверить доступность LuaJ
        // return try {
        //     Class.forName("org.luaj.vm2.Globals")
        //     true
        // } catch (e: ClassNotFoundException) {
        //     false
        // }
        return false
    }

    override fun initialize(api: ScriptAPI) {
        // TODO: Инициализировать Lua контекст
        // globals = JsePlatform.standardGlobals()
        // globals.set("api", CoerceJavaToLua.coerce(api))
    }

    override fun loadScript(scriptPath: String): Script? {
        // TODO: Загрузить Lua скрипт
        println("[LuaEngine] Lua engine not implemented yet")
        return null
    }

    override fun execute(code: String) {
        // TODO: Выполнить Lua код
        // globals.load(code).call()
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        // TODO: Вызвать Lua функцию
        // val func = globals.get(functionName)
        // return func.call()
        return null
    }

    override fun shutdown() {
        // TODO: Очистить контекст
    }
}
