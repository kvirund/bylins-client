package com.bylins.client.aicontrol

import com.bylins.client.plugins.ClientControl
import com.bylins.client.plugins.PluginAPI
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Правка выходов карты через API.
 *
 * Действий про выходы не было вовсе: карта читала направления, но записать их
 * было нечем, и скрытые с тёмными проходами оставались только в заметках.
 * Единственным путём была прямая правка maps.manual.db — которая переживала
 * ровно до следующей записи комнаты и требовала перезапуска клиента.
 */
class MapExitApiTest {

    private val exits = mutableListOf<List<Any?>>()
    private var setExitError: String? = null
    private var removed = true

    private val control: ClientControl = Proxy.newProxyInstance(
        ClientControl::class.java.classLoader,
        arrayOf(ClientControl::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "isConnected" -> false
            else -> null
        }
    } as ClientControl

    private val api: PluginAPI = Proxy.newProxyInstance(
        PluginAPI::class.java.classLoader,
        arrayOf(PluginAPI::class.java)
    ) { _, method, args ->
        when (method.name) {
            "getClient" -> control
            "hasPermission" -> true
            "setRoomExit" -> { exits.add(args.toList()); setExitError }
            "removeRoomExit" -> { exits.add(args.toList()); removed }
            else -> null
        }
    } as PluginAPI

    private val port = 47598
    private lateinit var sessions: SessionManager
    private lateinit var server: AiHttpServer
    private lateinit var token: String

    @BeforeTest
    fun setUp() {
        sessions = SessionManager(api, OutputJournal(100), 60_000)
        server = AiHttpServer(api, sessions, OutputJournal(100), "MASTER", port) { }
        server.start()
        token = sessions.open("test").token
    }

    @AfterTest
    fun tearDown() = server.stop()

    private fun post(path: String, body: String): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("X-Session-Token", token)
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code < 400) connection.inputStream else connection.errorStream
        return code to stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `выход прописывается`() {
        val (code, body) = post(
            "/map/exit/set",
            """{"roomId":"4759","direction":"EAST","targetRoomId":"4760"}"""
        )

        assertEquals(200, code, body)
        assertTrue(body.contains("\"applied\":[\"exit\"]"), body)
        assertEquals(listOf("4759", "EAST", "4760", null, false), exits.single())
    }

    @Test
    fun `дверь и двусторонний проход доезжают`() {
        post(
            "/map/exit/set",
            """{"roomId":"4759","direction":"восток","targetRoomId":"4760","door":"калитка","both":true}"""
        )

        assertEquals(listOf("4759", "восток", "4760", "калитка", true), exits.single())
    }

    @Test
    fun `отказ клиента доходит текстом, а не пятисоткой`() {
        // Несуществующая цель — это работа агенту (сначала заведи комнату),
        // а не внутренняя ошибка сервера
        setExitError = "Комната назначения 9999 не найдена на карте: сначала заведите её"

        val (code, body) = post(
            "/map/exit/set",
            """{"roomId":"4759","direction":"EAST","targetRoomId":"9999"}"""
        )

        assertEquals(400, code, body)
        assertTrue(body.contains("9999"), body)
    }

    @Test
    fun `опечатка в поле не проходит молча`() {
        val (code, body) = post(
            "/map/exit/set",
            """{"roomId":"4759","direction":"EAST","targetRoom":"4760"}"""
        )

        assertEquals(400, code, body)
        assertTrue(body.contains("Неизвестные поля: targetRoom"), body)
        assertTrue(exits.isEmpty())
    }

    @Test
    fun `выход снимается`() {
        val (code, body) = post("/map/exit/remove", """{"roomId":"4759","direction":"EAST"}""")

        assertEquals(200, code, body)
        assertEquals(listOf("4759", "EAST"), exits.single())
    }

    @Test
    fun `снятие несуществующего выхода — 404`() {
        removed = false

        val (code, body) = post("/map/exit/remove", """{"roomId":"4759","direction":"EAST"}""")

        assertEquals(404, code, body)
    }

    @Test
    fun `схема знает про выходы`() {
        val (code, body) = post("/client/schema", "{}")

        assertEquals(200, code, body)
        assertTrue(body.contains("map/exit"), body)
        assertTrue(body.contains("targetRoomId"), body)
    }
}
