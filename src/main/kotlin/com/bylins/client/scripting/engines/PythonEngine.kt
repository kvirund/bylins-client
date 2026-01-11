package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine
import org.python.core.PyFunction
import org.python.util.PythonInterpreter
import java.io.File
import java.util.*

/**
 * Python движок (использует Jython)
 * Поддерживает Python 2.7 синтаксис
 */
class PythonEngine : ScriptEngine {
    override val name: String = "python"
    override val fileExtensions: List<String> = listOf(".py")

    private var interpreter: PythonInterpreter? = null
    private lateinit var api: ScriptAPI

    override fun isAvailable(): Boolean {
        return try {
            Class.forName("org.python.util.PythonInterpreter")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override fun initialize(api: ScriptAPI) {
        this.api = api

        try {
            interpreter = PythonInterpreter()

            // Добавляем API в контекст
            interpreter?.set("api", api)

            // Добавляем вспомогательные функции
            interpreter?.exec("""
# Глобальные функции для удобства
def send(command):
    api.send(command)

def echo(text):
    api.echo(text)

def log(message):
    api.log(message)

# Переменные
def get_var(name):
    return api.getVariable(name)

def set_var(name, value):
    api.setVariable(name, str(value))

# Триггеры
def add_trigger(pattern, callback):
    return api.addTrigger(pattern, callback)

# Алиасы
def add_alias(pattern, replacement):
    return api.addAlias(pattern, replacement)

# Таймеры
def set_timeout(callback, delay):
    return api.setTimeout(delay, callback)

def set_interval(callback, interval):
    return api.setInterval(interval, callback)

def clear_timer(timer_id):
    api.clearTimer(timer_id)
            """.trimIndent())
        } catch (e: Exception) {
            println("[PythonEngine] Error initializing Jython: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun loadScript(scriptPath: String): Script? {
        val file = File(scriptPath)
        if (!file.exists()) {
            println("[PythonEngine] Script not found: $scriptPath")
            return null
        }

        return try {
            val scriptCode = file.readText()
            interpreter?.exec(scriptCode)

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
            println("[PythonEngine] Error loading script: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override fun execute(code: String) {
        try {
            interpreter?.exec(code)
        } catch (e: Exception) {
            println("[PythonEngine] Error executing code: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        return try {
            val func = interpreter?.get(functionName)
            if (func is PyFunction) {
                // Конвертируем аргументы в Python объекты
                val pyArgs = args.map { interpreter?.eval(it?.toString() ?: "None") as org.python.core.PyObject }.toTypedArray()
                // __call__ принимает Array<PyObject>, а не vararg
                func.__call__(pyArgs)
            } else {
                null
            }
        } catch (e: Exception) {
            // Функция не найдена или ошибка вызова - это нормально
            null
        }
    }

    override fun shutdown() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            println("[PythonEngine] Error closing interpreter: ${e.message}")
        }
        interpreter = null
    }
}
