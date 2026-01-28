package com.bylins.client.plugins

import mu.KotlinLogging
import java.io.File

/**
 * Абстрактный базовый класс для удобства создания плагинов.
 *
 * Предоставляет доступ к API, логгеру и директории данных.
 *
 * Пример использования:
 * ```kotlin
 * class MyPlugin : PluginBase() {
 *     override fun onEnable() {
 *         logger.info("Plugin enabled!")
 *         api.send("say Hello from plugin!")
 *     }
 *
 *     override fun onDisable() {
 *         logger.info("Plugin disabled!")
 *     }
 * }
 * ```
 */
private val logger = KotlinLogging.logger("Plugin")
abstract class PluginBase : Plugin {

    /**
     * Уникальный идентификатор плагина.
     * Устанавливается PluginManager при загрузке.
     */
    override lateinit var id: String
        internal set

    /**
     * API для взаимодействия с клиентом.
     * Устанавливается PluginManager при загрузке.
     */
    lateinit var api: PluginAPI
        internal set

    /**
     * Метаданные плагина.
     * Устанавливается PluginManager при загрузке.
     */
    lateinit var metadata: PluginMetadata
        internal set

    /**
     * Логгер плагина.
     */
    val logger: PluginLogger by lazy { PluginLogger(metadata.name) }

    /**
     * Директория для хранения данных плагина.
     * Создаётся автоматически при первом обращении.
     */
    val dataFolder: File by lazy {
        File("plugins/${metadata.id}").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }

    /**
     * Загружает конфигурацию плагина из файла config.json в dataFolder.
     */
    inline fun <reified T> loadConfig(): T? {
        return api.loadConfig(T::class.java)
    }

    /**
     * Сохраняет конфигурацию плагина в файл config.json в dataFolder.
     */
    fun saveConfig(config: Any) {
        api.saveConfig(config)
    }
}

/**
 * Простой логгер для плагинов.
 */
class PluginLogger(private val pluginName: String) {

    fun info(message: String) {
        logger.info { "[Plugin:$pluginName] $message" }
    }

    fun warn(message: String) {
        logger.info { "[Plugin:$pluginName] WARN: $message" }
    }

    fun error(message: String, throwable: Throwable? = null) {
        logger.info { "[Plugin:$pluginName] ERROR: $message" }
        throwable?.printStackTrace()
    }

    fun debug(message: String) {
        logger.debug { "[Plugin:$pluginName] DEBUG: $message" }
    }
}
