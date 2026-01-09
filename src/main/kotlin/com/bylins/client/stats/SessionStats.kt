package com.bylins.client.stats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SessionStats {
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    private var sessionStartTime: LocalDateTime? = null

    /**
     * Начинает новую сессию
     */
    fun startSession() {
        sessionStartTime = LocalDateTime.now()
        _stats.value = Stats(
            sessionStart = sessionStartTime,
            commandsSent = 0,
            bytesReceived = 0,
            triggersActivated = 0,
            aliasesExecuted = 0,
            hotkeysUsed = 0
        )
    }

    /**
     * Останавливает сессию
     */
    fun stopSession() {
        sessionStartTime = null
        _stats.value = Stats()
    }

    /**
     * Добавляет отправленную команду
     */
    fun incrementCommandsSent() {
        _stats.value = _stats.value.copy(
            commandsSent = _stats.value.commandsSent + 1
        )
    }

    /**
     * Добавляет полученные байты
     */
    fun addBytesReceived(bytes: Int) {
        _stats.value = _stats.value.copy(
            bytesReceived = _stats.value.bytesReceived + bytes
        )
    }

    /**
     * Добавляет сработавший триггер
     */
    fun incrementTriggersActivated() {
        _stats.value = _stats.value.copy(
            triggersActivated = _stats.value.triggersActivated + 1
        )
    }

    /**
     * Добавляет выполненный алиас
     */
    fun incrementAliasesExecuted() {
        _stats.value = _stats.value.copy(
            aliasesExecuted = _stats.value.aliasesExecuted + 1
        )
    }

    /**
     * Добавляет использованный хоткей
     */
    fun incrementHotkeysUsed() {
        _stats.value = _stats.value.copy(
            hotkeysUsed = _stats.value.hotkeysUsed + 1
        )
    }

    /**
     * Возвращает длительность текущей сессии
     */
    fun getSessionDuration(): Duration? {
        return sessionStartTime?.let { start ->
            Duration.between(start, LocalDateTime.now())
        }
    }

    /**
     * Форматирует длительность в читабельный вид
     */
    fun getFormattedDuration(): String {
        val duration = getSessionDuration() ?: return "00:00:00"
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Форматирует количество байтов в читабельный вид
     */
    fun getFormattedBytes(): String {
        val bytes = _stats.value.bytesReceived
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

data class Stats(
    val sessionStart: LocalDateTime? = null,
    val commandsSent: Int = 0,
    val bytesReceived: Long = 0,
    val triggersActivated: Int = 0,
    val aliasesExecuted: Int = 0,
    val hotkeysUsed: Int = 0
) {
    fun getStartTimeFormatted(): String {
        return sessionStart?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "—"
    }
}
