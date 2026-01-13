package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine
import mu.KotlinLogging
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.script.Invocable
import javax.script.ScriptEngineManager

private val logger = KotlinLogging.logger("JavaScript")

/**
 * JavaScript движок (использует Nashorn или GraalVM)
 */
class JavaScriptEngine : ScriptEngine {
    override val name: String = "javascript"
    override val fileExtensions: List<String> = listOf(".js")

    private var engine: javax.script.ScriptEngine? = null
    private lateinit var api: ScriptAPI

    // Реестр callback'ов
    private val triggerCallbacks = ConcurrentHashMap<String, Any>()
    private val timerCallbacks = ConcurrentHashMap<String, Any>()

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

        // Добавляем хелперы для callback'ов
        engine?.put("_triggerHelper", TriggerHelper())
        engine?.put("_timerHelper", TimerHelper())

        // Загружаем вспомогательные функции из ресурсов
        val helperCode = javaClass.getResourceAsStream("/javascript_helper.js")
            ?.bufferedReader(Charsets.UTF_8)
            ?.readText()
            ?: throw IllegalStateException("javascript_helper.js not found in resources")
        engine?.eval(helperCode)
    }

    /**
     * Вызывает JavaScript callback через рефлексию (универсально для разных JS движков)
     */
    private fun invokeJsCallback(callback: Any, vararg args: Any?) {
        try {
            // Пробуем найти метод call(Object, Object...)
            val callMethod = callback.javaClass.getMethod("call", Object::class.java, Array<Any>::class.java)
            callMethod.invoke(callback, null, args)
        } catch (e: NoSuchMethodException) {
            try {
                // Альтернативный вариант - метод call с varargs
                val methods = callback.javaClass.methods.filter { it.name == "call" }
                for (method in methods) {
                    try {
                        if (method.parameterCount == 2) {
                            method.invoke(callback, null, args)
                            return
                        } else if (method.isVarArgs) {
                            method.invoke(callback, null, *args)
                            return
                        }
                    } catch (_: Exception) { }
                }
                logger.warn { "Could not find suitable call method" }
            } catch (ex: Exception) {
                logger.error { "Error invoking callback: ${ex.message}" }
            }
        } catch (e: Exception) {
            logger.error { "Error in callback: ${e.message}" }
        }
    }

    /**
     * Хелпер для регистрации триггеров с JavaScript callback'ами
     */
    inner class TriggerHelper {
        fun register(pattern: String, callback: Any): String {
            // DEBUG: проверяем что пришло из JavaScript
            val patternBytes = pattern.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }
            logger.debug { "TriggerHelper.register pattern='$pattern' bytes=[$patternBytes]" }

            val kotlinCallback: (String, Map<Int, String>) -> Unit = { line, groups ->
                invokeJsCallback(callback, line, groups)
            }

            val triggerId = api.addTrigger(pattern, kotlinCallback)
            triggerCallbacks[triggerId] = callback
            return triggerId
        }
    }

    /**
     * Хелпер для регистрации таймеров с JavaScript callback'ами
     */
    inner class TimerHelper {
        fun registerTimeout(callback: Any, delay: Long): String {
            val kotlinCallback: () -> Unit = {
                invokeJsCallback(callback)
            }

            val timerId = api.setTimeout(delay, kotlinCallback)
            timerCallbacks[timerId] = callback
            return timerId
        }

        fun registerInterval(callback: Any, interval: Long): String {
            val kotlinCallback: () -> Unit = {
                invokeJsCallback(callback)
            }

            val timerId = api.setInterval(interval, kotlinCallback)
            timerCallbacks[timerId] = callback
            return timerId
        }
    }

    override fun loadScript(scriptPath: String): Script? {
        val file = File(scriptPath)
        if (!file.exists()) {
            logger.warn { "Script not found: $scriptPath" }
            return null
        }

        return try {
            // Явно читаем скрипт в UTF-8
            val scriptCode = file.readText(Charsets.UTF_8)
            engine?.eval(scriptCode)

            Script(
                id = UUID.randomUUID().toString(),
                name = file.nameWithoutExtension,
                path = scriptPath,
                engine = this,
                enabled = true
            )
        } catch (e: Exception) {
            logger.error { "Error loading script: ${e.message}" }
            e.printStackTrace()
            null
        }
    }

    override fun execute(code: String) {
        try {
            engine?.eval(code)
        } catch (e: Exception) {
            logger.error { "Error executing code: ${e.message}" }
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
            logger.debug { "Error calling $functionName: ${e.message}" }
            null
        }
    }

    override fun shutdown() {
        engine = null
    }
}
