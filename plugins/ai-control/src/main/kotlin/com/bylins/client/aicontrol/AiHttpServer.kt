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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger("AiHttpServer")

/** Поля комнаты, доступные через /map/room/set. */
private val ROOM_FIELDS = setOf("name", "zone", "terrain", "visited", "notes", "color")

// Поля сущностей — один набор и на create, и на update. Держать их двумя
// списками уже пробовали: create читал поля поимённо, и списки разъехались
// молча — приходил {"ok":true}, а часть данных терялась по дороге.
// profileId переносит правило между профилями персонажей (null — в базовый
// набор), сохраняя id: иначе перенос означал бы удалить и создать заново, с
// новым id и ручной починкой всего, что на него ссылалось.
private val TRIGGER_FIELDS =
    setOf("name", "pattern", "commands", "enabled", "gag", "priority", "once", "scope", "profileId")
private val ALIAS_FIELDS =
    setOf("name", "pattern", "commands", "enabled", "priority", "profileId")
private val HOTKEY_FIELDS =
    setOf("key", "ctrl", "alt", "shift", "commands", "enabled", "scope", "profileId")
private val TAB_FIELDS =
    setOf("name", "patterns", "captureMode", "profileTab", "profileLog", "persistContent", "timestamps")
private val CONTEXT_RULE_FIELDS =
    setOf("command", "pattern", "scope", "ttl", "ttlMinutes", "priority", "enabled", "profileId")
private val VARIABLE_FIELDS = setOf("name", "value")

/** Адрес сущности, а не её данные: приходит вместе с полями, но не сверяется. */
private val ADDRESS_FIELDS = setOf("id", "roomId")

/** Потолок размера пакета: массовая правка не должна вешать клиент надолго. */
private const val MAX_BATCH = 500

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
     * Неизвестное поле — почти всегда опечатка в имени, и молча его терять
     * дороже, чем отказать: правило со скоупом world вместо зоны не падает и
     * не жалуется, оно просто начинает срабатывать не там.
     *
     * @param address поля-адреса (id и подобные) — они не данные, их не сверяем
     */
    private fun requireKnown(req: JsonObject, known: Set<String>, address: Set<String> = ADDRESS_FIELDS) {
        val unknown = req.keys - known - address
        if (unknown.isNotEmpty()) {
            throw ApiError(
                400,
                "Неизвестные поля: ${unknown.joinToString(", ")}. " +
                    "Доступны: ${known.sorted().joinToString(", ")}"
            )
        }
        requireScopeShape(req)
    }

    /**
     * Изменения для update-ручек: возвращаем список реально применённых полей,
     * иначе `{"ok":true}` приходит и когда не изменилось ничего.
     */
    private fun changesOf(req: JsonObject, known: Set<String>): Pair<Map<String, Any?>, List<String>> {
        requireKnown(req, known)
        // id/roomId — адрес сущности, а не изменяемое поле
        val changes = req.toChanges().filterKeys { it !in ADDRESS_FIELDS }
        return changes to changes.keys.toList()
    }

    private fun updated(applied: List<String>, ok: Boolean, notFound: String): Any {
        if (!ok) throw ApiError(404, notFound)
        return mapOf("ok" to true, "applied" to applied)
    }

    /** mutated/updated отдают Any ради одиночных ручек; пакету нужен Map. */
    @Suppress("UNCHECKED_CAST")
    private fun asMap(result: Any): Map<String, Any?> = result as Map<String, Any?>

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

            // Поля самой комнаты. Отдельная ручка нужна потому, что
            // property/set пишет только произвольные key/value, а на отрисовку
            // карты влияют именно поля: в комнату-ловушку (ДТ) не зайти, но
            // пометить её посещённой и подписать надо.
            "room/set" -> {
                requireControl()
                val roomId = req.str("roomId") ?: throw ApiError(400, "Нужно roomId")
                val (changes, applied) = changesOf(req, ROOM_FIELDS)
                if (changes.isEmpty()) throw ApiError(400, "Нечего менять: укажите ${ROOM_FIELDS.sorted().joinToString(", ")}")
                val result = updated(applied, api.updateRoom(roomId, changes), "Комната $roomId не найдена на карте")
                audit("[${session.name}] комната $roomId: ${applied.joinToString(", ")}")
                result
            }

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

            // Проход по маршруту. Слать направления самому через /exec —
            // верный способ разъехаться: клиент ведёт путь по шагам и сам
            // пересчитывает его, если персонажа унесло
            "walk" -> {
                requireControl()
                val target = req.str("targetRoomId") ?: throw ApiError(400, "Нужно targetRoomId")
                val started = api.startWalk(target)
                audit("[${session.name}] маршрут к $target")
                mapOf("walking" to started)
            }

            "walk/stop" -> {
                requireControl()
                api.stopWalk()
                audit("[${session.name}] маршрут остановлен")
                mapOf("ok" to true)
            }

            "walk/status" -> mapOf("walking" to api.isWalking())

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

    /**
     * Одиночный вызов или пакет.
     *
     * Пакет включается массивом `items` (объекты с полями действия) или `ids`
     * (для удаления). Поштучные ручки делали массовые правки неподъёмными:
     * перенос восьмидесяти правил между профилями — это сто шестьдесят вызовов
     * инструмента, и агент уходил править конфиг в обход клиента.
     *
     * Ошибка одного элемента не отменяет остальные: при массовой правке важнее
     * знать, что именно не прошло, чем потерять всю партию. Поэтому пакет
     * всегда отвечает 200, а исход каждого элемента лежит в results.
     */
    private fun batched(req: JsonObject, one: (JsonObject) -> Map<String, Any?>): Any {
        val items: List<JsonObject> = when {
            req["items"] is JsonArray -> (req["items"] as JsonArray).mapIndexed { i, element ->
                element as? JsonObject ?: throw ApiError(400, "items[$i] должен быть объектом")
            }
            req["ids"] is JsonArray -> (req["ids"] as JsonArray).mapIndexed { i, element ->
                val primitive = element as? JsonPrimitive ?: throw ApiError(400, "ids[$i] должен быть строкой")
                JsonObject(mapOf("id" to primitive))
            }
            // Обычный вызов с полями в корне — как было
            else -> return one(req)
        }
        if (items.isEmpty()) throw ApiError(400, "Пустой пакет: items/ids не должны быть пустыми")
        if (items.size > MAX_BATCH) throw ApiError(400, "Слишком большой пакет: ${items.size}, максимум $MAX_BATCH")

        val results = items.map { item ->
            runCatching { one(item) }.fold(
                onSuccess = { mapOf("ok" to true) + it },
                onFailure = { mapOf("ok" to false, "error" to (it.message ?: it.javaClass.simpleName)) }
            )
        }
        return mapOf(
            "batch" to true,
            "total" to results.size,
            "failed" to results.count { it["ok"] == false },
            "results" to results
        )
    }

    /**
     * Скоуп на входе — вложенный объект, как его отдают ручки чтения.
     *
     * В profile.json на диске лежит другая, плоская форма
     * (`"scope":"zone","zones":[...]`), и кто сверяется с файлом, пишет её.
     * Раньше такое тело давало `{"ok":true}` и правило вообще без скоупа —
     * то есть срабатывающее по всему миру.
     */
    private fun requireScopeShape(req: JsonObject) {
        val raw = req["scope"] ?: return
        if (raw is JsonNull || raw is JsonObject) return
        throw ApiError(
            400,
            "scope должен быть объектом: {\"type\":\"zone\",\"zones\":[\"41\"]} или " +
                "{\"type\":\"room\",\"roomIds\":[\"4056\"]}. Плоская форма из profile.json здесь не " +
                "принимается — образец есть в выдаче triggers/hotkeys/context/rules."
        )
    }

    /** Область действия из тела запроса: {"scope": {"type":"zone","zones":[...]}}. */
    private fun scopeOf(req: JsonObject): Map<String, Any?>? {
        requireScopeShape(req)
        val raw = req["scope"] as? JsonObject ?: return null
        return raw.toChanges()
    }

    private fun handleClient(exchange: HttpExchange, req: JsonObject): Any {
        val session = requireSession(exchange)
        val action = exchange.requestURI.path.removePrefix("/client/").trim('/')
        // Любое изменение состояния клиента — только с правом записи
        val isMutation = action.substringAfterLast('/') in
            setOf("create", "update", "delete", "select", "push", "pop", "requires", "start", "stop", "set") ||
            action in setOf("connect", "disconnect")
        if (isMutation) requireWrite(session)
        val client = api.client

        fun audited(what: String, result: Any): Any {
            // У пакета в лог игрока идут счётчики: «изменены триггеры» без них
            // не отличить правку одного правила от восьмидесяти
            val batch = (result as? Map<*, *>)?.takeIf { it["batch"] == true }
            val text = if (batch == null) what else {
                val failed = batch["failed"] as? Int ?: 0
                "$what: ${batch["total"]}" + if (failed > 0) ", с ошибками: $failed" else ""
            }
            audit("[${session.name}] клиент: $text")
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
            "triggers/create" -> audited("созданы триггеры", batched(req) { item ->
                requireKnown(item, TRIGGER_FIELDS)
                mapOf(
                    "id" to client.createTrigger(
                        name = item.str("name") ?: "ai-trigger",
                        pattern = item.str("pattern") ?: throw ApiError(400, "Нужен pattern"),
                        commands = item.strList("commands"),
                        enabled = item.bool("enabled", true),
                        gag = item.bool("gag", false),
                        priority = item.int("priority", 0),
                        once = item.bool("once", false),
                        profileId = item.str("profileId"),
                        scope = scopeOf(item)
                    )
                )
            })
            "triggers/update" -> audited("изменены триггеры", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(item, TRIGGER_FIELDS)
                asMap(updated(applied, client.updateTrigger(id, changes), "Триггер не найден: $id"))
            })
            "triggers/delete" -> audited("удалены триггеры", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                asMap(mutated(client.deleteTrigger(id), "Триггер не найден: $id"))
            })

            // Алиасы
            "aliases" -> mapOf("aliases" to client.listAliases())
            "aliases/create" -> audited("созданы алиасы", batched(req) { item ->
                requireKnown(item, ALIAS_FIELDS)
                mapOf(
                    "id" to client.createAlias(
                        name = item.str("name") ?: "ai-alias",
                        pattern = item.str("pattern") ?: throw ApiError(400, "Нужен pattern"),
                        commands = item.strList("commands"),
                        enabled = item.bool("enabled", true),
                        priority = item.int("priority", 0),
                        profileId = item.str("profileId")
                    )
                )
            })
            "aliases/update" -> audited("изменены алиасы", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(item, ALIAS_FIELDS)
                asMap(updated(applied, client.updateAlias(id, changes), "Алиас не найден: $id"))
            })
            "aliases/delete" -> audited("удалены алиасы", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                asMap(mutated(client.deleteAlias(id), "Алиас не найден: $id"))
            })

            // Хоткеи
            "hotkeys" -> mapOf("hotkeys" to client.listHotkeys())
            "hotkeys/create" -> audited("созданы хоткеи", batched(req) { item ->
                requireKnown(item, HOTKEY_FIELDS)
                mapOf(
                    "id" to client.createHotkey(
                        key = item.str("key") ?: throw ApiError(400, "Нужна key"),
                        commands = item.strList("commands"),
                        ctrl = item.bool("ctrl", false),
                        alt = item.bool("alt", false),
                        shift = item.bool("shift", false),
                        enabled = item.bool("enabled", true),
                        profileId = item.str("profileId"),
                        scope = scopeOf(item)
                    )
                )
            })
            "hotkeys/update" -> audited("изменены хоткеи", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(item, HOTKEY_FIELDS)
                asMap(updated(applied, client.updateHotkey(id, changes), "Хоткей не найден: $id"))
            })
            "hotkeys/delete" -> audited("удалены хоткеи", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                asMap(mutated(client.deleteHotkey(id), "Хоткей не найден: $id"))
            })

            // Вкладки вывода
            "tabs" -> mapOf("tabs" to client.listTabs())
            "tabs/create" -> audited("создана вкладка '${req.str("name")}'", run {
                requireKnown(req, TAB_FIELDS)
                mapOf(
                    "id" to client.createTab(
                        name = req.str("name") ?: throw ApiError(400, "Нужно name"),
                        patterns = req.strList("patterns"),
                        captureMode = req.str("captureMode") ?: "COPY",
                        profileTab = req.bool("profileTab", false),
                        profileLog = req.bool("profileLog", false),
                        persistContent = req.bool("persistContent", false),
                        timestamps = req.bool("timestamps", false)
                    )
                )
            })
            "tabs/update" -> audited("изменены вкладки", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(item, TAB_FIELDS)
                asMap(updated(applied, client.updateTab(id, changes), "Вкладка не найдена: $id"))
            })
            "tabs/delete" -> audited("удалена вкладка", mutated(
                client.deleteTab(req.str("id") ?: throw ApiError(400, "Нужно id")),
                "Вкладка не найдена: ${req.str("id")}"
            ))

            // Контекстные команды (предложения в панели, не автоматические)
            "context/rules" -> mapOf("rules" to client.listContextRules())
            "context/rules/create" -> audited("созданы контекстные правила", batched(req) { item ->
                requireKnown(item, CONTEXT_RULE_FIELDS)
                mapOf(
                    "id" to client.createContextRule(
                        command = item.str("command") ?: throw ApiError(400, "Нужно command"),
                        pattern = item.str("pattern"),
                        scope = scopeOf(item),
                        ttl = item.str("ttl") ?: "room_change",
                        ttlMinutes = item["ttlMinutes"]?.let { item.int("ttlMinutes", 10) },
                        priority = item.int("priority", 0),
                        enabled = item.bool("enabled", true),
                        profileId = item.str("profileId")
                    )
                )
            })
            // profileId здесь переносит правило между профилями, сохраняя id
            "context/rules/update" -> audited("изменены контекстные правила", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                val (changes, applied) = changesOf(item, CONTEXT_RULE_FIELDS)
                asMap(updated(applied, client.updateContextRule(id, changes), "Контекстное правило не найдено: $id"))
            })
            "context/rules/delete" -> audited("удалены контекстные правила", batched(req) { item ->
                val id = item.str("id") ?: throw ApiError(400, "Нужно id")
                asMap(mutated(client.deleteContextRule(id), "Контекстное правило не найдено: $id"))
            })
            "context/queue" -> mapOf("queue" to client.listContextQueue())

            // Переменные клиента: ${target}, ${first_attack} и прочее, на что
            // ссылаются команды правил. Без них профиль через API не настроить
            // целиком: правила завести можно, а значения для них — нет.
            "variables" -> mapOf("variables" to api.getAllVariables())
            "variables/set" -> audited("заданы переменные", batched(req) { item ->
                requireKnown(item, VARIABLE_FIELDS)
                val name = item.str("name") ?: throw ApiError(400, "Нужно name")
                val value = item.str("value") ?: throw ApiError(400, "Нужно value")
                api.setVariable(name, value)
                mapOf("name" to name)
            })
            "variables/delete" -> audited("удалены переменные", batched(req) { item ->
                requireKnown(item, setOf("name"))
                val name = item.str("name") ?: throw ApiError(400, "Нужно name")
                // Молчаливое удаление несуществующей переменной скрыло бы опечатку
                if (api.getVariable(name) == null) throw ApiError(404, "Переменная не найдена: $name")
                api.deleteVariable(name)
                mapOf("name" to name)
            })

            // Где игрок сейчас: комната и зона с человекочитаемой подписью
            "where" -> client.getLocation()

            // Снимок MSDP: структурированные данные вместо парсинга текста.
            // vars — выборочно, иначе всё, что прислал сервер.
            "msdp" -> client.getMsdp(req.strList("vars").ifEmpty { null })

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
