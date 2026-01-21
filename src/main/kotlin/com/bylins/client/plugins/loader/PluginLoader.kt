package com.bylins.client.plugins.loader

import mu.KotlinLogging
import com.bylins.client.plugins.Plugin
import com.bylins.client.plugins.PluginBase
import com.bylins.client.plugins.PluginDependency
import com.bylins.client.plugins.PluginMetadata
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.jar.JarFile

/**
 * Загрузчик плагинов из JAR файлов.
 *
 * Отвечает за:
 * - Чтение plugin.yml из JAR
 * - Парсинг метаданных
 * - Создание изолированного ClassLoader
 * - Инстанциирование главного класса плагина
 */
private val logger = KotlinLogging.logger("PluginLoader")
class PluginLoader {

    private val yaml = Yaml()

    /**
     * Загружает плагин из JAR файла.
     *
     * @param jarFile JAR файл плагина
     * @return Результат загрузки
     */
    fun load(jarFile: File): LoadResult {
        // Проверяем файл
        if (!jarFile.exists()) {
            return LoadResult.Error("File does not exist: ${jarFile.name}")
        }

        if (!jarFile.name.endsWith(".jar")) {
            return LoadResult.Error("Not a JAR file: ${jarFile.name}")
        }

        return try {
            // 1. Читаем метаданные из plugin.yml
            val metadata = readMetadata(jarFile)
                ?: return LoadResult.Error("Missing plugin.yml in ${jarFile.name}")

            logger.info { "Loading plugin: ${metadata.name} v${metadata.version}" }

            // 2. Создаем изолированный ClassLoader
            val classLoader = PluginClassLoader(jarFile)

            // 3. Загружаем главный класс
            val mainClass = try {
                classLoader.loadClass(metadata.mainClass)
            } catch (e: ClassNotFoundException) {
                classLoader.close()
                return LoadResult.Error(
                    "Main class not found: ${metadata.mainClass} in ${jarFile.name}"
                )
            }

            // 4. Проверяем, что класс реализует Plugin
            if (!Plugin::class.java.isAssignableFrom(mainClass)) {
                classLoader.close()
                return LoadResult.Error(
                    "Main class ${metadata.mainClass} does not implement Plugin interface"
                )
            }

            // 5. Создаем экземпляр
            val constructor = try {
                mainClass.getDeclaredConstructor()
            } catch (e: NoSuchMethodException) {
                classLoader.close()
                return LoadResult.Error(
                    "Main class ${metadata.mainClass} must have a no-arg constructor"
                )
            }

            constructor.isAccessible = true
            val instance = constructor.newInstance() as Plugin

            // 6. Инициализируем базовые поля для PluginBase
            if (instance is PluginBase) {
                // Используем reflection для установки internal полей
                setInternalField(instance, "id", metadata.id)
                setInternalField(instance, "metadata", metadata)
            }

            logger.info { "Successfully loaded: ${metadata.name}" }

            LoadResult.Success(
                metadata = metadata,
                instance = instance,
                classLoader = classLoader
            )
        } catch (e: Exception) {
            LoadResult.Error("Failed to load ${jarFile.name}: ${e.message}")
        }
    }

    /**
     * Читает plugin.yml из JAR файла.
     */
    private fun readMetadata(jarFile: File): PluginMetadata? {
        return try {
            JarFile(jarFile).use { jar ->
                // Ищем plugin.yml или plugin.yaml
                val entry = jar.getJarEntry("plugin.yml")
                    ?: jar.getJarEntry("plugin.yaml")
                    ?: return null

                jar.getInputStream(entry).use { input ->
                    @Suppress("UNCHECKED_CAST")
                    val map = yaml.load<Map<String, Any>>(input)
                    parseMetadata(map)
                }
            }
        } catch (e: Exception) {
            logger.error { "Error reading plugin.yml: ${e.message}" }
            null
        }
    }

    /**
     * Парсит метаданные из Map (содержимое plugin.yml).
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseMetadata(map: Map<String, Any>): PluginMetadata {
        // Обязательные поля
        val id = map["id"] as? String
            ?: throw IllegalArgumentException("Missing 'id' in plugin.yml")
        val mainClass = map["main"] as? String
            ?: throw IllegalArgumentException("Missing 'main' in plugin.yml")

        // Опциональные поля
        val name = map["name"] as? String ?: id
        val version = map["version"] as? String ?: "1.0.0"
        val description = map["description"] as? String ?: ""
        val author = map["author"] as? String ?: ""
        val website = map["website"] as? String ?: ""
        val apiVersion = map["api-version"] as? String ?: "1.0"

        // Зависимости
        val dependencies = parseDependencies(map["dependencies"])
        val softDependencies = (map["soft-dependencies"] as? List<String>) ?: emptyList()

        return PluginMetadata(
            id = id,
            name = name,
            version = version,
            description = description,
            author = author,
            website = website,
            mainClass = mainClass,
            dependencies = dependencies,
            softDependencies = softDependencies,
            apiVersion = apiVersion
        )
    }

    /**
     * Парсит список зависимостей.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseDependencies(raw: Any?): List<PluginDependency> {
        if (raw == null) return emptyList()

        return when (raw) {
            // Формат: dependencies: [plugin1, plugin2]
            is List<*> -> raw.mapNotNull { item ->
                when (item) {
                    is String -> PluginDependency(id = item)
                    is Map<*, *> -> {
                        val depMap = item as Map<String, Any>
                        val id = depMap["id"] as? String ?: return@mapNotNull null
                        val minVersion = depMap["min-version"] as? String
                        PluginDependency(id = id, minVersion = minVersion)
                    }
                    else -> null
                }
            }
            else -> emptyList()
        }
    }

    /**
     * Устанавливает internal поле через reflection.
     */
    private fun setInternalField(instance: Any, fieldName: String, value: Any) {
        try {
            val field = instance::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(instance, value)
        } catch (e: Exception) {
            // Игнорируем, если поле не найдено
        }
    }

    /**
     * Результат загрузки плагина.
     */
    sealed class LoadResult {
        /**
         * Успешная загрузка.
         */
        data class Success(
            val metadata: PluginMetadata,
            val instance: Plugin,
            val classLoader: PluginClassLoader
        ) : LoadResult()

        /**
         * Ошибка загрузки.
         */
        data class Error(val message: String) : LoadResult()
    }
}
