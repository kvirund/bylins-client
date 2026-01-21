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
        // API плагинов
        "com.bylins.client.plugins.",

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
        return parentFirstPackages.any { name.startsWith(it) }
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
