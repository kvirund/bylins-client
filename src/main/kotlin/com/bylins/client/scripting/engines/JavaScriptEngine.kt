package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptAPIImpl
import com.bylins.client.scripting.ScriptEngine
import mu.KotlinLogging
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.script.Bindings
import javax.script.Invocable
import javax.script.ScriptContext
import javax.script.ScriptEngineManager
import javax.script.SimpleScriptContext

private val logger = KotlinLogging.logger("JavaScript")

/**
 * JavaScript движок (использует Nashorn или GraalVM)
 * Каждый скрипт выполняется в изолированном контексте
 */
class JavaScriptEngine : ScriptEngine {
    override val name: String = "javascript"
    override val fileExtensions: List<String> = listOf(".js")

    private var engineManager: ScriptEngineManager? = null
    private lateinit var api: ScriptAPI

    // Храним контексты для каждого скрипта (scriptId -> context)
    private val scriptContexts = ConcurrentHashMap<String, ScriptContext>()

    // Реестр callback'ов
    private val triggerCallbacks = ConcurrentHashMap<String, Any>()
    private val timerCallbacks = ConcurrentHashMap<String, Any>()

    // Хелперы - создаются один раз
    private lateinit var triggerHelper: TriggerHelper
    private lateinit var timerHelper: TimerHelper
    private lateinit var mapperHelper: MapperHelper

    // Код хелперов - загружается один раз
    private var helperCode: String? = null

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
        this.engineManager = ScriptEngineManager()

        // Создаём хелперы
        triggerHelper = TriggerHelper()
        timerHelper = TimerHelper()
        mapperHelper = MapperHelper()

        // Загружаем код хелперов из ресурсов
        helperCode = javaClass.getResourceAsStream("/javascript_helper.js")
            ?.bufferedReader(Charsets.UTF_8)
            ?.readText()
            ?: throw IllegalStateException("javascript_helper.js not found in resources")
    }

    /**
     * Создаёт новый движок с изолированным контекстом для скрипта
     */
    private fun createEngineWithContext(scriptId: String): Pair<javax.script.ScriptEngine, ScriptContext> {
        val engine = engineManager!!.getEngineByName("javascript")
            ?: engineManager!!.getEngineByName("nashorn")
            ?: engineManager!!.getEngineByName("graal.js")
            ?: throw IllegalStateException("No JavaScript engine available")

        // Создаём изолированный контекст
        val context = SimpleScriptContext()
        val bindings = engine.createBindings()

        // Добавляем API и хелперы
        bindings["api"] = api
        bindings["_triggerHelper"] = triggerHelper
        bindings["_timerHelper"] = timerHelper
        bindings["_mapperHelper"] = mapperHelper
        bindings["_scriptId"] = scriptId
        bindings["_engine"] = engine  // Для передачи в mapperHelper

        context.setBindings(bindings, ScriptContext.ENGINE_SCOPE)
        engine.context = context

        // Загружаем хелперы в этот контекст
        engine.eval(helperCode, context)

        // Сохраняем контекст
        scriptContexts[scriptId] = context

        return Pair(engine, context)
    }

    /**
     * Вызывает JavaScript callback через рефлексию (универсально для разных JS движков)
     */
    private fun invokeJsCallback(callback: Any, vararg args: Any?) {
        logger.debug { "invokeJsCallback: callback=${callback.javaClass}, args=${args.map { it?.javaClass?.simpleName ?: "null" }}" }
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
                    } catch (innerEx: Exception) {
                        val cause = if (innerEx is java.lang.reflect.InvocationTargetException) innerEx.cause else innerEx
                        logger.error { "Error in callback method: ${cause?.message}" }
                        cause?.printStackTrace()
                    }
                }
                logger.warn { "Could not find suitable call method for ${callback.javaClass}" }
            } catch (ex: Exception) {
                logger.error { "Error invoking callback: ${ex.message}" }
                ex.printStackTrace()
            }
        } catch (e: Exception) {
            val cause = if (e is java.lang.reflect.InvocationTargetException) e.cause else e
            logger.error { "Error in callback: ${cause?.message}" }
            cause?.printStackTrace()
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

            val kotlinCallback: (String, Map<Int, String>) -> Unit = { _, groups ->
                // Конвертируем Map<Int, String> в массив для JavaScript
                // groups[0] = full match, groups[1] = group 1, etc.
                val maxIndex = groups.keys.maxOrNull() ?: -1
                val groupsArray = Array(maxIndex + 1) { index -> groups[index] ?: "" }
                invokeJsCallback(callback, groupsArray)
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

    /**
     * Converts a Kotlin Map to a JavaScript object via JSON
     */
    private fun mapToJsObject(engine: javax.script.ScriptEngine, map: Map<String, Any>): Any? {
        val json = kotlinMapToJson(map)
        return engine.eval("($json)")
    }

    private fun kotlinMapToJson(map: Map<*, *>): String {
        val sb = StringBuilder("{")
        var first = true
        for ((key, value) in map) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"").append(key).append("\":")
            sb.append(valueToJson(value))
        }
        sb.append("}")
        return sb.toString()
    }

    private fun valueToJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Map<*, *> -> kotlinMapToJson(value)
            is List<*> -> "[${value.joinToString(",") { valueToJson(it) }}]"
            is Set<*> -> "[${value.joinToString(",") { valueToJson(it) }}]"
            else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
    }

    /**
     * Хелпер для регистрации команд контекстного меню карты
     */
    inner class MapperHelper {
        private val mapCallbacks = ConcurrentHashMap<String, Pair<Any, javax.script.ScriptEngine>>()

        fun registerContextCommand(name: String, callback: Any, engine: javax.script.ScriptEngine) {
            mapCallbacks[name] = Pair(callback, engine)
            // Регистрируем обёртку, которая будет вызывать JS callback через движок
            val wrapperCallback: Any = object : Function1<Map<String, Any>, Unit> {
                override fun invoke(roomData: Map<String, Any>) {
                    val (jsCallback, jsEngine) = mapCallbacks[name] ?: return
                    // Convert Kotlin Map to JavaScript object
                    val jsObject = mapToJsObject(jsEngine, roomData)
                    if (jsObject != null) {
                        invokeJsCallback(jsCallback, jsObject)
                    } else {
                        logger.error { "Failed to convert room data to JS object" }
                    }
                }
            }
            api.registerMapCommand(name, wrapperCallback)
        }

        fun unregisterContextCommand(name: String) {
            mapCallbacks.remove(name)
            api.unregisterMapCommand(name)
        }
    }

    override fun loadScript(scriptPath: String): Script {
        val file = File(scriptPath)
        if (!file.exists()) {
            throw IllegalArgumentException("Script not found: $scriptPath")
        }

        val scriptId = UUID.randomUUID().toString()

        // Устанавливаем имя скрипта для логирования в API
        (api as? ScriptAPIImpl)?.currentScriptName = file.name

        // Создаём изолированный движок и контекст для этого скрипта
        val (engine, context) = createEngineWithContext(scriptId)

        // Явно читаем скрипт в UTF-8
        val scriptCode = file.readText(Charsets.UTF_8)

        try {
            engine.eval(scriptCode, context)
        } catch (e: Exception) {
            // Извлекаем понятное сообщение об ошибке
            val message = when (e) {
                is javax.script.ScriptException -> {
                    val lineInfo = if (e.lineNumber > 0) " (строка ${e.lineNumber})" else ""
                    "${e.message?.substringBefore(" in <eval>") ?: e.message}$lineInfo"
                }
                else -> e.message ?: "Unknown error"
            }
            scriptContexts.remove(scriptId)
            throw RuntimeException(message, e)
        }

        return Script(
            id = scriptId,
            name = file.nameWithoutExtension,
            path = scriptPath,
            engine = this,
            enabled = true,
            context = ScriptContextWrapper(engine, context)
        )
    }

    override fun execute(code: String) {
        // Для выполнения произвольного кода создаём временный контекст
        try {
            val engine = engineManager!!.getEngineByName("javascript")
                ?: engineManager!!.getEngineByName("nashorn")
                ?: engineManager!!.getEngineByName("graal.js")
            engine?.eval(code)
        } catch (e: Exception) {
            logger.error { "Error executing code: ${e.message}" }
            e.printStackTrace()
        }
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        // Этот метод вызывается через Script.call() с context
        // Не используется напрямую
        return null
    }

    /**
     * Вызывает функцию в контексте конкретного скрипта
     */
    fun callFunctionInContext(context: ScriptContextWrapper, functionName: String, vararg args: Any?): Any? {
        return try {
            val invocable = context.engine as? Invocable
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
        scriptContexts.clear()
        engineManager = null
    }

    /**
     * Удаляет контекст скрипта при выгрузке
     */
    fun removeScriptContext(scriptId: String) {
        scriptContexts.remove(scriptId)
    }
}

/**
 * Обёртка для хранения движка и контекста скрипта
 */
class ScriptContextWrapper(
    val engine: javax.script.ScriptEngine,
    val context: ScriptContext
)
