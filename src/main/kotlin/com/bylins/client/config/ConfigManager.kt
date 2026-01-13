package com.bylins.client.config

import mu.KotlinLogging
import com.bylins.client.aliases.Alias
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.tabs.Tab
import com.bylins.client.tabs.TabDto
import com.bylins.client.triggers.Trigger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private val logger = KotlinLogging.logger("ConfigManager")
class ConfigManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configDir = Paths.get(System.getProperty("user.home"), ".bylins-client")
    private val configFile = configDir.resolve("config.json")

    init {
        // Создаём директорию конфига если её нет
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
        }
    }

    /**
     * Сохраняет триггеры, алиасы, хоткеи, переменные, вкладки и настройки в файл
     */
    fun saveConfig(
        triggers: List<Trigger>,
        aliases: List<Alias>,
        hotkeys: List<Hotkey>,
        variables: Map<String, String>,
        tabs: List<Tab>,
        encoding: String = "UTF-8",
        miniMapWidth: Int = 250,
        theme: String = "DARK",
        fontFamily: String = "MONOSPACE",
        fontSize: Int = 14,
        connectionProfiles: List<com.bylins.client.connection.ConnectionProfile> = emptyList(),
        currentProfileId: String? = null,
        ignoreNumLock: Boolean = false
    ) {
        try {
            val config = ClientConfig(
                triggers = triggers.map { TriggerDto.fromTrigger(it) },
                aliases = aliases.map { AliasDto.fromAlias(it) },
                hotkeys = hotkeys.map { HotkeyDto.fromHotkey(it) },
                variables = variables,
                tabs = tabs.map { TabDto.fromTab(it) },
                encoding = encoding,
                miniMapWidth = miniMapWidth,
                theme = theme,
                fontFamily = fontFamily,
                fontSize = fontSize,
                connectionProfiles = connectionProfiles.map { ConnectionProfileDto.fromConnectionProfile(it) },
                currentProfileId = currentProfileId,
                ignoreNumLock = ignoreNumLock
            )

            val jsonString = json.encodeToString(config)
            Files.writeString(configFile, jsonString)

            logger.info { "Config saved to: $configFile" }
        } catch (e: Exception) {
            logger.error { "Failed to save config: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Загружает триггеры, алиасы, хоткеи, переменные, вкладки и настройки из файла
     */
    fun loadConfig(): ConfigData {
        try {
            if (!Files.exists(configFile)) {
                logger.info { "Config file not found: $configFile" }
                return ConfigData(
                    emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(),
                    "UTF-8", 250, "DARK", "MONOSPACE", 14,
                    com.bylins.client.connection.ConnectionProfile.createDefaultProfiles(),
                    null, false
                )
            }

            val jsonString = Files.readString(configFile)
            val config = json.decodeFromString<ClientConfig>(jsonString)

            val triggers = config.triggers.map { it.toTrigger() }
            val aliases = config.aliases.map { it.toAlias() }
            val hotkeys = config.hotkeys.mapNotNull { it.toHotkey() }
            val variables = config.variables
            val tabs = config.tabs.map { it.toTab() }
            val encoding = config.encoding
            val miniMapWidth = config.miniMapWidth
            val theme = config.theme
            val fontFamily = config.fontFamily
            val fontSize = config.fontSize
            val connectionProfiles = config.connectionProfiles.map { it.toConnectionProfile() }.ifEmpty {
                com.bylins.client.connection.ConnectionProfile.createDefaultProfiles()
            }
            val currentProfileId = config.currentProfileId
            val ignoreNumLock = config.ignoreNumLock

            logger.info { "Config loaded from: $configFile (${triggers.size} triggers, ${aliases.size} aliases, ${hotkeys.size} hotkeys, ${variables.size} variables, ${tabs.size} tabs, encoding: $encoding, miniMapWidth: $miniMapWidth, theme: $theme, fontFamily: $fontFamily, fontSize: $fontSize, ${connectionProfiles.size} connection profiles, ignoreNumLock: $ignoreNumLock)" }
            return ConfigData(triggers, aliases, hotkeys, variables, tabs, encoding, miniMapWidth, theme, fontFamily, fontSize, connectionProfiles, currentProfileId, ignoreNumLock)
        } catch (e: Exception) {
            logger.error { "Failed to load config: ${e.message}" }
            e.printStackTrace()
            return ConfigData(
                emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(),
                "UTF-8", 250, "DARK", "MONOSPACE", 14,
                com.bylins.client.connection.ConnectionProfile.createDefaultProfiles(),
                null, false
            )
        }
    }

    /**
     * Экспортирует конфигурацию в указанный файл
     */
    fun exportConfig(file: File, triggers: List<Trigger>, aliases: List<Alias>, hotkeys: List<Hotkey>, variables: Map<String, String>, tabs: List<Tab>, encoding: String = "UTF-8", miniMapWidth: Int = 250, theme: String = "DARK", fontFamily: String = "MONOSPACE", fontSize: Int = 14) {
        try {
            val config = ClientConfig(
                triggers = triggers.map { TriggerDto.fromTrigger(it) },
                aliases = aliases.map { AliasDto.fromAlias(it) },
                hotkeys = hotkeys.map { HotkeyDto.fromHotkey(it) },
                variables = variables,
                tabs = tabs.map { TabDto.fromTab(it) },
                encoding = encoding,
                miniMapWidth = miniMapWidth,
                theme = theme,
                fontFamily = fontFamily,
                fontSize = fontSize
            )

            val jsonString = json.encodeToString(config)
            file.writeText(jsonString)

            logger.info { "Config exported to: ${file.absolutePath}" }
        } catch (e: Exception) {
            logger.error { "Failed to export config: ${e.message}" }
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Импортирует конфигурацию из указанного файла
     */
    fun importConfig(file: File): ConfigData {
        try {
            val jsonString = file.readText()
            val config = json.decodeFromString<ClientConfig>(jsonString)

            val triggers = config.triggers.map { it.toTrigger() }
            val aliases = config.aliases.map { it.toAlias() }
            val hotkeys = config.hotkeys.mapNotNull { it.toHotkey() }
            val variables = config.variables
            val tabs = config.tabs.map { it.toTab() }
            val encoding = config.encoding
            val miniMapWidth = config.miniMapWidth
            val theme = config.theme
            val fontFamily = config.fontFamily
            val fontSize = config.fontSize

            logger.info { "Config imported from: ${file.absolutePath} (${triggers.size} triggers, ${aliases.size} aliases, ${hotkeys.size} hotkeys, ${variables.size} variables, ${tabs.size} tabs, encoding: $encoding, miniMapWidth: $miniMapWidth, theme: $theme, fontFamily: $fontFamily, fontSize: $fontSize)" }
            return ConfigData(triggers, aliases, hotkeys, variables, tabs, encoding, miniMapWidth, theme, fontFamily, fontSize)
        } catch (e: Exception) {
            logger.error { "Failed to import config: ${e.message}" }
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Возвращает путь к директории конфига
     */
    fun getConfigDir(): String = configDir.toString()

    /**
     * Возвращает путь к файлу конфига
     */
    fun getConfigFile(): String = configFile.toString()
}

/**
 * Данные конфигурации
 */
data class ConfigData(
    val triggers: List<Trigger>,
    val aliases: List<Alias>,
    val hotkeys: List<Hotkey>,
    val variables: Map<String, String>,
    val tabs: List<Tab>,
    val encoding: String = "UTF-8",
    val miniMapWidth: Int = 250,
    val theme: String = "DARK",
    val fontFamily: String = "MONOSPACE",
    val fontSize: Int = 14,
    val connectionProfiles: List<com.bylins.client.connection.ConnectionProfile> = emptyList(),
    val currentProfileId: String? = null,
    val ignoreNumLock: Boolean = false
)
