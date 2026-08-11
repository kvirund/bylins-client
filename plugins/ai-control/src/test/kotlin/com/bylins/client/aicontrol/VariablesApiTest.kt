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
 * Переменные клиента через API.
 *
 * На них держится вся параметризация конфига: ${first_attack} стоит в хоткее и
 * в сотне контекстных атак, ${target} ставят полсотни триггеров. Правила через
 * API завести было можно, а значения, на которые они ссылаются, — нет, и
 * профиль целиком не настраивался.
 */
class VariablesApiTest {

    private val variables = mutableMapOf("first_attack" to "пнуть")

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
            "getAllVariables" -> variables.toMap()
            "getVariable" -> variables[args[0] as String]
            "setVariable" -> variables.put(args[0] as String, args[1] as String).let { null }
            "deleteVariable" -> variables.remove(args[0] as String).let { null }
            else -> null
        }
    } as PluginAPI

    private val port = 47595
    private lateinit var sessions: SessionManager
    private lateinit var server: AiHttpServer
    private lateinit var token: String
    private val auditLog = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        sessions = SessionManager(api, OutputJournal(100), 60_000)
        server = AiHttpServer(api, sessions, OutputJournal(100), "MASTER", port) { auditLog.add(it) }
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
    fun `список переменных читается`() {
        val (code, body) = post("/client/variables", "{}")

        assertEquals(200, code, body)
        assertTrue(body.contains("first_attack"), body)
        assertTrue(body.contains("пнуть"), body)
    }

    @Test
    fun `переменная заводится`() {
        val (code, body) = post("/client/variables/set", """{"name":"light","value":"шар"}""")

        assertEquals(200, code, body)
        assertEquals("шар", variables["light"])
    }

    @Test
    fun `переменные заводятся пакетом`() {
        // Заводить их обычно надо несколько сразу — на профиль целиком
        val (code, body) = post(
            "/client/variables/set",
            """{"items":[{"name":"light","value":"шар"},{"name":"weapon","value":"секир"}]}"""
        )

        assertEquals(200, code, body)
        assertTrue(body.contains("\"failed\":0"), body)
        assertEquals("шар", variables["light"])
        assertEquals("секир", variables["weapon"])
    }

    @Test
    fun `переменная удаляется`() {
        val (code, body) = post("/client/variables/delete", """{"name":"first_attack"}""")

        assertEquals(200, code, body)
        assertTrue(variables.isEmpty(), variables.toString())
    }

    @Test
    fun `удаление несуществующей — ошибка, а не молчание`() {
        val (code, body) = post("/client/variables/delete", """{"name":"нет-такой"}""")

        assertEquals(404, code, body)
        assertTrue(body.contains("Переменная не найдена"), body)
    }

    @Test
    fun `опечатка в поле не проходит молча`() {
        val (code, body) = post("/client/variables/set", """{"name":"light","valeu":"шар"}""")

        assertEquals(400, code, body)
        assertTrue(body.contains("Неизвестные поля: valeu"), body)
        assertTrue(!variables.containsKey("light"), "переменная заведена без значения")
    }

    @Test
    fun `правка переменных попадает в лог игрока`() {
        // Переменная влияет на то, какие команды уйдут на сервер, — игрок
        // должен видеть, что агент их трогал
        post("/client/variables/set", """{"name":"light","value":"шар"}""")

        assertTrue(auditLog.any { it.contains("переменные") }, auditLog.toString())
    }
}
