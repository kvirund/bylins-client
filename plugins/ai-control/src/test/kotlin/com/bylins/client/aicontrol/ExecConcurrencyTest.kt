package com.bylins.client.aicontrol

import com.bylins.client.plugins.ClientControl
import com.bylins.client.plugins.PluginAPI
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * /exec держит поток сервера, пока ждёт ответа MUD — до тридцати секунд.
 *
 * Потоков было четыре, и пара таких вызовов подвешивала весь сервер, включая
 * /status. При этом параллельный залп бессмыслен сам по себе: персонаж один, и
 * вывод двух команд всё равно перемешался бы.
 */
class ExecConcurrencyTest {

    private val sent = mutableListOf<String>()

    private val control: ClientControl = Proxy.newProxyInstance(
        ClientControl::class.java.classLoader,
        arrayOf(ClientControl::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "isConnected" -> true
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
            "send" -> { synchronized(sent) { sent.add(args[0] as String) }; null }
            else -> null
        }
    } as PluginAPI

    private val port = 47597
    private lateinit var sessions: SessionManager
    private lateinit var journal: OutputJournal
    private lateinit var server: AiHttpServer
    private lateinit var token: String

    @BeforeTest
    fun setUp() {
        journal = OutputJournal(100)
        sessions = SessionManager(api, journal, 60_000)
        server = AiHttpServer(api, sessions, journal, "MASTER", port) { }
        server.start()
        token = sessions.open("test").token
    }

    @AfterTest
    fun tearDown() = server.stop()

    private fun post(path: String, body: String, sessionToken: String = token): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("X-Session-Token", sessionToken)
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code < 400) connection.inputStream else connection.errorStream
        return code to stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `ответ приходит сразу после затишья, а не по таймауту`() {
        val pool = Executors.newSingleThreadExecutor()
        pool.submit {
            Thread.sleep(150)
            journal.append("Вы посмотрели по сторонам.", System.currentTimeMillis())
        }

        val started = System.currentTimeMillis()
        val (code, body) = post("/exec", """{"command":"смотреть","timeoutMs":10000,"quietMs":200}""")
        val elapsed = System.currentTimeMillis() - started
        pool.shutdown()

        assertEquals(200, code, body)
        assertTrue(body.contains("\"stoppedBy\":\"quiet\""), body)
        assertTrue(body.contains("посмотрели"), body)
        // 150 мс до строки + 200 мс тишины; в таймаут (10 с) упираться не должны
        assertTrue(elapsed < 3000, "ждали $elapsed мс вместо примерно 350")
    }

    @Test
    fun `второй залп не пускается, пока идёт первый`() {
        val pool = Executors.newFixedThreadPool(2)
        val firstStarted = CountDownLatch(1)
        val codes = java.util.Collections.synchronizedList(mutableListOf<Int>())

        pool.submit {
            firstStarted.countDown()
            codes.add(post("/exec", """{"command":"долгая","timeoutMs":1500,"quietMs":300}""").first)
        }
        firstStarted.await()
        Thread.sleep(200) // даём первому вызову дойти до ожидания
        val (second, body) = post("/exec", """{"command":"вторая","timeoutMs":1500}""")

        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(409, second, body)
        assertTrue(body.contains("ещё выполняется"), body)
        assertEquals(listOf(200), codes.toList())
        // Вторая команда до сервера не дошла — иначе смешалась бы с первой
        assertEquals(listOf("долгая"), sent.toList())
    }

    @Test
    fun `чтение отвечает, пока exec ждёт`() {
        val pool = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        pool.submit {
            started.countDown()
            post("/exec", """{"command":"долгая","timeoutMs":2000,"quietMs":300}""")
        }
        started.await()
        Thread.sleep(200)

        val (code, body) = post("/client/connected", "{}")
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(200, code, body)
    }

    @Test
    fun `слот освобождается после ответа`() {
        post("/exec", """{"command":"первая","timeoutMs":300}""")
        val (code, body) = post("/exec", """{"command":"вторая","timeoutMs":300}""")

        assertEquals(200, code, body)
        assertEquals(listOf("первая", "вторая"), sent.toList())
    }
}
