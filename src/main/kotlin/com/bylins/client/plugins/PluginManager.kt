package com.bylins.client.plugins

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Интерфейс менеджера плагинов.
 *
 * Управляет жизненным циклом плагинов:
 * - Загрузка/выгрузка
 * - Включение/выключение
 * - Автозагрузка из директории
 */
interface PluginManager {

    /**
     * Список загруженных плагинов (реактивный).
     */
    val plugins: StateFlow<List<LoadedPlugin>>

    /**
     * Директория плагинов.
     */
    val pluginsDirectory: File

    /**
     * Загружает плагин из JAR файла.
     *
     * @param jarFile JAR файл плагина
     * @return Загруженный плагин или null при ошибке
     */
    fun loadPlugin(jarFile: File): LoadedPlugin?

    /**
     * Выгружает плагин.
     *
     * @param pluginId ID плагина
     * @return true если плагин был выгружен
     */
    fun unloadPlugin(pluginId: String): Boolean

    /**
     * Включает плагин.
     *
     * @param pluginId ID плагина
     * @return true если плагин был включен
     */
    fun enablePlugin(pluginId: String): Boolean

    /**
     * Выключает плагин.
     *
     * @param pluginId ID плагина
     * @return true если плагин был выключен
     */
    fun disablePlugin(pluginId: String): Boolean

    /**
     * Перезагружает плагин.
     *
     * @param pluginId ID плагина
     * @return true если плагин был перезагружен
     */
    fun reloadPlugin(pluginId: String): Boolean

    /**
     * Получает плагин по ID.
     *
     * @param pluginId ID плагина
     * @return Плагин или null если не найден
     */
    fun getPlugin(pluginId: String): LoadedPlugin?

    /**
     * Проверяет, загружен ли плагин.
     *
     * @param pluginId ID плагина
     * @return true если плагин загружен
     */
    fun isPluginLoaded(pluginId: String): Boolean

    /**
     * Автозагрузка плагинов из директории plugins/.
     */
    fun autoLoadPlugins()

    /**
     * Выгружает все плагины (при выходе из приложения).
     */
    fun shutdown()

    /**
     * Получает API плагина для проверки триггеров.
     */
    fun getPluginAPI(pluginId: String): PluginAPIImpl?
}
