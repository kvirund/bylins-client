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
        srv.createContext("/session/lease", handler(::handleSessionLease))
        srv.createContext("/output", handler(::handleOutput))
        srv.createContext("/exec", handler(::handleExec))
        srv.createContext("/client", handler(::handleClient))
        srv.createContext("/map", handler(::handleMap))

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

    /**
     * Результат мутации: `false` от клиента означает «сущность не найдена».
     * Возвращать на это `{"ok":false}` бесполезно — вызывающий не отличит
     * «нет прав» от «неверный id», поэтому отвечаем 404 с текстом.
     */
    private fun mutated(result: Boolean, notFound: String): Any =
        if (result) mapOf("ok" to true) else throw ApiError(404, notFound)

    /**
     * Изменения для update-ручек: возвращаем список реально применённых полей,
     * иначе `{"ok":true}` приходит и когда не изменилось ничего.
     * Неизвестные ключи — ошибка: обычно это опечатка в имени поля.
     */
    private fun changesOf(req: JsonObject, known: Set<String>): Pair<Map<String, Any?>, List<String>> {
        val changes = req.toChanges().filterKeys { it != "id" }
        val unknown = changes.keys - known
        if (unknown.isNotEmpty()) {
            throw ApiError(400, "Неизвестные поля: ${unknown.joinToString(", ")}. Доступны: ${known.sorted().joinToString(", ")}")
        }
        return changes to changes.keys.toList()
    }

    private fun updated(applied: List<String>, ok: Boolean, notFound: String): Any {
        if (!ok) throw ApiError(404, notFound)
        return mapOf("ok" to true, "applied" to applied)
    }

    /**
     * Изменять состояние клиента может только сессия с правом записи —
     * иначе два агента незаметно перетирают настройки друг друга.
     */
    private fun requireWrite(session: AiSession) {
        if (sessions.canWrite(session)) return
        if (session.muted) throw ApiError(403, "Сессия заглушена игроком (#ai mute)")
        val holder = sessions.all().find { it.hasWriteLease }
        throw ApiError(
            403,
            "Нет права на запись: его держит сессия ${holder?.name ?: "?"} (${holder?.id ?: "-"}). " +
                "Запросите право через /session/lease или попросите игрока: #ai take <id>"
        )
    }

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

    /** Запрос права записи: отдаётся, если текущий держатель молчит. */
    private fun handleSessionLease(exchange: HttpExchange, req: JsonObject): Any {
        val session = requireSession(exchange)
        val granted = sessions.requestWriteLease(session)
        if (granted) audit("[${session.name}] взял право отправлять команды")
        return mapOf(
            "granted" to granted,
            "holder" to sessions.all().find { it.hasWriteLease }?.name
        )
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
    /**
     * Карта: где игрок, что вокруг, как дойти и что записано о комнатах.
     *
     * Чтение открыто любой сессии (агент и так видит вывод), а изменение карты
     * требует того же разрешения, что и остальное управление клиентом.
     */
    private fun handleMap(exchange: HttpExchange, req: JsonObject): Any {
        val session = requireSession(exchange)
        val action = exchange.requestURI.path.removePrefix("/map").trim('/')

        fun requireControl() {
            requireWrite(session)
            if (!api.hasPermission(com.bylins.client.plugins.PluginPermission.CLIENT_CONTROL)) {
                throw com.bylins.client.plugins.PluginPermissionDeniedException(
                    "ai-control", com.bylins.client.plugins.PluginPermission.CLIENT_CONTROL
                )
            }
        }

        return when (action) {
            // --- Чтение ---
            "", "room" -> mapOf("room" to api.getCurrentRoom())

            "get" -> mapOf(
                "room" to api.getRoom(req.str("id") ?: throw ApiError(400, "Нужно id"))
            )

            // Поиск по названию; с zoneId — все комнаты зоны, в том числе при
            // пустом запросе (раньше пустой query просто возвращал ничего)
            "search" -> {
                val zoneId = req.str("zoneId")
                val query = req.str("query").orEmpty()
                val rooms = when {
                    zoneId != null -> api.client.listZoneRooms(zoneId).let { list ->
                        if (query.isBlank()) list
                        else list.filter { (it["name"] as? String)?.contains(query, ignoreCase = true) == true }
                    }
                    query.isBlank() -> throw ApiError(400, "Нужно query или zoneId")
                    else -> api.searchRooms(query)
                }
                mapOf("rooms" to rooms)
            }

            // Путь до комнаты: направления для ходьбы и сами комнаты по порядку
            "path" -> {
                val target = req.str("targetRoomId") ?: throw ApiError(400, "Нужно targetRoomId")
                mapOf(
                    "directions" to api.findPath(target),
                    "roomIds" to api.findPathRoomIds(target)
                )
            }

            // Ближайшая комната со свойством или подстрокой в названии —
            // «где тут лавка» без ручного обхода карты
            "nearest" -> {
                val property = req.str("property")
                val nameContains = req.str("nameContains")?.lowercase()
                if (property == null && nameContains == null) {
                    throw ApiError(400, "Нужно property или nameContains")
                }
                val found = api.findNearestMatching { room ->
                    val byProperty = property?.let { key ->
                        @Suppress("UNCHECKED_CAST")
                        (room["properties"] as? Map<String, String>)?.containsKey(key) == true
                    } ?: false
                    val byName = nameContains?.let { part ->
                        (room["name"] as? String)?.lowercase()?.contains(part) == true
                    } ?: false
                    byProperty || byName
                }
                mapOf("room" to found?.first, "directions" to found?.second)
            }

            "properties" -> mapOf(
                "properties" to api.getRoomProperties(req.str("roomId") ?: throw ApiError(400, "Нужно roomId"))
            )

            // Зоны: метаданные, список и комнаты — раньше приходилось лезть в БД карты
            "zones" -> mapOf("zones" to api.client.listZones())

            "zone" -> api.client.getZone(req.str("zoneId") ?: throw ApiError(400, "Нужно zoneId"))

            "zone/rooms" -> mapOf(
                "rooms" to api.client.listZoneRooms(req.str("zoneId") ?: throw ApiError(400, "Нужно zoneId"))
            )

            "zone/note" -> {
                requireControl()
                val zoneId = req.str("zoneId") ?: throw ApiError(400, "Нужно zoneId")
                api.client.setZoneNote(zoneId, req.str("note") ?: "")
                audit("[${session.name}] заметка к зоне $zoneId")
                mapOf("ok" to true)
            }

            "zone/properties" -> mapOf(
                "properties" to api.getZoneProperties(req.str("zoneId") ?: throw ApiError(400, "Нужно zoneId"))
            )

            // --- Изменение (требует разрешения) ---
            "note" -> {
                requireControl()
                api.setRoomNote(
                    req.str("roomId") ?: throw ApiError(400, "Нужно roomId"),
                    req.str("note") ?: ""
                )
                audit("[${session.name}] заметка к комнате ${req.str("roomId")}")
                mapOf("ok" to true)
            }

            "property/set" -> {
                requireControl()
                api.setRoomProperty(
                    req.str("roomId") ?: throw ApiError(400, "Нужно roomId"),
                    req.str("key") ?: throw ApiError(400, "Нужно key"),
                    req.str("value") ?: ""
                )
                audit("[${session.name}] свойство комнаты ${req.str("roomId")}: ${req.str("key")}")
                mapOf("ok" to true)
            }

            "property/remove" -> {
                requireControl()
                api.removeRoomProperty(
                    req.str("roomId") ?: throw ApiError(400, "Нужно roomId"),
                    req.str("key") ?: throw ApiError(400, "Нужно key")
                )
                mapOf("ok" to true)
            }

            "zone/property/set" -> {
                requireControl()
                api.setZoneProperty(
                    req.str("zoneId") ?: throw ApiError(400, "Нужно zoneId"),
                    req.str("key") ?: throw ApiError(400, "Нужно key"),
                    req.str("value") ?: ""
                )
                mapOf("ok" to true)
            }

            // Подсветка маршрута на карте — чтобы игрок видел, куда ведёт агент
            "highlight" -> {
                requireControl()
                api.setPathHighlight(req.strList("roomIds"), req.str("targetRoomId"))
                mapOf("ok" to true)
            }

            "highlight/clear" -> {
                requireControl()
                api.clearPathHighlight()
                mapOf("ok" to true)
            }

            else -> throw ApiError(404, "Неизвестное действие карты: $action")
        }
    }

    /** Область действия из тела запроса: {"scope": {"type":"zone","zones":[...]}}. */
    private fun scopeOf(req: JsonObject): Map<String, Any?>? {
        val raw = req["scope"] as? JsonObject ?: return null
        return raw.toChanges()
    }

    private fun handleClient(exchange: HttpExchange, req: JsonObject): Any {
        val session = requireSession(exchange)
        val action = exchange.requestURI.path.removePrefix("/client/").trim('/')
        // Любое изменение состояния клиента — только с правом записи
        val isMutation = action.substringAfterLast('/') in
            setOf("create", "update", "delete", "select", "push", "pop", "requires", "start", "stop") ||
            action in setOf("connect", "disconnect")
        if (isMutation) requireWrite(session)
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
            "profiles/update" -> {
                val id = req.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(
                    req,
                    setOf("name", "host", "port", "encoding", "mapFile", "autoReconnect")
                )
                audited("изменён профиль", updated(applied, client.updateConnectionProfile(id, changes), "Профиль подключения не найден: $id"))
            }
            "profiles/delete" -> audited("удалён профиль", mutated(
                client.deleteConnectionProfile(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Профиль подключения не найден: ${req.str("id")}"
            ))
            "profiles/select" -> audited("выбран профиль", mutated(
                client.selectConnectionProfile(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Профиль подключения не найден: ${req.str("id")}"
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
                    profileId = req.str("profileId"),
                    scope = scopeOf(req)
                )
            ))
            "triggers/update" -> {
                val id = req.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(
                    req,
                    setOf("name", "pattern", "commands", "enabled", "gag", "priority", "scope")
                )
                audited("изменён триггер", updated(applied, client.updateTrigger(id, changes), "Триггер не найден: $id"))
            }
            "triggers/delete" -> audited("удалён триггер", mutated(
                client.deleteTrigger(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Триггер не найден: ${req.str("id")}"
            ))

            // Алиасы
            "aliases" -> mapOf("aliases" to client.listAliases())
            "aliases/create" -> audited("создан алиас '${req.str("name")}'", mapOf(
                "id" to client.createAlias(
                    name = req.str("name") ?: "ai-alias",
                    pattern = req.str("pattern") ?: throw ApiError(400, "Нужен pattern"),
                    commands = req.strList("commands"),
                    enabled = req.bool("enabled", true),
                    profileId = req.str("profileId")
                )
            ))
            "aliases/update" -> {
                val id = req.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(req, setOf("name", "pattern", "commands", "enabled", "priority"))
                audited("изменён алиас", updated(applied, client.updateAlias(id, changes), "Алиас не найден: $id"))
            }
            "aliases/delete" -> audited("удалён алиас", mutated(
                client.deleteAlias(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Алиас не найден: ${req.str("id")}"
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
                    enabled = req.bool("enabled", true),
                    profileId = req.str("profileId"),
                    scope = scopeOf(req)
                )
            ))
            "hotkeys/update" -> {
                val id = req.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(
                    req,
                    setOf("key", "ctrl", "alt", "shift", "commands", "enabled", "scope")
                )
                audited("изменён хоткей", updated(applied, client.updateHotkey(id, changes), "Хоткей не найден: $id"))
            }
            "hotkeys/delete" -> audited("удалён хоткей", mutated(
                client.deleteHotkey(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Хоткей не найден: ${req.str("id")}"
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
            "tabs/delete" -> audited("удалена вкладка", mutated(
                client.deleteTab(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Вкладка не найдена: ${req.str("id")}"
            ))

            // Контекстные команды (предложения в панели, не автоматические)
            "context/rules" -> mapOf("rules" to client.listContextRules())
            "context/rules/create" -> audited("создано контекстное правило", mapOf(
                "id" to client.createContextRule(
                    command = req.str("command") ?: throw ApiError(400, "Нужно command"),
                    pattern = req.str("pattern"),
                    scope = scopeOf(req),
                    ttl = req.str("ttl") ?: "room_change",
                    ttlMinutes = req["ttlMinutes"]?.let { req.int("ttlMinutes", 10) },
                    priority = req.int("priority", 0),
                    enabled = req.bool("enabled", true),
                    profileId = req.str("profileId")
                )
            ))
            "context/rules/delete" -> audited("удалено контекстное правило", mutated(
                client.deleteContextRule(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Контекстное правило не найдено: ${req.str("id")}"
            ))
            "context/queue" -> mapOf("queue" to client.listContextQueue())

            // Где игрок сейчас: комната и зона с человекочитаемой подписью
            "where" -> client.getLocation()

            // Настройки клиента
            "settings" -> mapOf("settings" to client.getSettings())
            "settings/update" -> audited("изменены настройки", mapOf(
                "applied" to client.updateSettings(req.toChanges())
            ))
            "logs" -> mapOf("log" to client.getLogInfo())
            "logs/start" -> audited("включено логирование", mapOf("ok" to true).also { client.setLogging(true) })
            "logs/stop" -> audited("выключено логирование", mapOf("ok" to true).also { client.setLogging(false) })

            // Профили персонажей
            "characters" -> mapOf("profiles" to client.listCharacterProfiles())
            "characters/create" -> audited("создан профиль персонажа '${req.str("name")}'", mapOf(
                "id" to client.createCharacterProfile(
                    name = req.str("name") ?: throw ApiError(400, "Нужно name"),
                    description = req.str("description") ?: "",
                    requires = req.strList("requires")
                )
            ))
            "characters/requires" -> audited("изменены зависимости профиля", mutated(
                client.setCharacterProfileDependencies(
                    req.str("id") ?: throw ApiError(400, "Нужно id"),
                    req.strList("requires")
                ),
                "Профиль персонажа не найден: ${req.str("id")}"
            ))
            "characters/push" -> audited("активирован профиль персонажа", mutated(
                client.pushCharacterProfile(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Профиль персонажа не найден: ${req.str("id")}"
            ))
            "characters/pop" -> audited("деактивирован профиль персонажа", mutated(
                client.popCharacterProfile(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Профиль не найден или не активен: ${req.str("id")}"
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
