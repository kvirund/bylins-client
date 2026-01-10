package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine

/**
 * Python движок (заглушка, требует GraalVM Python или Jython)
 *
 * Для полной реализации нужно:
 * 1. GraalVM Python: dependencies { implementation("org.graalvm.polyglot:polyglot:23.1.0") }
 * 2. Или Jython: dependencies { implementation("org.python:jython-standalone:2.7.3") }
 * 3. Или внешний процесс CPython
 */
class PythonEngine : ScriptEngine {
    override val name: String = "python"
    override val fileExtensions: List<String> = listOf(".py")

    override fun isAvailable(): Boolean {
        // TODO: Проверить доступность GraalVM Python или Jython
        // return try {
        //     Class.forName("org.graalvm.polyglot.Context")
        //     true
        // } catch (e: ClassNotFoundException) {
        //     false
        // }
        return false
    }

    override fun initialize(api: ScriptAPI) {
        // TODO: Инициализировать Python контекст
        // context = Context.newBuilder("python")
        //     .allowAllAccess(true)
        //     .build()
        // context.getBindings("python").putMember("api", api)
    }

    override fun loadScript(scriptPath: String): Script? {
        // TODO: Загрузить Python скрипт
        println("[PythonEngine] Python engine not implemented yet")
        return null
    }

    override fun execute(code: String) {
        // TODO: Выполнить Python код
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        // TODO: Вызвать Python функцию
        return null
    }

    override fun shutdown() {
        // TODO: Закрыть контекст
    }
}
