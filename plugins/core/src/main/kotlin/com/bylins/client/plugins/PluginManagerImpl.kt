package com.bylins.client.plugins

import mu.KotlinLogging
import com.bylins.client.plugins.events.EventBus
import com.bylins.client.plugins.loader.PluginClassLoader
import com.bylins.client.plugins.loader.PluginLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Реализация менеджера плагинов.
 *
 * @param eventBus Шина событий для плагинов
 * @param apiFactory Фабрика для создания PluginAPI для каждого плагина
 */
private val logger = KotlinLogging.logger("PluginManager")
class PluginManagerImpl(
    private val eventBus: EventBus,
    private val apiFactory: (String, File) -> PluginAPIImpl
) : PluginManager {

    private val _plugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    override val plugins: StateFlow<List<LoadedPlugin>> = _plugins

    override val pluginsDirectory = File(
        System.getProperty("bylins.plugins.dir", "plugins")
    )

    private val pluginLoader = PluginLoader()
    private val classLoaders = mutableMapOf<String, PluginClassLoader>()
    private val pluginApis = mutableMapOf<String, PluginAPIImpl>()

    init {
        if (!pluginsDirectory.exists()) {
            pluginsDirectory.mkdirs()
        }
    }

    override fun loadPlugin(jarFile: File): LoadedPlugin? {
        val result = pluginLoader.load(jarFile)

        return when (result) {
            is PluginLoader.LoadResult.Success -> {
                val metadata = result.metadata

                // Проверяем, не загружен ли уже плагин
                if (_plugins.value.any { it.metadata.id == metadata.id }) {
                    logger.info { "Plugin ${metadata.id} is already loaded" }
                    result.classLoader.close()
                    return null
                }

                // Проверяем зависимости
                val missingDeps = checkDependencies(metadata.dependencies)
                if (missingDeps.isNotEmpty()) {
                    logger.info { "Missing dependencies for ${metadata.id}: $missingDeps" }
                    result.classLoader.close()
                    return null
                }

                // Создаем API для плагина
                val dataFolder = File(pluginsDirectory, metadata.id)
                val api = apiFactory(metadata.id, dataFolder)
                pluginApis[metadata.id] = api

                // Инициализируем PluginBase
                val instance = result.instance
                if (instance is PluginBase) {
                    try {
                        // Устанавливаем id
                        val idField = PluginBase::class.java.getDeclaredField("id")
                        idField.isAccessible = true
                        idField.set(instance, metadata.id)

                        // Устанавливаем metadata
                        val metadataField = PluginBase::class.java.getDeclaredField("metadata")
                        metadataField.isAccessible = true
                        metadataField.set(instance, metadata)

                        // Устанавливаем api
                        val apiField = PluginBase::class.java.getDeclaredField("api")
                        apiField.isAccessible = true
                        apiField.set(instance, api)
                    } catch (e: Exception) {
                        logger.warn { "Warning: Could not set PluginBase fields: ${e.message}" }
                    }
                }

                // Вызываем onLoad
                try {
                    instance.onLoad()
                } catch (e: Exception) {
                    logger.error { "Error in onLoad for ${metadata.id}: ${e.message}" }
                    e.printStackTrace()
                    api.cleanup()
                    pluginApis.remove(metadata.id)
                    result.classLoader.close()
                    return null
                }

                // Сохраняем ClassLoader
                classLoaders[metadata.id] = result.classLoader

                val loadedPlugin = LoadedPlugin(
                    metadata = metadata,
                    instance = instance,
                    state = PluginState.LOADED,
                    jarFile = jarFile
                )

                _plugins.value = _plugins.value + loadedPlugin
                logger.info { "Loaded plugin: ${metadata.name} v${metadata.version}" }

                loadedPlugin
            }

            is PluginLoader.LoadResult.Error -> {
                logger.info { "${result.message}" }
                null
            }
        }
    }

    override fun enablePlugin(pluginId: String): Boolean {
        val plugin = getPlugin(pluginId) ?: return false

        if (plugin.state == PluginState.ENABLED) {
            return true
        }

        return try {
            plugin.instance.onEnable()
            updatePluginState(pluginId, PluginState.ENABLED)
            logger.info { "Enabled plugin: ${plugin.metadata.name}" }
            true
        } catch (e: Exception) {
            logger.error { "Error enabling ${plugin.metadata.name}: ${e.message}" }
            e.printStackTrace()
            updatePluginState(pluginId, PluginState.ERROR, e.message)
            false
        }
    }

    override fun disablePlugin(pluginId: String): Boolean {
        val plugin = getPlugin(pluginId) ?: return false

        if (plugin.state != PluginState.ENABLED) {
            return true
        }

        return try {
            plugin.instance.onDisable()
            updatePluginState(pluginId, PluginState.DISABLED)
            logger.info { "Disabled plugin: ${plugin.metadata.name}" }
            true
        } catch (e: Exception) {
            logger.error { "Error disabling ${plugin.metadata.name}: ${e.message}" }
            e.printStackTrace()
            false
        }
    }

    override fun unloadPlugin(pluginId: String): Boolean {
        val plugin = getPlugin(pluginId) ?: return false

        // Сначала выключаем
        if (plugin.state == PluginState.ENABLED) {
            disablePlugin(pluginId)
        }

        // Вызываем onUnload
        try {
            plugin.instance.onUnload()
        } catch (e: Exception) {
            logger.error { "Error in onUnload for ${plugin.metadata.name}: ${e.message}" }
        }

        // Очищаем API
        pluginApis[pluginId]?.cleanup()
        pluginApis.remove(pluginId)

        // Отписываем от событий
        eventBus.unsubscribeAll(pluginId)

        // Закрываем ClassLoader
        classLoaders[pluginId]?.close()
        classLoaders.remove(pluginId)

        // Удаляем из списка
        _plugins.value = _plugins.value.filter { it.metadata.id != pluginId }
        logger.info { "Unloaded plugin: ${plugin.metadata.name}" }

        return true
    }

    override fun reloadPlugin(pluginId: String): Boolean {
        val plugin = getPlugin(pluginId) ?: return false
        val jarFile = plugin.jarFile
        val wasEnabled = plugin.state == PluginState.ENABLED

        unloadPlugin(pluginId)
        val reloaded = loadPlugin(jarFile)

        if (reloaded != null && wasEnabled) {
            enablePlugin(pluginId)
            return true
        }

        return reloaded != null
    }

    override fun getPlugin(pluginId: String): LoadedPlugin? {
        return _plugins.value.find { it.metadata.id == pluginId }
    }

    override fun isPluginLoaded(pluginId: String): Boolean {
        return _plugins.value.any { it.metadata.id == pluginId }
    }

    override fun autoLoadPlugins() {
        val jarFiles = pluginsDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".jar")
        } ?: emptyArray()

        if (jarFiles.isEmpty()) {
            logger.info { "No plugins found in ${pluginsDirectory.absolutePath}" }
            return
        }

        logger.info { "Auto-loading ${jarFiles.size} plugin(s)..." }

        // Загружаем плагины с учетом зависимостей
        val loadOrder = sortByDependencies(jarFiles.toList())

        loadOrder.forEach { jarFile ->
            val loaded = loadPlugin(jarFile)
            loaded?.let { enablePlugin(it.metadata.id) }
        }
    }

    override fun shutdown() {
        logger.info { "Shutting down all plugins..." }

        // Выгружаем в обратном порядке (зависимые первыми)
        _plugins.value.reversed().forEach { plugin ->
            unloadPlugin(plugin.metadata.id)
        }

        eventBus.clear()
    }

    override fun getPluginAPI(pluginId: String): PluginAPIImpl? {
        return pluginApis[pluginId]
    }

    /**
     * Проверяет триггеры всех плагинов для строки.
     *
     * @param line Строка для проверки
     * @return TriggerResult или null если ни один триггер не сработал
     */
    fun checkPluginTriggers(line: String, rawLine: String): TriggerResult? {
        for ((pluginId, api) in pluginApis) {
            val plugin = getPlugin(pluginId)
            if (plugin?.state == PluginState.ENABLED) {
                val result = api.checkTriggers(line, rawLine)
                if (result != null && result != TriggerResult.CONTINUE) {
                    return result
                }
            }
        }
        return null
    }

    /**
     * Проверяет алиасы всех плагинов для команды.
     *
     * @param command Команда для проверки
     * @return true если алиас обработал команду
     */
    fun checkPluginAliases(command: String): Boolean {
        for ((pluginId, api) in pluginApis) {
            val plugin = getPlugin(pluginId)
            if (plugin?.state == PluginState.ENABLED) {
                if (api.checkAliases(command)) {
                    return true
                }
            }
        }
        return false
    }

    // ============================================
    // Приватные методы
    // ============================================

    private fun checkDependencies(deps: List<PluginDependency>): List<String> {
        return deps.filter { dep ->
            val loaded = _plugins.value.find { it.metadata.id == dep.id }
            loaded == null || (dep.minVersion != null &&
                    compareVersions(loaded.metadata.version, dep.minVersion) < 0)
        }.map { it.id }
    }

    private fun updatePluginState(pluginId: String, state: PluginState, errorMessage: String? = null) {
        _plugins.value = _plugins.value.map { plugin ->
            if (plugin.metadata.id == pluginId) {
                plugin.copy(state = state, errorMessage = errorMessage)
            } else {
                plugin
            }
        }
    }

    /**
     * Сортирует JAR файлы по зависимостям (топологическая сортировка).
     */
    private fun sortByDependencies(files: List<File>): List<File> {
        // Сначала читаем метаданные всех плагинов
        val metadataMap = mutableMapOf<String, Pair<File, PluginMetadata>>()

        for (file in files) {
            try {
                val result = pluginLoader.load(file)
                if (result is PluginLoader.LoadResult.Success) {
                    metadataMap[result.metadata.id] = file to result.metadata
                    // Закрываем ClassLoader, мы только читаем метаданные
                    result.classLoader.close()
                }
            } catch (e: Exception) {
                // Игнорируем ошибки на этом этапе
            }
        }

        // Простая топологическая сортировка
        val sorted = mutableListOf<File>()
        val visited = mutableSetOf<String>()

        fun visit(id: String) {
            if (id in visited) return
            visited.add(id)

            val (file, metadata) = metadataMap[id] ?: return

            // Сначала загружаем зависимости
            for (dep in metadata.dependencies) {
                visit(dep.id)
            }

            sorted.add(file)
        }

        for (id in metadataMap.keys) {
            visit(id)
        }

        // Добавляем файлы без метаданных в конец
        for (file in files) {
            if (file !in sorted) {
                sorted.add(file)
            }
        }

        return sorted
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
