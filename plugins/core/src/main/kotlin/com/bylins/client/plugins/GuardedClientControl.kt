package com.bylins.client.plugins

/**
 * Обёртка над [ClientControl], проверяющая разрешение ПЕРЕД каждым вызовом.
 *
 * Проверка именно на каждом вызове (а не один раз при создании API): пользователь
 * может отозвать разрешение уже после запуска плагина, и это должно подействовать
 * немедленно.
 *
 * @param delegate реальная реализация из клиента; null означает, что управление
 *   клиентом недоступно в этой сборке — вызовы будут отклонены.
 */
internal class GuardedClientControl(
    private val pluginId: String,
    private val delegate: ClientControl?,
    private val hasPermission: (PluginPermission) -> Boolean
) : ClientControl {

    private fun require(permission: PluginPermission): ClientControl {
        if (!hasPermission(permission)) {
            throw PluginPermissionDeniedException(pluginId, permission)
        }
        return delegate ?: throw IllegalStateException(
            "Управление клиентом недоступно (ClientControl не подключён)"
        )
    }

    /** Операции с соединением требуют отдельного, более «громкого» разрешения. */
    private fun connection(): ClientControl = require(PluginPermission.CONNECTION_CONTROL)

    private fun control(): ClientControl = require(PluginPermission.CLIENT_CONTROL)

    // --- Соединение ---
    override fun connect(profileId: String?) = connection().connect(profileId)
    override fun disconnect() = connection().disconnect()
    override fun isConnected(): Boolean = connection().isConnected()

    // --- Профили подключения ---
    override fun listConnectionProfiles(): List<Map<String, Any?>> = control().listConnectionProfiles()
    override fun getCurrentConnectionProfile(): Map<String, Any?>? = control().getCurrentConnectionProfile()
    override fun createConnectionProfile(
        name: String, host: String, port: Int, encoding: String, mapFile: String, autoReconnect: Boolean
    ): String = control().createConnectionProfile(name, host, port, encoding, mapFile, autoReconnect)
    override fun updateConnectionProfile(id: String, changes: Map<String, Any?>): Boolean =
        control().updateConnectionProfile(id, changes)
    override fun deleteConnectionProfile(id: String): Boolean = control().deleteConnectionProfile(id)
    override fun selectConnectionProfile(id: String): Boolean = control().selectConnectionProfile(id)

    // --- Триггеры ---
    override fun listTriggers(): List<Map<String, Any?>> = control().listTriggers()
    override fun createTrigger(
        name: String, pattern: String, commands: List<String>,
        enabled: Boolean, gag: Boolean, priority: Int, profileId: String?, scope: Map<String, Any?>?
    ): String = control().createTrigger(name, pattern, commands, enabled, gag, priority, profileId, scope)
    override fun updateTrigger(id: String, changes: Map<String, Any?>): Boolean = control().updateTrigger(id, changes)
    override fun deleteTrigger(id: String): Boolean = control().deleteTrigger(id)

    // --- Алиасы ---
    override fun listAliases(): List<Map<String, Any?>> = control().listAliases()
    override fun createAlias(
        name: String, pattern: String, commands: List<String>, enabled: Boolean, profileId: String?
    ): String = control().createAlias(name, pattern, commands, enabled, profileId)
    override fun updateAlias(id: String, changes: Map<String, Any?>): Boolean = control().updateAlias(id, changes)
    override fun deleteAlias(id: String): Boolean = control().deleteAlias(id)

    // --- Хоткеи ---
    override fun listHotkeys(): List<Map<String, Any?>> = control().listHotkeys()
    override fun createHotkey(
        name: String, key: String, commands: List<String>,
        ctrl: Boolean, alt: Boolean, shift: Boolean, enabled: Boolean,
        profileId: String?, scope: Map<String, Any?>?
    ): String = control().createHotkey(name, key, commands, ctrl, alt, shift, enabled, profileId, scope)
    override fun updateHotkey(id: String, changes: Map<String, Any?>): Boolean = control().updateHotkey(id, changes)
    override fun deleteHotkey(id: String): Boolean = control().deleteHotkey(id)

    // --- Вкладки вывода ---
    override fun listTabs(): List<Map<String, Any?>> = control().listTabs()
    override fun createTab(
        name: String, patterns: List<String>, captureMode: String,
        profileTab: Boolean, profileLog: Boolean, persistContent: Boolean, timestamps: Boolean
    ): String = control().createTab(name, patterns, captureMode, profileTab, profileLog, persistContent, timestamps)
    override fun deleteTab(id: String): Boolean = control().deleteTab(id)

    // --- Контекстные команды ---
    override fun listContextRules(): List<Map<String, Any?>> = control().listContextRules()
    override fun createContextRule(
        command: String, pattern: String?, scope: Map<String, Any?>?,
        ttl: String, ttlMinutes: Int?, priority: Int, enabled: Boolean, profileId: String?
    ): String = control().createContextRule(command, pattern, scope, ttl, ttlMinutes, priority, enabled, profileId)
    override fun updateContextRule(id: String, changes: Map<String, Any?>): Boolean =
        control().updateContextRule(id, changes)
    override fun deleteContextRule(id: String): Boolean = control().deleteContextRule(id)
    override fun listContextQueue(): List<Map<String, Any?>> = control().listContextQueue()
    override fun getLocation(): Map<String, Any?> = control().getLocation()
    override fun getMsdp(vars: List<String>?): Map<String, Any?> = control().getMsdp(vars)
    override fun getZone(zoneId: String): Map<String, Any?> = control().getZone(zoneId)
    override fun listZones(): List<Map<String, Any?>> = control().listZones()
    override fun listZoneRooms(zoneId: String): List<Map<String, Any?>> = control().listZoneRooms(zoneId)
    override fun setZoneNote(zoneId: String, note: String) = control().setZoneNote(zoneId, note)

    // --- Настройки клиента ---
    override fun getSettings(): Map<String, Any?> = control().getSettings()
    override fun updateSettings(changes: Map<String, Any?>): Map<String, Any?> = control().updateSettings(changes)
    override fun getLogInfo(): Map<String, Any?> = control().getLogInfo()
    override fun setLogging(enabled: Boolean) = control().setLogging(enabled)

    // --- Профили персонажей ---
    override fun listCharacterProfiles(): List<Map<String, Any?>> = control().listCharacterProfiles()
    override fun createCharacterProfile(name: String, description: String, requires: List<String>): String =
        control().createCharacterProfile(name, description, requires)
    override fun setCharacterProfileDependencies(id: String, requires: List<String>): Boolean =
        control().setCharacterProfileDependencies(id, requires)
    override fun pushCharacterProfile(id: String): Boolean = control().pushCharacterProfile(id)
    override fun popCharacterProfile(id: String): Boolean = control().popCharacterProfile(id)
}
