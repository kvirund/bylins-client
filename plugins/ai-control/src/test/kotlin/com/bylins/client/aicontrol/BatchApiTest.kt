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
 * Пакетные операции: create/update/delete принимают массив.
 *
 * Тест ходит настоящим HTTP к поднятому серверу плагина — именно на границе
 * HTTP и живёт разбор пакета, а сам клиент для этого не нужен: ClientControl
 * подменён заглушкой.
 */
class BatchApiTest {

    private val created = mutableListOf<String>()
    private val deleted = mutableListOf<String>()
    private val updates = mutableListOf<Pair<String, Map<String, Any?>>>()
    private val createdTabs = mutableListOf<String>()
    private val createdProfiles = mutableListOf<String>()

    /** Заглушка знает три правила; всё остальное — «не найдено». */
    private val knownRules = setOf("r1", "r2", "r3")

    private val control: ClientControl = Proxy.newProxyInstance(
        ClientControl::class.java.classLoader,
        arrayOf(ClientControl::class.java)
    ) { _, method, args ->
        when (method.name) {
            "createContextRule" -> "new-${created.size}".also { created.add(args[0] as String) }
            "deleteContextRule" -> (args[0] as String in knownRules).also { deleted.add(args[0] as String) }
            "updateContextRule" -> {
                @Suppress("UNCHECKED_CAST")
                updates.add((args[0] as String) to (args[1] as Map<String, Any?>))
                args[0] as String in knownRules
            }
            "createTab" -> "tab-${createdTabs.size}".also { createdTabs.add(args[0] as String) }
            "deleteTab" -> true
            "createConnectionProfile" -> "conn-${createdProfiles.size}".also { createdProfiles.add(args[0] as String) }
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

    private val port = 47593
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
    fun tearDown() {
        server.stop()
    }

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
    fun `create принимает массив правил`() {
        val (code, body) = post(
            "/client/context/rules/create",
            """{"items":[{"command":"откр дв с"},{"command":"откр дв ю"},{"command":"откр дв в"}]}"""
        )

        assertEquals(200, code)
        assertTrue(body.contains("\"batch\":true"), body)
        assertTrue(body.contains("\"total\":3"), body)
        assertTrue(body.contains("\"failed\":0"), body)
        assertEquals(listOf("откр дв с", "откр дв ю", "откр дв в"), created)
    }

    @Test
    fun `delete принимает массив id`() {
        val (code, body) = post("/client/context/rules/delete", """{"ids":["r1","r2"]}""")

        assertEquals(200, code)
        assertEquals(listOf("r1", "r2"), deleted)
        assertTrue(body.contains("\"failed\":0"), body)
    }

    @Test
    fun `ошибка одного элемента не отменяет остальные`() {
        val (code, body) = post("/client/context/rules/delete", """{"ids":["r1","нет-такого","r3"]}""")

        // Пакет всегда 200: иначе после массовой правки непонятно, что применилось
        assertEquals(200, code)
        assertEquals(listOf("r1", "нет-такого", "r3"), deleted)
        assertTrue(body.contains("\"total\":3"), body)
        assertTrue(body.contains("\"failed\":1"), body)
        assertTrue(body.contains("Контекстное правило не найдено: нет-такого"), body)
    }

    @Test
    fun `update переносит правила между профилями пакетом`() {
        val (code, body) = post(
            "/client/context/rules/update",
            """{"items":[{"id":"r1","profileId":"kuznec"},{"id":"r2","profileId":"kuznec"}]}"""
        )

        assertEquals(200, code)
        assertTrue(body.contains("\"failed\":0"), body)
        assertEquals(listOf("r1", "r2"), updates.map { it.first })
        assertTrue(updates.all { it.second["profileId"] == "kuznec" }, updates.toString())
    }

    @Test
    fun `update принимает scope`() {
        val (code, _) = post(
            "/client/context/rules/update",
            """{"id":"r1","scope":{"type":"zone","zones":["53"]}}"""
        )

        assertEquals(200, code)
        val scope = updates.single().second["scope"]
        assertTrue(scope is Map<*, *> && scope["type"] == "zone", scope.toString())
    }

    @Test
    fun `одиночный вызов работает как раньше`() {
        val (code, body) = post("/client/context/rules/create", """{"command":"смотреть"}""")

        assertEquals(200, code)
        assertTrue(body.contains("\"id\""), body)
        assertTrue(!body.contains("\"batch\""), body)
        assertEquals(listOf("смотреть"), created)
    }

    @Test
    fun `неизвестное поле в пакете отвечает ошибкой по элементу`() {
        val (code, body) = post(
            "/client/context/rules/update",
            """{"items":[{"id":"r1","prioriti":5}]}"""
        )

        assertEquals(200, code)
        assertTrue(body.contains("\"failed\":1"), body)
        assertTrue(body.contains("Неизвестные поля: prioriti"), body)
    }

    @Test
    fun `общее поле из корня применяется ко всему списку id`() {
        // Перенос девяноста правил в другой профиль — это одно и то же поле у
        // всех. Повторять его в каждом объекте значило слать килобайты
        // одинакового текста, а форма ids молча меняла ноль полей
        val (code, body) = post(
            "/client/context/rules/update",
            """{"ids":["r1","r2","r3"],"profileId":"былины"}"""
        )

        assertEquals(200, code, body)
        assertTrue(body.contains("\"failed\":0"), body)
        assertEquals(listOf("r1", "r2", "r3"), updates.map { it.first })
        assertTrue(updates.all { it.second["profileId"] == "былины" }, updates.toString())
        // applied показывает, что поле действительно применилось: раньше
        // единственным признаком беды был пустой список
        assertTrue(body.contains("\"applied\":[\"profileId\"]"), body)
    }

    @Test
    fun `общее поле сверяется так же строго, как в items`() {
        val (code, body) = post(
            "/client/context/rules/update",
            """{"ids":["r1","r2"],"profileld":"былины"}"""
        )

        assertEquals(200, code, body)
        assertTrue(body.contains("\"failed\":2"), body)
        assertTrue(body.contains("Неизвестные поля: profileld"), body)
        assertTrue(updates.isEmpty(), "правила изменены, несмотря на опечатку")
    }

    @Test
    fun `удаление принимает только id`() {
        // Поля из корня теперь доезжают до элементов, и лишнее у delete должно
        // быть слышно, а не проглатываться
        val (code, body) = post("/client/context/rules/delete", """{"ids":["r1"],"profileId":"былины"}""")

        assertEquals(200, code, body)
        assertTrue(body.contains("\"failed\":1"), body)
        assertTrue(deleted.isEmpty(), "правило удалено по запросу с лишним полем")
    }

    @Test
    fun `вкладки и профили подключения тоже принимают пакет`() {
        // Пакетность была неоднородной: у соседей есть, у этих нет, и агенту
        // приходилось помнить, где можно, а где надо звать поштучно
        val (tabsCode, tabsBody) = post(
            "/client/tabs/create",
            """{"items":[{"name":"Чат"},{"name":"Бой"}]}"""
        )
        val (profilesCode, profilesBody) = post(
            "/client/profiles/create",
            """{"items":[{"name":"основной","host":"bylins.su"},{"name":"зеркало","host":"mud.ru"}]}"""
        )

        assertEquals(200, tabsCode, tabsBody)
        assertEquals(listOf("Чат", "Бой"), createdTabs)
        assertEquals(200, profilesCode, profilesBody)
        assertEquals(listOf("основной", "зеркало"), createdProfiles)
    }

    @Test
    fun `вкладки удаляются пакетом`() {
        val (code, body) = post("/client/tabs/delete", """{"ids":["t1","t2"]}""")

        assertEquals(200, code, body)
        assertTrue(body.contains("\"total\":2"), body)
        assertTrue(body.contains("\"failed\":0"), body)
    }

    @Test
    fun `пустой пакет — ошибка запроса`() {
        val (code, body) = post("/client/context/rules/delete", """{"ids":[]}""")

        assertEquals(400, code)
        assertTrue(body.contains("Пустой пакет"), body)
    }
}
