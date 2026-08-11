package com.bylins.client.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Команды не должны наслаиваться друг на друга при записи в сокет.
 *
 * Раньше запись шла без замка, а каждая команда уходила своей корутиной в пул
 * IO. Пачка команд от ИИ приходила серверу перемешанной или склеенной — часть
 * команд для игрока просто пропадала.
 */
class TelnetSendOrderTest {

    /** Поток, который пишет по одному байту с задержкой — так гонка проявляется. */
    private class SlowStream : OutputStream() {
        val collected = ByteArrayOutputStream()

        override fun write(b: Int) {
            collected.write(b)
        }

        override fun write(b: ByteArray) {
            // Побайтно и с уступкой планировщику: сплошной write атомарен сам по
            // себе и гонку бы скрыл
            for (byte in b) {
                collected.write(byte.toInt())
                Thread.yield()
            }
        }
    }

    /** Подсовывает клиенту наш поток вместо сокета. */
    private fun clientWith(stream: OutputStream): TelnetClient {
        val client = TelnetClient()
        val field = TelnetClient::class.java.getDeclaredField("outputStream")
        field.isAccessible = true
        field.set(client, stream)
        return client
    }

    @Test
    fun `команды не перемешиваются при одновременной отправке`() {
        val stream = SlowStream()
        val client = clientWith(stream)

        val commands = (1..40).map { "команда$it" }
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        commands.forEach { command ->
            pool.submit {
                start.await()
                client.send(command)
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        val sent = stream.collected.toString("UTF-8").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(commands.size, sent.size, "команд дошло не столько, сколько отправляли: $sent")
        assertEquals(commands.toSet(), sent.toSet(), "команды побились при записи")
    }

    @Test
    fun `команда уходит целиком с переводом строки`() {
        val stream = SlowStream()
        val client = clientWith(stream)

        client.send("смотреть")

        assertEquals("смотреть\r\n", stream.collected.toString("UTF-8"))
    }

    @Test
    fun `отправка без соединения не роняет клиент`() {
        val client = TelnetClient()

        client.send("смотреть")

        assertTrue(true, "отправка в закрытое соединение должна проходить молча")
    }
}
