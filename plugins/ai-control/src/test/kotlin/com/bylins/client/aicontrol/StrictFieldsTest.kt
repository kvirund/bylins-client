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
 * create проверяет поля так же строго, как update.
 *
 * Раньше create читал поля поимённо и всё незнакомое отбрасывал. Опечатка в
 * имени поля давала {"ok":true} и правило без части данных; двенадцать правил
 * так легли со скоупом world вместо зоны и начали срабатывать по всему миру.
 * Заметить это можно было только сверив profile.json с диска.
 */
class StrictFieldsTest {

    private val createdRules = mutableListOf<List<Any?>>()
    private val createdAliases = mutableListOf<List<Any?>>()
    private val createdHotkeys = mutableListOf<List<Any?>>()
    private val createdTriggers = mutableListOf<List<Any?>>()

    private val control: ClientControl = Proxy.newProxyInstance(
        ClientControl::class.java.classLoader,
        arrayOf(ClientControl::class.java)
    ) { _, method, args ->
        when (method.name) {
            "createContextRule" -> "rule".also { createdRules.add(args.toList()) }
            "createAlias" -> "alias".also { createdAliases.add(args.toList()) }
            "createHotkey" -> "hotkey".also { createdHotkeys.add(args.toList()) }
            "createTrigger" -> "trigger".also { createdTriggers.add(args.toList()) }
            "updateContextRule" -> true
            "isConnected" -> false
            else -> null
        }
    } as ClientControl

    private val api: PluginAPI = Proxy.newProxyInstance(
        PluginAPI::class.java.classLoader,
        arrayOf(PluginAPI::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getClient" -> control
            "hasPermission" -> true
            else -> null
        }
    } as PluginAPI

    private val port = 47594
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
    fun `опечатка в поле на create — отказ, а не молчаливая потеря`() {
        val (code, body) = post("/client/context/rules/create", """{"command":"пнуть","prioriti":5}""")

        assertEquals(400, code, body)
        assertTrue(body.contains("Неизвестные поля: prioriti"), body)
        assertTrue(createdRules.isEmpty(), "правило создано, несмотря на ошибку")
    }

    @Test
    fun `плоская форма scope отвергается, а не превращается в world`() {
        // Именно так скоуп записан в profile.json — и именно оттуда его переписывают
        val (code, body) = post(
            "/client/context/rules/create",
            """{"command":"пнуть","scope":"zone","zones":["41"]}"""
        )

        assertEquals(400, code, body)
        assertTrue(createdRules.isEmpty(), "правило создано без скоупа")
    }

    @Test
    fun `вложенная форма scope проходит и доходит до клиента`() {
        val (code, body) = post(
            "/client/context/rules/create",
            """{"command":"пнуть","scope":{"type":"zone","zones":["41"]}}"""
        )

        assertEquals(200, code, body)
        val scope = createdRules.single()[2]
        assertTrue(scope is Map<*, *> && scope["type"] == "zone", scope.toString())
    }

    @Test
    fun `scope неверной формы отвергается и на update`() {
        val (code, body) = post("/client/context/rules/update", """{"id":"r1","scope":"zone"}""")

        assertEquals(400, code, body)
        assertTrue(body.contains("scope должен быть объектом"), body)
    }

    @Test
    fun `хоткей не притворяется, что у него есть имя`() {
        // Поля name у хоткея нет: клавиша с модификаторами и есть его имя.
        // Параметр принимался и выбрасывался — теперь об этом говорится вслух
        val (code, body) = post("/client/hotkeys/create", """{"key":"F5","name":"атака","commands":["пнуть"]}""")

        assertEquals(400, code, body)
        assertTrue(body.contains("Неизвестные поля: name"), body)
        assertTrue(createdHotkeys.isEmpty())
    }

    @Test
    fun `приоритет алиаса доходит до клиента`() {
        // priority у алиаса есть в модели и в update, а create его не читал
        val (code, body) = post(
            "/client/aliases/create",
            """{"name":"бой","pattern":"^бой$","commands":["пнуть"],"priority":7}"""
        )

        assertEquals(200, code, body)
        assertEquals(7, createdAliases.single()[4], createdAliases.toString())
    }

    @Test
    fun `одноразовый триггер заводится через API`() {
        // Временный триггер иначе остаётся включённым навсегда и его надо
        // удалять отдельным вызовом — а поле once в модели есть давно
        val (code, body) = post(
            "/client/triggers/create",
            """{"name":"разовый","pattern":"посмотрели по сторонам","commands":["смотреть"],"once":true}"""
        )

        assertEquals(200, code, body)
        assertEquals(true, createdTriggers.single()[6], createdTriggers.toString())
    }

    @Test
    fun `правильный вызов create по-прежнему проходит`() {
        val (code, body) = post(
            "/client/context/rules/create",
            """{"command":"пнуть","pattern":"мышь","ttl":"room_change","priority":1,"enabled":true}"""
        )

        assertEquals(200, code, body)
        assertEquals(1, createdRules.size)
    }
}
