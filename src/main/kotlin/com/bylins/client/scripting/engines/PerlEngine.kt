package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Perl движок (использует внешний perl интерпретатор)
 *
 * Требует установленный Perl в системе
 */
class PerlEngine : ScriptEngine {
    override val name: String = "perl"
    override val fileExtensions: List<String> = listOf(".pl")

    private lateinit var api: ScriptAPI

    override fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("perl", "--version")
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun initialize(api: ScriptAPI) {
        this.api = api
        // Perl использует внешний процесс, поэтому инициализация не требуется
    }

    override fun loadScript(scriptPath: String): Script? {
        // TODO: Реализовать загрузку Perl скрипта через IPC
        // Можно использовать JSON-RPC или простой stdin/stdout обмен
        println("[PerlEngine] Perl engine not fully implemented yet")
        return null
    }

    override fun execute(code: String) {
        // TODO: Выполнить Perl код через внешний процесс
        try {
            val process = ProcessBuilder("perl", "-e", code)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.lineSequence().forEach { line ->
                println("[Perl] $line")
            }

            process.waitFor()
        } catch (e: Exception) {
            println("[PerlEngine] Error executing code: ${e.message}")
        }
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        // TODO: Вызвать Perl функцию через IPC
        return null
    }

    override fun shutdown() {
        // Закрываем процессы если они есть
    }
}
