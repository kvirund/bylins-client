package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine
import java.io.File
import java.util.*
import javax.script.Invocable
import javax.script.ScriptEngineManager

/**
 * JavaScript движок (использует Nashorn или GraalVM)
 */
class JavaScriptEngine : ScriptEngine {
    override val name: String = "javascript"
    override val fileExtensions: List<String> = listOf(".js")

    private var engine: javax.script.ScriptEngine? = null
    private lateinit var api: ScriptAPI

    override fun isAvailable(): Boolean {
        return try {
            val manager = ScriptEngineManager()
            // Пробуем найти JavaScript движок
            val testEngine = manager.getEngineByName("javascript")
                ?: manager.getEngineByName("nashorn")
                ?: manager.getEngineByName("graal.js")

            testEngine != null
        } catch (e: Exception) {
            false
        }
    }

    override fun initialize(api: ScriptAPI) {
        this.api = api

        val manager = ScriptEngineManager()
        engine = manager.getEngineByName("javascript")
            ?: manager.getEngineByName("nashorn")
            ?: manager.getEngineByName("graal.js")
            ?: throw IllegalStateException("No JavaScript engine available")

        // Добавляем API в глобальный контекст
        engine?.put("api", api)

        // Добавляем вспомогательные функции
        engine?.eval("""
            // Глобальные функции для удобства
            function send(command) { api.send(command); }
            function echo(text) { api.echo(text); }
            function log(message) { api.log(message); }
            function print(message) { api.print(message); }

            // Переменные
            function getVar(name) { return api.getVariable(name); }
            function setVar(name, value) { api.setVariable(name, value); }

            // Триггеры
            function addTrigger(pattern, callback) {
                return api.addTrigger(pattern, callback);
            }

            // Алиасы
            function addAlias(pattern, replacement) {
                return api.addAlias(pattern, replacement);
            }

            // Таймеры
            function setTimeout(callback, delay) {
                return api.setTimeout(delay, callback);
            }

            function setInterval(callback, interval) {
                return api.setInterval(interval, callback);
            }

            function clearTimer(id) {
                api.clearTimer(id);
            }
        """.trimIndent())
    }

    override fun loadScript(scriptPath: String): Script? {
        val file = File(scriptPath)
        if (!file.exists()) {
            println("[JavaScriptEngine] Script not found: $scriptPath")
            return null
        }

        return try {
            val scriptCode = file.readText()
            engine?.eval(scriptCode)

            Script(
                id = UUID.randomUUID().toString(),
                name = file.nameWithoutExtension,
                path = scriptPath,
                engine = this,
                enabled = true
            )
        } catch (e: Exception) {
            println("[JavaScriptEngine] Error loading script: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override fun execute(code: String) {
        try {
            engine?.eval(code)
        } catch (e: Exception) {
            println("[JavaScriptEngine] Error executing code: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        return try {
            val invocable = engine as? Invocable
            invocable?.invokeFunction(functionName, *args)
        } catch (e: NoSuchMethodException) {
            // Функция не найдена - это нормально
            null
        } catch (e: Exception) {
            println("[JavaScriptEngine] Error calling $functionName: ${e.message}")
            null
        }
    }

    override fun shutdown() {
        engine = null
    }
}
