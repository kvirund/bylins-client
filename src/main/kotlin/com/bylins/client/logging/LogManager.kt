package com.bylins.client.logging

import mu.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger("LogManager")
class LogManager {
    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging

    private val _currentLogFile = MutableStateFlow<String?>(null)
    val currentLogFile: StateFlow<String?> = _currentLogFile

    /** Сохранять ANSI-коды (цвета) в логах */
    private val _logWithColors = MutableStateFlow(false)
    val logWithColors: StateFlow<Boolean> = _logWithColors

    fun setLogWithColors(enabled: Boolean) {
        _logWithColors.value = enabled
    }

    private val logsDir = Paths.get(System.getProperty("user.home"), ".bylins-client", "logs")
    private var currentFile: File? = null

    private val ansiRegex = "\u001B\\[[;\\d]*m".toRegex()

    init {
        // Создаём директорию логов если её нет
        if (!Files.exists(logsDir)) {
            Files.createDirectories(logsDir)
        }
    }

    /**
     * Начинает логирование в новый файл
     */
    fun startLogging(stripAnsi: Boolean = true) {
        if (_isLogging.value) {
            stopLogging()
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val fileName = "bylins_$timestamp.log"
        currentFile = logsDir.resolve(fileName).toFile()

        _isLogging.value = true
        _currentLogFile.value = currentFile?.absolutePath

        // Записываем заголовок
        val header = buildString {
            appendLine("=" * 80)
            appendLine("Bylins MUD Client - Log Session")
            appendLine("Started: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine("File: $fileName")
            appendLine("Strip ANSI: $stripAnsi")
            appendLine("=" * 80)
            appendLine()
        }

        writeToFile(header)

        logger.info { "Logging started: ${currentFile?.absolutePath}" }
    }

    /**
     * Останавливает логирование
     */
    fun stopLogging() {
        if (!_isLogging.value) return

        // Записываем футер
        val footer = buildString {
            appendLine()
            appendLine("=" * 80)
            appendLine("Session ended: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine("=" * 80)
        }

        writeToFile(footer)

        _isLogging.value = false
        _currentLogFile.value = null
        currentFile = null

        logger.info { "Logging stopped" }
    }

    /**
     * Записывает текст в лог-файл
     */
    fun log(text: String) {
        if (!_isLogging.value || currentFile == null) return

        val processedText = if (!_logWithColors.value) {
            text.replace(ansiRegex, "")
        } else {
            text
        }

        writeToFile(processedText)
    }

    /**
     * Записывает текст в файл
     */
    private fun writeToFile(text: String) {
        try {
            currentFile?.let { file ->
                file.appendText(text)
            }
        } catch (e: Exception) {
            logger.error { "Failed to write to log file: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Возвращает список всех лог-файлов
     */
    fun getLogFiles(): List<File> {
        return try {
            logsDir.toFile().listFiles()
                ?.filter { it.isFile && it.extension == "log" }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Удаляет старые лог-файлы (старше указанных дней)
     */
    fun cleanOldLogs(daysToKeep: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)

        getLogFiles().forEach { file ->
            if (file.lastModified() < cutoffTime) {
                try {
                    file.delete()
                    logger.info { "Deleted old log file: ${file.name}" }
                } catch (e: Exception) {
                    logger.error { "Failed to delete log file ${file.name}: ${e.message}" }
                }
            }
        }
    }

    /**
     * Возвращает путь к директории логов
     */
    fun getLogsDirectory(): String = logsDir.toString()

    private operator fun String.times(count: Int): String = this.repeat(count)
}
