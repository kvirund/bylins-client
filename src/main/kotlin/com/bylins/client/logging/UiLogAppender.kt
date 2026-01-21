package com.bylins.client.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Logback appender, который хранит логи для отображения в UI.
 * Синглтон UiLogBuffer хранит последние N сообщений.
 */
class UiLogAppender : AppenderBase<ILoggingEvent>() {

    override fun append(event: ILoggingEvent) {
        val entry = LogEntry(
            timestamp = Instant.ofEpochMilli(event.timeStamp),
            level = event.level.toString(),
            logger = event.loggerName.substringAfterLast('.'),
            message = event.formattedMessage,
            throwable = event.throwableProxy?.message
        )
        UiLogBuffer.add(entry)
    }
}

/**
 * Запись лога
 */
data class LogEntry(
    val timestamp: Instant,
    val level: String,
    val logger: String,
    val message: String,
    val throwable: String? = null
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun formatted(): String {
        val time = timeFormatter.format(timestamp)
        // ANSI цвета для уровней логирования
        val levelColor = when (level) {
            "ERROR" -> "\u001B[1;31m" // Яркий красный
            "WARN" -> "\u001B[1;33m"  // Яркий жёлтый
            "INFO" -> "\u001B[1;32m"  // Яркий зелёный
            "DEBUG" -> "\u001B[1;34m" // Яркий синий
            "TRACE" -> "\u001B[1;30m" // Яркий серый
            else -> ""
        }
        val reset = "\u001B[0m"
        val prefix = "[$time] $levelColor[$level]$reset [$logger]"
        return if (throwable != null) {
            "$prefix $message\n  \u001B[31m$throwable$reset"
        } else {
            "$prefix $message"
        }
    }
}

/**
 * Буфер логов для UI (синглтон)
 */
object UiLogBuffer {
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun add(entry: LogEntry) {
        val current = _entries.value.toMutableList()
        current.add(entry)
        // Ограничиваем размер буфера
        while (current.size > MAX_ENTRIES) {
            current.removeAt(0)
        }
        _entries.value = current
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
