package com.bylins.client.aicontrol

import com.bylins.client.plugins.ClientControl
import com.bylins.client.plugins.PluginAPI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Поле, объявленное в схеме, должно доезжать до клиента.
 *
 * Схема сущности жила в четырёх местах сразу — список полей, чтение в create,
 * подпись в ClientControl, выдача в list — и нигде не сверялась. Разъезжались
 * они молча: у алиаса priority был в списке и в update, но create его не читал,
 * а хоткей принимал name, которого у него нет вовсе. Компилятор на такое не
 * ругается, ответ приходит {"ok":true}.
 *
 * Здесь список полей берётся из самой схемы, а значения подобраны отличными от
 * умолчаний: непрочитанное поле придёт в клиент своим умолчанием, и сверка
 * аргументов это покажет.
 */
class SchemaConformanceTest {

    private val calls = mutableMapOf<String, List<Any?>>()

    private val control: ClientControl = Proxy.newProxyInstance(
        ClientControl::class.java.classLoader,
        arrayOf(ClientControl::class.java)
    ) { _, method, args ->
        when (method.name) {
            "createTrigger", "createAlias", "createHotkey", "createTab", "createContextRule" -> {
                calls[method.name] = args.toList()
                "new-id"
            }
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
            "setVariable" -> { calls["setVariable"] = args.toList(); null }
            "getAllVariables" -> emptyMap<String, String>()
            else -> null
        }
    } as PluginAPI

    private val port = 47596
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

    private fun post(path: String, body: String, sessionToken: String? = token): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        sessionToken?.let { connection.setRequestProperty("X-Session-Token", it) }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code < 400) connection.inputStream else connection.errorStream
        return code to stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Поля сущности так, как их объявляет сам API. */
    private fun schemaFields(entity: String): Set<String> {
        val (code, body) = post("/client/schema", "{}")
        assertEquals(200, code, body)
        val entities = Json.parseToJsonElement(body).jsonObject["entities"]!!.jsonObject
        val fields = entities[entity]?.jsonObject ?: error("в схеме нет сущности «$entity»")
        return fields["fields"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
    }

    /**
     * Отправляет тело и сверяет, что оно покрывает схему целиком, а клиент
     * получил ровно то, что послано.
     */
    private fun checkEntity(entity: String, path: String, body: String, call: String, expected: List<Any?>) {
        val sent = Json.parseToJsonElement(body).jsonObject.keys
        assertEquals(
            schemaFields(entity), sent,
            "тело не покрывает схему «$entity» — допишите поле в проверку"
        )

        val (code, response) = post(path, body)
        assertEquals(200, code, response)
        assertEquals(expected, calls[call], "поле из схемы не доехало до клиента: $entity")
    }

    @Test
    fun `все поля триггера доезжают до клиента`() {
        checkEntity(
            entity = "triggers",
            path = "/client/triggers/create",
            body = """{"name":"т","pattern":"п","commands":["к"],"enabled":false,"gag":true,
                       "priority":7,"once":true,"profileId":"пр",
                       "scope":{"type":"zone","zones":["41"]}}""".trimIndent().replace("\n", ""),
            call = "createTrigger",
            expected = listOf(
                "т", "п", listOf("к"), false, true, 7, true, "пр",
                mapOf("type" to "zone", "zones" to listOf("41"))
            )
        )
    }

    @Test
    fun `все поля алиаса доезжают до клиента`() {
        checkEntity(
            entity = "aliases",
            path = "/client/aliases/create",
            body = """{"name":"а","pattern":"п","commands":["к"],"enabled":false,"priority":7,"profileId":"пр"}""",
            call = "createAlias",
            expected = listOf("а", "п", listOf("к"), false, 7, "пр")
        )
    }

    @Test
    fun `все поля хоткея доезжают до клиента`() {
        checkEntity(
            entity = "hotkeys",
            path = "/client/hotkeys/create",
            body = """{"key":"F5","commands":["к"],"ctrl":true,"alt":true,"shift":true,"enabled":false,
                       "profileId":"пр","scope":{"type":"world"}}""".trimIndent().replace("\n", ""),
            call = "createHotkey",
            expected = listOf("F5", listOf("к"), true, true, true, false, "пр", mapOf("type" to "world"))
        )
    }

    @Test
    fun `все поля вкладки доезжают до клиента`() {
        checkEntity(
            entity = "tabs",
            path = "/client/tabs/create",
            body = """{"name":"в","patterns":["п"],"captureMode":"MOVE","profileTab":true,
                       "profileLog":true,"persistContent":true,"timestamps":true}""".trimIndent().replace("\n", ""),
            call = "createTab",
            expected = listOf("в", listOf("п"), "MOVE", true, true, true, true)
        )
    }

    @Test
    fun `все поля контекстного правила доезжают до клиента`() {
        checkEntity(
            entity = "context/rules",
            path = "/client/context/rules/create",
            body = """{"command":"пнуть","pattern":"мышь","scope":{"type":"world"},"ttl":"timed",
                       "ttlMinutes":5,"priority":7,"enabled":false,"profileId":"пр"}""".trimIndent().replace("\n", ""),
            call = "createContextRule",
            expected = listOf("пнуть", "мышь", mapOf("type" to "world"), "timed", 5, 7, false, "пр")
        )
    }

    @Test
    fun `все поля переменной доезжают до клиента`() {
        checkEntity(
            entity = "variables",
            path = "/client/variables/set",
            body = """{"name":"light","value":"шар"}""",
            call = "setVariable",
            expected = listOf("light", "шар")
        )
    }

    @Test
    fun `неизвестное действие считается изменяющим, а не читающим`() {
        // Право узнаётся по закрытому списку читающих действий: действие,
        // о котором забыли, должно упереться в право записи, а не проскочить
        // Право записи у первой сессии; вторая менять ничего не вправе
        val readOnly = sessions.open("наблюдатель")

        val (code, body) = post("/client/characters/rename", """{"id":"x"}""", readOnly.token)

        assertEquals(403, code, body)
    }

    @Test
    fun `схема перечисляет читающие действия`() {
        val (code, body) = post("/client/schema", "{}")

        assertEquals(200, code, body)
        val schema = Json.parseToJsonElement(body).jsonObject
        val readOnly = schema["readOnly"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(readOnly.contains("triggers"), readOnly.toString())
        assertTrue(!readOnly.contains("triggers/create"), readOnly.toString())
    }

    @Test
    fun `схема доступна и без права записи`() {
        val observer = sessions.open("наблюдатель")

        val (code, _) = post("/client/schema", "{}", observer.token)

        assertEquals(200, code)
    }

    @Test
    fun `схема показывает форму скоупа`() {
        val (_, body) = post("/client/schema", "{}")

        val scope = Json.parseToJsonElement(body).jsonObject["scope"]!!.jsonObject
        val zone = scope["zone"]!!.jsonObject
        assertEquals("zone", zone["type"]!!.jsonPrimitive.content)
        assertTrue(zone["zones"] is kotlinx.serialization.json.JsonArray, zone.toString())
    }
}
