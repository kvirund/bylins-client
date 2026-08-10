package com.bylins.client.network

import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Буфер вывода правят несколько потоков: читающий сокет, UI (эхо команды игрока)
 * и потоки плагинов (команды от ИИ). Раньше правка была обычным
 * read-modify-write без замка, и одна запись молча затирала другую — пропадало
 * то эхо команды, то кусок вывода сервера.
 */
class TelnetClientBufferTest {

    private fun lines(text: String) = text.split("\n").filter { it.isNotEmpty() }

    @Test
    fun `параллельное эхо не теряет строк`() {
        val client = TelnetClient()
        val threads = 4
        val perThread = 300
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)

        repeat(threads) { t ->
            pool.submit {
                start.await()
                repeat(perThread) { i -> client.echoCommand("cmd-$t-$i") }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        assertEquals(threads * perThread, lines(client.receivedData.value).size)
    }

    @Test
    fun `эхо и локальный вывод не затирают друг друга`() {
        val client = TelnetClient()
        val count = 300
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)

        pool.submit {
            start.await()
            repeat(count) { i -> client.echoCommand("cmd-$i") }
        }
        pool.submit {
            start.await()
            repeat(count) { i -> client.addLocalOutput("out-$i") }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        val text = client.receivedData.value
        assertEquals(count, Regex("cmd-\\d+").findAll(text).count())
        assertEquals(count, Regex("out-\\d+").findAll(text).count())
    }

    @Test
    fun `снимок согласован с текстом буфера`() {
        val client = TelnetClient()
        repeat(50) { i -> client.echoCommand("cmd-$i") }

        val snapshot = client.snapshot.value
        assertEquals(client.receivedData.value, snapshot.text)
        assertEquals(50, lines(snapshot.text).size)
    }
}
