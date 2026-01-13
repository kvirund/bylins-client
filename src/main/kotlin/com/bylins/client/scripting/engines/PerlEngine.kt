package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine
import com.bylins.client.scripting.ScriptStats
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger("Perl")

/**
 * Perl движок - использует внешний Perl интерпретатор через IPC (stdin/stdout JSON)
 *
 * Требования:
 * - Установленный Perl 5.10+ в системе
 * - Модуль JSON::PP (обычно включён в стандартную поставку Perl)
 */
class PerlEngine : ScriptEngine {
    override val name: String = "perl"
    override val fileExtensions: List<String> = listOf(".pl")

    private lateinit var api: ScriptAPI
    private var process: Process? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val running = AtomicBoolean(false)

    // Реестр callback'ов
    private val triggerCallbacks = ConcurrentHashMap<String, (String, Map<Int, String>) -> Unit>()
    private val timerCallbacks = ConcurrentHashMap<String, () -> Unit>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Загруженные скрипты (id -> Script)
    private val loadedScripts = ConcurrentHashMap<String, Script>()

    override fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("perl", "-v")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                // Проверяем наличие JSON::PP модуля
                val jsonCheck = ProcessBuilder("perl", "-MJSON::PP", "-e", "1")
                    .redirectErrorStream(true)
                    .start()
                jsonCheck.waitFor() == 0
            } else {
                false
            }
        } catch (e: Exception) {
            logger.debug { "Perl not available: ${e.message}" }
            false
        }
    }

    override fun initialize(api: ScriptAPI) {
        this.api = api
        startPerlProcess()
    }

    private fun startPerlProcess() {
        try {
            // Загружаем Perl wrapper script из ресурсов
            val wrapperScript = javaClass.getResourceAsStream("/perl_wrapper.pl")
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                ?: throw IllegalStateException("perl_wrapper.pl not found in resources")

            // Bootstrap: читает скрипт из stdin до маркера, затем eval
            val bootstrap = """my @lines; while (<STDIN>) { last if /^__WRAPPER_END__${'$'}/; push @lines, ${'$'}_; } eval join('', @lines); die ${'$'}@ if ${'$'}@;"""

            // Запускаем Perl с bootstrap'ом
            process = ProcessBuilder("perl", "-e", bootstrap)
                .redirectErrorStream(false)
                .start()

            val outputStream = process!!.outputStream

            // Отправляем wrapper скрипт и маркер конца
            outputStream.write(wrapperScript.toByteArray(Charsets.UTF_8))
            outputStream.write("\n__WRAPPER_END__\n".toByteArray(Charsets.UTF_8))
            outputStream.flush()

            writer = PrintWriter(OutputStreamWriter(outputStream, Charsets.UTF_8), true)
            reader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))

            // Читаем stderr в отдельном потоке
            Thread {
                BufferedReader(InputStreamReader(process!!.errorStream, Charsets.UTF_8)).use { errReader ->
                    errReader.lineSequence().forEach { line ->
                        logger.debug { "[Perl stderr] $line" }
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

            running.set(true)

            // Запускаем обработчик ответов
            Thread {
                processResponses()
            }.apply {
                isDaemon = true
                start()
            }

            // Ждём готовности Perl процесса (уменьшено с 100ms)
            Thread.sleep(50)

            logger.info { "Perl engine initialized" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start Perl process" }
        }
    }

    private var nextRequestId = 0
    private val pendingRequests = ConcurrentHashMap<Int, (Map<String, String>) -> Unit>()

    /**
     * Обрабатывает ответы от Perl процесса
     */
    private fun processResponses() {
        try {
            while (running.get() && process?.isAlive == true) {
                val line = reader?.readLine() ?: break
                if (line.isEmpty()) continue

                try {
                    val response = json.parseToJsonElement(line).jsonObject

                    // Проверяем, это ответ на наш запрос или API вызов от Perl
                    val responseTo = response["response_to"]?.jsonPrimitive?.intOrNull
                    if (responseTo != null) {
                        val callback = pendingRequests.remove(responseTo)
                        val data = extractStringMap(response["data"])
                        callback?.invoke(data)
                    } else {
                        // API вызов от Perl скрипта
                        handleApiCall(response)
                    }
                } catch (e: Exception) {
                    logger.debug { "Error parsing Perl response: ${e.message}, line: $line" }
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                logger.error(e) { "Error in Perl response processor" }
            }
        }
    }

    /**
     * Извлекает Map<String, String> из JsonElement
     */
    private fun extractStringMap(element: JsonElement?): Map<String, String> {
        if (element == null || element is JsonNull) return emptyMap()
        return try {
            element.jsonObject.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> v.content
                    else -> v.toString()
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Обрабатывает API вызовы от Perl скриптов
     */
    private fun handleApiCall(message: JsonObject) {
        val type = message["type"]?.jsonPrimitive?.content ?: return
        val data = extractStringMap(message["data"])
        val id = message["id"]?.jsonPrimitive?.intOrNull

        when (type) {
            "api_send" -> {
                val command = data["command"] ?: return
                api.send(command)
            }
            "api_echo" -> {
                val text = data["text"] ?: return
                api.echo(text)
            }
            "api_log" -> {
                val msg = data["message"] ?: return
                api.log(msg)
            }
            "api_get_var" -> {
                val name = data["name"] ?: ""
                val value = api.getVariable(name)
                if (id != null) {
                    sendResponse(id, mapOf("value" to (value ?: "")))
                }
            }
            "api_set_var" -> {
                val name = data["name"] ?: return
                val value = data["value"] ?: ""
                api.setVariable(name, value)
            }
            "api_add_trigger" -> {
                val pattern = data["pattern"] ?: return
                // Сначала регистрируем триггер чтобы получить triggerId
                var capturedTriggerId: String? = null
                val callback: (String, Map<Int, String>) -> Unit = { line, groups ->
                    // Отправляем событие триггера в Perl с правильным triggerId
                    capturedTriggerId?.let { tid ->
                        sendCommand("trigger_fired", mapOf(
                            "trigger_id" to tid,
                            "line" to line,
                            "groups" to groups.entries.joinToString("\u001F") { "${it.key}\u001E${it.value}" }
                        ))
                    }
                }
                val triggerId = api.addTrigger(pattern, callback)
                capturedTriggerId = triggerId
                triggerCallbacks[triggerId] = callback
                if (id != null) {
                    sendResponse(id, mapOf("trigger_id" to triggerId))
                }
            }
            "api_add_alias" -> {
                val pattern = data["pattern"] ?: return
                val replacement = data["replacement"] ?: return
                api.addAlias(pattern, replacement)
            }
            "api_set_timeout" -> {
                val delay = data["delay"]?.toLongOrNull() ?: return
                val callback: () -> Unit = {
                    sendCommand("timer_fired", mapOf("timer_id" to delay.toString()))
                }
                val timerId = api.setTimeout(delay, callback)
                timerCallbacks[timerId] = callback
                if (id != null) {
                    sendResponse(id, mapOf("timer_id" to timerId))
                }
            }
            "api_set_interval" -> {
                val interval = data["interval"]?.toLongOrNull() ?: return
                val callback: () -> Unit = {
                    sendCommand("timer_fired", mapOf("timer_id" to interval.toString()))
                }
                val timerId = api.setInterval(interval, callback)
                timerCallbacks[timerId] = callback
                if (id != null) {
                    sendResponse(id, mapOf("timer_id" to timerId))
                }
            }
            "api_clear_timer" -> {
                val timerId = data["timer_id"] ?: return
                api.clearTimer(timerId)
                timerCallbacks.remove(timerId)
            }
            // Mapper API
            "api_handle_movement" -> {
                val direction = data["direction"] ?: return
                val roomName = data["room_name"] ?: ""
                val exits = data["exits"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                val roomId = data["room_id"]?.takeIf { it.isNotEmpty() }
                val result = api.handleMovement(direction, roomName, exits, roomId)
                if (id != null) {
                    sendResponse(id, if (result != null) mapOf(
                        "id" to (result["id"]?.toString() ?: ""),
                        "name" to (result["name"]?.toString() ?: "")
                    ) else emptyMap())
                }
            }
            "api_get_current_room" -> {
                val room = api.getCurrentRoom()
                if (id != null) {
                    sendResponse(id, if (room != null) mapOf(
                        "id" to (room["id"]?.toString() ?: ""),
                        "name" to (room["name"]?.toString() ?: "")
                    ) else emptyMap())
                }
            }
            "api_create_room" -> {
                val roomId = data["room_id"] ?: return
                val roomName = data["room_name"] ?: ""
                val success = api.createRoom(roomId, roomName)
                if (id != null) {
                    sendResponse(id, mapOf("success" to success.toString()))
                }
            }
            "api_set_current_room" -> {
                val roomId = data["room_id"] ?: return
                api.setCurrentRoom(roomId)
            }
            "api_search_rooms" -> {
                val query = data["query"] ?: ""
                val rooms = api.searchRooms(query)
                if (id != null) {
                    sendResponse(id, mapOf("count" to rooms.size.toString()))
                }
            }
            "api_add_unexplored_exits" -> {
                val roomId = data["room_id"] ?: return
                val exits = data["exits"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                api.addUnexploredExits(roomId, exits)
            }
            "api_set_room_zone" -> {
                val roomId = data["room_id"] ?: return
                val zoneId = data["zone_id"] ?: return
                api.setRoomZone(roomId, zoneId)
            }
            "api_set_map_enabled" -> {
                val enabled = data["enabled"] == "true"
                api.setMapEnabled(enabled)
            }
        }
    }

    private fun sendCommand(type: String, data: Map<String, String>, id: Int? = null) {
        val msg = buildJsonObject {
            put("type", type)
            put("data", buildJsonObject {
                data.forEach { (k, v) -> put(k, v) }
            })
            if (id != null) {
                put("id", id)
            }
        }
        writer?.println(msg.toString())
    }

    private fun sendResponse(id: Int, data: Map<String, String>) {
        val msg = buildJsonObject {
            put("response_to", id)
            put("data", buildJsonObject {
                data.forEach { (k, v) -> put(k, v) }
            })
        }
        writer?.println(msg.toString())
    }

    private fun sendCommandSync(type: String, data: Map<String, String>, timeoutMs: Long = 500): Map<String, String> {
        val startTime = System.nanoTime()
        val id = ++nextRequestId
        var result: Map<String, String> = emptyMap()
        val latch = CountDownLatch(1)

        pendingRequests[id] = { response ->
            result = response
            latch.countDown()
        }

        sendCommand(type, data, id)

        // Ждём ответ с коротким таймаутом (500ms вместо 5 секунд)
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            pendingRequests.remove(id)
            logger.warn { "Timeout waiting for Perl response (${timeoutMs}ms): $type" }
        }

        val elapsed = System.nanoTime() - startTime
        ScriptStats.recordIpcCall("perl", type, elapsed)

        return result
    }

    override fun loadScript(scriptPath: String): Script? {
        val file = File(scriptPath)
        if (!file.exists()) {
            logger.warn { "Script not found: $scriptPath" }
            return null
        }

        return try {
            val code = file.readText(Charsets.UTF_8)
            val scriptId = UUID.randomUUID().toString()

            // Загрузка скрипта может занять больше времени
            val response = sendCommandSync("load_script", mapOf(
                "script_id" to scriptId,
                "code" to code
            ), timeoutMs = 2000)

            val success = response["success"] == "true" || response["success"] == "1"
            val error = response["error"]

            if (!success) {
                logger.error { "Failed to load Perl script: $error" }
                return null
            }

            val script = Script(
                id = scriptId,
                name = file.nameWithoutExtension,
                path = scriptPath,
                engine = this,
                enabled = true
            )

            loadedScripts[scriptId] = script
            logger.info { "Loaded Perl script: ${script.name}" }
            script
        } catch (e: Exception) {
            logger.error(e) { "Error loading Perl script: ${e.message}" }
            null
        }
    }

    override fun execute(code: String) {
        if (!running.get()) {
            logger.warn { "Perl engine not running" }
            return
        }

        try {
            val response = sendCommandSync("execute", mapOf("code" to code))
            val error = response["error"]
            if (!error.isNullOrEmpty()) {
                logger.error { "Perl execution error: $error" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error executing Perl code" }
        }
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        // Для Perl вызываем функцию во всех загруженных скриптах
        loadedScripts.values.forEach { script ->
            try {
                val response = sendCommandSync("call_function", mapOf(
                    "script_id" to script.id,
                    "function" to functionName,
                    "args" to args.joinToString(",") { it?.toString() ?: "" }
                ))
                val error = response["error"]
                if (!error.isNullOrEmpty()) {
                    logger.debug { "Error calling $functionName in ${script.name}: $error" }
                }
            } catch (e: Exception) {
                logger.debug { "Error calling $functionName: ${e.message}" }
            }
        }
        return null
    }

    override fun shutdown() {
        running.set(false)

        try {
            // Выгружаем все скрипты
            loadedScripts.keys.toList().forEach { scriptId ->
                sendCommand("unload_script", mapOf("script_id" to scriptId))
            }
            loadedScripts.clear()

            // Отправляем команду shutdown
            sendCommand("shutdown", emptyMap())

            // Даём время на завершение (уменьшено)
            Thread.sleep(50)

            // Закрываем потоки
            writer?.close()
            reader?.close()

            // Убиваем процесс если ещё жив
            process?.let {
                if (it.isAlive) {
                    it.destroyForcibly()
                }
            }
        } catch (e: Exception) {
            logger.debug { "Error during Perl engine shutdown: ${e.message}" }
        }

        process = null
        writer = null
        reader = null
        triggerCallbacks.clear()
        timerCallbacks.clear()

        logger.info { "Perl engine shutdown" }
    }
}
