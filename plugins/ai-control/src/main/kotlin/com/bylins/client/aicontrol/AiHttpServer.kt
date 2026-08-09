package com.bylins.client.aicontrol

import com.bylins.client.aicontrol.ApiJson.bool
import com.bylins.client.aicontrol.ApiJson.int
import com.bylins.client.aicontrol.ApiJson.long
import com.bylins.client.aicontrol.ApiJson.str
import com.bylins.client.aicontrol.ApiJson.strList
import com.bylins.client.aicontrol.ApiJson.toChanges
import com.bylins.client.plugins.PluginAPI
import com.bylins.client.plugins.PluginPermissionDeniedException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger("AiHttpServer")

/**
 * Локальный HTTP+JSON сервер для ИИ-агентов.
 *
 * Слушает ТОЛЬКО 127.0.0.1: это удалённое управление персонажем, наружу его
 * выставлять нельзя. Авторизация двухуровневая:
 *  - мастер-токен (`X-Master-Token`) — открыть сессию и посмотреть статус;
 *  - токен сессии (`X-Session-Token`) — всё остальное, от имени агента.
 */
class AiHttpServer(
    private val api: PluginAPI,
    private val sessions: SessionManager,
    private val journal: OutputJournal,
    private val masterToken: String,
    private val port: Int,
    /** Куда писать действия агентов, чтобы игрок их видел. */
    private val audit: (String) -> Unit
) {
    private var server: HttpServer? = null

    val isRunning: Boolean get() = server != null

    fun start() {
        if (server != null) return
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        srv.executor = Executors.newFixedThreadPool(4)

        srv.createContext("/status", handler(::handleStatus))
        srv.createContext("/session/open", handler(::handleSessionOpen))
        srv.createContext("/session/close", handler(::handleSessionClose))
        srv.createContext("/session/list", handler(::handleSessionList))
        srv.createContext("/output", handler(::handleOutput))
        srv.createContext("/exec", handler(::handleExec))
        srv.createContext("/client", handler(::handleClient))

        srv.start()
        server = srv
        logger.info { "AI control server listening on 127.0.0.1:$port" }
    }

    fun stop() {
        server?.stop(0)
        server = null
        logger.info { "AI control server stopped" }
    }

    // --- Инфраструктура ---

    private fun handler(fn: (HttpExchange, JsonObject) -> Any?) = com.sun.net.httpserver.HttpHandler { exchange ->
        try {
            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val request = ApiJson.parseObject(body)
            val result = fn(exchange, request)
            respond(exchange, 200, result)
        } catch (e: PluginPermissionDeniedException) {
            // Пользователь не выдал право — агент должен получить внятный отказ
            respond(exchange, 403, mapOf("error" to e.message, "permission" to e.permission.id))
        } catch (e: ApiError) {
            respond(exchange, e.status, mapOf("error" to e.message))
        } catch (e: Exception) {
            logger.error(e) { "Request failed: ${exchange.requestURI}" }
            respond(exchange, 500, mapOf("error" to (e.message ?: e.javaClass.simpleName)))
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, payload: Any?) {
        val bytes = ApiJson.stringify(payload).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private class ApiError(val status: Int, message: String) : RuntimeException(message)

    private fun requireMaster(exchange: HttpExchange) {
        val token = exchange.requestHeaders.getFirst("X-Master-Token")
        if (token != masterToken) throw ApiError(401, "Неверный мастер-токен")
    }

    private fun requireSession(exchange: HttpExchange): AiSession {
        val token = exchange.requestHeaders.getFirst("X-Session-Token")
            ?: throw ApiError(401, "Нужен заголовок X-Session-Token")
        val session = sessions.byToken(token) ?: throw ApiError(401, "Сессия не найдена или закрыта")
        session.touch()
        return session
    }

    private fun query(exchange: HttpExchange): Map<String, String> =
        exchange.requestURI.query
            ?.split("&")
            ?.mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to
                    java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            }?.toMap() ?: emptyMap()

    // --- Обработчики ---

    private fun handleStatus(exchange: HttpExchange, req: JsonObject): Any {
        requireMaster(exchange)
        return mapOf(
            "connected" to runCatching { api.client.isConnected() }.getOrDefault(false),
            "journalHead" to journal.headSeq,
            "journalSize" to journal.size,
            "sessions" to sessions.all().map { it.describe() }
        )
    }

    private fun handleSessionOpen(exchange: HttpExchange, req: JsonObject): Any {
        requireMaster(exchange)
        val name = req.str("name") ?: "ai"
        val fromStart = req.bool("fromStart", false)
        val session = sessions.open(name, fromStart)
        audit("[$name] контекст открыт (id=${session.id})")
        return mapOf(
            "id" to session.id,
            "token" to session.token,
            "cursor" to session.cursorSeq,
            "hasWriteLease" to session.hasWriteLease
        )
    }

    private fun handleSessionClose(exchange: HttpExchange, req: JsonObject): Any {
        // Закрыть может как сам агент (своим токеном), так и владелец мастер-токеном
        val byMaster = exchange.requestHeaders.getFirst("X-Master-Token") == masterToken
        val id = if (byMaster) req.str("id") ?: throw ApiError(400, "Нужен id") else requireSession(exchange).id
        val name = sessions.get(id)?.name ?: id
        val closed = sessions.close(id)
        if (closed) audit("[$name] контекст закрыт, ресурсы освобождены")
        return mapOf("closed" to closed)
    }

    private fun handleSessionList(exchange: HttpExchange, req: JsonObject): Any {
        requireMaster(exchange)
        return mapOf("sessions" to sessions.all().map { it.describe() })
    }

    /**
     * Отдаёт вывод с курсора сессии и двигает курсор — это и есть «покажи, что
     * произошло с моей прошлой попытки».
     */
    private fun handleOutput(exchange: HttpExchange, req: JsonObject): Any {
        val session = requireSession(exchange)
        val q = query(exchange)
        val since = q["since"]?.toLongOrNull() ?: req.long("since", session.cursorSeq)
        val limit = q["limit"]?.toIntOrNull() ?: req.int("limit", 500)

        val result = journal.read(since, limit)
        session.cursorSeq = result.nextSeq
        return mapOf(
            "lines" to result.lines.map { it.text },
            "sinceSeq" to since,
            "nextSeq" to result.nextSeq,
            "missed" to result.missed
        )
    }

    /**
     * Отправляет команды и ждёт ответа: возвращается, когда вывод «затих»
     * (нет новых строк [quietMs]) или истёк [timeoutMs]. Так агент получает
     * готовый результат одним вызовом, не опрашивая /output в цикле.
     */
    private fun handleExec(exchange: HttpExchange, req: JsonObject): Any {
        val session = requireSession(exchange)
        val commands = req.strList("commands").ifEmpty {
            req.str("command")?.let { listOf(it) } ?: emptyList()
        }
        if (commands.isEmpty()) throw ApiError(400, "Нужны commands или command")

        if (!sessions.canWrite(session)) {
            session.countCommandRejected()
            throw ApiError(
                409,
                if (session.muted) "Сессия заглушена игроком"
                else "Право на отправку команд сейчас у другой сессии"
            )
        }

        val timeoutMs = req.long("timeoutMs", 3000).coerceIn(0, 30_000)
        val quietMs = req.long("quietMs", 400).coerceIn(50, 5_000)
        val startSeq = journal.headSeq

        commands.forEach { command ->
            api.send(command)
            session.countCommandSent()
            audit("[${session.name}] → $command")
        }

        // Ждём тишины: вывод MUD приходит порциями, «затихание» — простой и
        // надёжный признак, что ответ пришёл целиком.
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSeq = journal.headSeq
        var lastChange = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            val head = journal.headSeq
            if (head != lastSeq) {
                lastSeq = head
                lastChange = System.currentTimeMillis()
            } else if (head > startSeq && System.currentTimeMillis() - lastChange >= quietMs) {
                break
            }
        }

        val result = journal.read(startSeq, limit = 1000)
        session.cursorSeq = result.nextSeq
        return mapOf(
            "commands" to commands,
            "lines" to result.lines.map { it.text },
            "sinceSeq" to startSeq,
            "nextSeq" to result.nextSeq,
            "stoppedBy" to if (System.currentTimeMillis() >= deadline) "timeout" else "quiet"
        )
    }

    /**
     * Управление клиентом. Требует разрешений, выданных пользователем:
     * проверку делает сам PluginAPI.client, здесь только маршрутизация.
     */
    private fun handleClient(exchange: HttpExchange, req: JsonObject): Any {
        val session = requireSession(exchange)
        val action = exchange.requestURI.path.removePrefix("/client/").trim('/')
        val client = api.client

        fun audited(what: String, result: Any): Any {
            audit("[${session.name}] клиент: $what")
            return result
        }

        return when (action) {
            // Соединение
            "connect" -> audited("подключение", mapOf("ok" to true).also { client.connect(req.str("profileId")) })
            "disconnect" -> audited("отключение", mapOf("ok" to true).also { client.disconnect() })
            "connected" -> mapOf("connected" to client.isConnected())

            // Профили подключения
            "profiles" -> mapOf("profiles" to client.listConnectionProfiles())
            "profiles/create" -> audited("создан профиль '${req.str("name")}'", mapOf(
                "id" to client.createConnectionProfile(
                    name = req.str("name") ?: throw ApiError(400, "Нужно name"),
                    host = req.str("host") ?: throw ApiError(400, "Нужно host"),
                    port = req.int("port", 4000),
                    encoding = req.str("encoding") ?: "UTF-8",
                    mapFile = req.str("mapFile") ?: "maps.db",
                    autoReconnect = req.bool("autoReconnect", false)
                )
            ))
            "profiles/update" -> audited("изменён профиль", mapOf(
                "ok" to client.updateConnectionProfile(
                    req.str("id") ?: throw ApiError(400, "Нужно id"),
                    req.toChanges()
                )
            ))
            "profiles/delete" -> audited("удалён профиль", mapOf(
                "ok" to client.deleteConnectionProfile(req.str("id") ?: throw ApiError(400, "Нужно id"))
            ))
            "profiles/select" -> audited("выбран профиль", mapOf(
                "ok" to client.selectConnectionProfile(req.str("id") ?: throw ApiError(400, "Нужно id"))
            ))

            // Триггеры
            "triggers" -> mapOf("triggers" to client.listTriggers())
            "triggers/create" -> audited("создан триггер '${req.str("name")}'", mapOf(
                "id" to client.createTrigger(
                    name = req.str("name") ?: "ai-trigger",
                    pattern = req.str("pattern") ?: throw ApiError(400, "Нужен pattern"),
                    commands = req.strList("commands"),
                    enabled = req.bool("enabled", true),
                    gag = req.bool("gag", false),
                    priority = req.int("priority", 0),
                    profileId = req.str("profileId")
                )
            ))
            "triggers/update" -> audited("изменён триггер", mapOf(
                "ok" to client.updateTrigger(req.str("id") ?: throw ApiError(400, "Нужно id"), req.toChanges())
            ))
            "triggers/delete" -> audited("удалён триггер", mapOf(
                "ok" to client.deleteTrigger(req.str("id") ?: throw ApiError(400, "Нужно id"))
            ))

            // Алиасы
            "aliases" -> mapOf("aliases" to client.listAliases())
            "aliases/create" -> audited("создан алиас '${req.str("name")}'", mapOf(
                "id" to client.createAlias(
                    name = req.str("name") ?: "ai-alias",
                    pattern = req.str("pattern") ?: throw ApiError(400, "Нужен pattern"),
                    commands = req.strList("commands"),
                    enabled = req.bool("enabled", true)
                )
            ))
            "aliases/delete" -> audited("удалён алиас", mapOf(
                "ok" to client.deleteAlias(req.str("id") ?: throw ApiError(400, "Нужно id"))
            ))

            // Хоткеи
            "hotkeys" -> mapOf("hotkeys" to client.listHotkeys())
            "hotkeys/create" -> audited("создан хоткей '${req.str("key")}'", mapOf(
                "id" to client.createHotkey(
                    name = req.str("name") ?: "ai-hotkey",
                    key = req.str("key") ?: throw ApiError(400, "Нужна key"),
                    commands = req.strList("commands"),
                    ctrl = req.bool("ctrl", false),
                    alt = req.bool("alt", false),
                    shift = req.bool("shift", false),
                    enabled = req.bool("enabled", true)
                )
            ))
            "hotkeys/delete" -> audited("удалён хоткей", mapOf(
                "ok" to client.deleteHotkey(req.str("id") ?: throw ApiError(400, "Нужно id"))
            ))

            // Вкладки вывода
            "tabs" -> mapOf("tabs" to client.listTabs())
            "tabs/create" -> audited("создана вкладка '${req.str("name")}'", mapOf(
                "id" to client.createTab(
                    name = req.str("name") ?: throw ApiError(400, "Нужно name"),
                    patterns = req.strList("patterns"),
                    captureMode = req.str("captureMode") ?: "COPY",
                    profileTab = req.bool("profileTab", false),
                    profileLog = req.bool("profileLog", false),
                    persistContent = req.bool("persistContent", false)
                )
            ))
            "tabs/delete" -> audited("удалена вкладка", mapOf(
                "ok" to client.deleteTab(req.str("id") ?: throw ApiError(400, "Нужно id"))
            ))

            // Профили персонажей
            "characters" -> mapOf("profiles" to client.listCharacterProfiles())
            "characters/create" -> audited("создан профиль персонажа '${req.str("name")}'", mapOf(
                "id" to client.createCharacterProfile(
                    name = req.str("name") ?: throw ApiError(400, "Нужно name"),
                    description = req.str("description") ?: "",
                    requires = req.strList("requires")
                )
            ))
            "characters/requires" -> audited("изменены зависимости профиля", mapOf(
                "ok" to client.setCharacterProfileDependencies(
                    req.str("id") ?: throw ApiError(400, "Нужно id"),
                    req.strList("requires")
                )
            ))
            "characters/push" -> audited("активирован профиль персонажа", mapOf(
                "ok" to client.pushCharacterProfile(req.str("id") ?: throw ApiError(400, "Нужно id"))
            ))
            "characters/pop" -> audited("деактивирован профиль персонажа", mapOf(
                "ok" to client.popCharacterProfile(req.str("id") ?: throw ApiError(400, "Нужно id"))
            ))

            else -> throw ApiError(404, "Неизвестное действие: $action")
        }
    }
}

/** Описание сессии для статуса/списка (и для CLI игрока). */
internal fun AiSession.describe(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "cursor" to cursorSeq,
    "muted" to muted,
    "writeLease" to hasWriteLease,
    "createdAt" to createdAt,
    "lastSeenAt" to lastSeenAt,
    "commandsSent" to stats.commandsSent,
    "commandsRejected" to stats.commandsRejected,
    "triggers" to stats.triggers
)
