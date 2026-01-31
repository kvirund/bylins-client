package com.bylins.client.plugins.loader

import java.io.File
import java.net.URLClassLoader

/**
 * Изолированный ClassLoader для плагинов.
 *
 * Каждый плагин загружается в своём ClassLoader для:
 * 1. Изоляции зависимостей (разные версии библиотек)
 * 2. Возможности выгрузки плагина (через GC)
 * 3. Защиты от конфликтов классов
 */
class PluginClassLoader(
    private val jarFile: File,
    parent: ClassLoader = PluginClassLoader::class.java.classLoader
) : URLClassLoader(arrayOf(jarFile.toURI().toURL()), parent) {

    /**
     * Пакеты, которые всегда загружаются из родительского ClassLoader.
     * Это необходимо для корректной работы API плагинов.
     */
    private val parentFirstPackages = listOf(
        // Kotlin стандартная библиотека
        "kotlin.",
        "kotlinx.",

        // Java стандартная библиотека
        "java.",
        "javax.",
        "sun.",
        "com.sun.",

        // Compose (если плагин использует UI)
        "androidx.compose."
    )

    /**
     * Пакеты Plugin API, которые загружаются из родительского ClassLoader,
     * но НЕ включают пакеты конкретных плагинов (aibot, etc).
     */
    private val pluginApiPackages = listOf(
        "com.bylins.client.plugins.events.",
        "com.bylins.client.plugins.loader.",
        "com.bylins.client.plugins.scripting.",
        "com.bylins.client.plugins.ui.",
        "com.bylins.client.plugins.PluginBase",
        "com.bylins.client.plugins.Plugin",
        "com.bylins.client.plugins.PluginAPI",
        "com.bylins.client.plugins.PluginAPIImpl",
        "com.bylins.client.plugins.PluginManager",
        "com.bylins.client.plugins.PluginManagerImpl",
        "com.bylins.client.plugins.LoadedPlugin",
        "com.bylins.client.plugins.PluginMetadata",
        "com.bylins.client.plugins.TriggerResult"
    )

    /**
     * Кэш загруженных классов плагина.
     */
    private val pluginClasses = mutableMapOf<String, Class<*>>()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            // Проверяем кэш
            var clazz = findLoadedClass(name)
            if (clazz != null) {
                return clazz
            }

            // Для системных пакетов и API - используем родительский загрузчик
            if (shouldLoadFromParent(name)) {
                return parent.loadClass(name)
            }

            // Пытаемся загрузить из JAR плагина
            return try {
                clazz = findClass(name)
                pluginClasses[name] = clazz
                if (resolve) {
                    resolveClass(clazz)
                }
                clazz
            } catch (e: ClassNotFoundException) {
                // Если не найден в JAR - ищем в родительском
                parent.loadClass(name)
            }
        }
    }

    /**
     * Проверяет, должен ли класс загружаться из родительского ClassLoader.
     */
    private fun shouldLoadFromParent(name: String): Boolean {
        // Системные пакеты всегда из родителя
        if (parentFirstPackages.any { name.startsWith(it) }) {
            return true
        }
        // Plugin API классы/пакеты из родителя (но не классы плагинов!)
        if (pluginApiPackages.any { name.startsWith(it) }) {
            return true
        }
        return false
    }

    /**
     * Получает путь к JAR файлу.
     */
    fun getJarPath(): String = jarFile.absolutePath

    /**
     * Получает список классов, загруженных этим ClassLoader.
     */
    fun getLoadedPluginClasses(): Set<String> = pluginClasses.keys.toSet()

    /**
     * Закрывает ClassLoader и освобождает ресурсы.
     */
    override fun close() {
        pluginClasses.clear()
        super.close()
    }
}
