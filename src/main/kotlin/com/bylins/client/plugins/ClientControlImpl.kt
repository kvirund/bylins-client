package com.bylins.client.plugins

import com.bylins.client.ClientState
import mu.KotlinLogging

private val logger = KotlinLogging.logger("ClientControl")

/**
 * Реализация [ClientControl] поверх [ClientState].
 *
 * Делает ровно то же, что пользователь руками в UI, поэтому каждое изменение
 * идёт через публичные методы ClientState (они сами сохраняют конфиг).
 *
 * Проверку разрешений выполняет обёртка GuardedClientControl в plugins/core —
 * здесь её сознательно нет, чтобы логика не дублировалась.
 */
class ClientControlImpl(private val state: ClientState) : ClientControl {

    // --- Соединение ---

    override fun connect(profileId: String?) {
        val profile = if (profileId != null) {
            state.connectionProfiles.value.find { it.id == profileId }
                ?: throw IllegalArgumentException("Профиль подключения не найден: $profileId")
        } else {
            state.getCurrentProfile()
                ?: throw IllegalStateException("Профиль подключения не выбран")
        }
        if (profileId != null && profileId != state.currentProfileId.value) {
            state.setCurrentProfile(profileId)
        }
        logger.info { "Plugin requested connect: ${profile.name} (${profile.host}:${profile.port})" }
        state.connect(profile.host, profile.port)
    }

    override fun disconnect() {
        logger.info { "Plugin requested disconnect" }
        state.disconnect()
    }

    override fun isConnected(): Boolean = state.isConnected.value

    // --- Профили подключения ---

    private fun com.bylins.client.connection.ConnectionProfile.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "host" to host,
        "port" to port,
        "encoding" to encoding,
        "mapFile" to mapFile,
        "autoReconnect" to autoReconnect,
        "current" to (id == state.currentProfileId.value)
    )

    override fun listConnectionProfiles(): List<Map<String, Any?>> =
        state.connectionProfiles.value.map { it.toMap() }

    override fun getCurrentConnectionProfile(): Map<String, Any?>? =
        state.getCurrentProfile()?.toMap()

    override fun createConnectionProfile(
        name: String,
        host: String,
        port: Int,
        encoding: String,
        mapFile: String,
        autoReconnect: Boolean
    ): String {
        val profile = com.bylins.client.connection.ConnectionProfile(
            name = name,
            host = host,
            port = port,
            encoding = encoding,
            mapFile = mapFile,
            autoReconnect = autoReconnect
        )
        state.addConnectionProfile(profile)
        return profile.id
    }

    override fun updateConnectionProfile(id: String, changes: Map<String, Any?>): Boolean {
        val existing = state.connectionProfiles.value.find { it.id == id } ?: return false
        val updated = existing.copy(
            name = changes["name"] as? String ?: existing.name,
            host = changes["host"] as? String ?: existing.host,
            port = (changes["port"] as? Number)?.toInt() ?: existing.port,
            encoding = changes["encoding"] as? String ?: existing.encoding,
            mapFile = changes["mapFile"] as? String ?: existing.mapFile,
            autoReconnect = changes["autoReconnect"] as? Boolean ?: existing.autoReconnect
        )
        state.updateConnectionProfile(updated)
        return true
    }

    override fun deleteConnectionProfile(id: String): Boolean {
        if (state.connectionProfiles.value.none { it.id == id }) return false
        state.removeConnectionProfile(id)
        return true
    }

    override fun selectConnectionProfile(id: String): Boolean {
        if (state.connectionProfiles.value.none { it.id == id }) return false
        state.setCurrentProfile(id)
        return true
    }

    // --- Триггеры ---

    /** Базовые триггеры + триггеры всех профилей персонажей (с пометкой profileId). */
    override fun listTriggers(): List<Map<String, Any?>> {
        fun com.bylins.client.triggers.Trigger.toMap(profileId: String?) = mapOf(
            "id" to id,
            "name" to name,
            "pattern" to pattern.pattern,
            "commands" to commands,
            "enabled" to enabled,
            "gag" to gag,
            "priority" to priority,
            "profileId" to profileId
        )
        val base = state.triggers.value.map { it.toMap(null) }
        val fromProfiles = state.profileManager.profiles.value.flatMap { profile ->
            profile.triggers.map { it.toMap(profile.id) }
        }
        return base + fromProfiles
    }

    /** Ищет профиль персонажа, которому принадлежит триггер. */
    private fun profileOfTrigger(triggerId: String): String? =
        state.profileManager.profiles.value
            .find { profile -> profile.triggers.any { it.id == triggerId } }?.id

    override fun createTrigger(
        name: String,
        pattern: String,
        commands: List<String>,
        enabled: Boolean,
        gag: Boolean,
        priority: Int,
        profileId: String?
    ): String {
        val trigger = com.bylins.client.triggers.Trigger(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            pattern = pattern.toRegex(),
            commands = commands,
            enabled = enabled,
            gag = gag,
            priority = priority
        )
        if (profileId != null) {
            // Триггер профиля персонажа: работает, только пока профиль в активном стеке
            require(state.profileManager.profiles.value.any { it.id == profileId }) {
                "Профиль персонажа не найден: $profileId"
            }
            state.profileManager.addTriggerToProfile(profileId, trigger)
        } else {
            state.addTrigger(trigger)
        }
        return trigger.id
    }

    override fun updateTrigger(id: String, changes: Map<String, Any?>): Boolean {
        val profileId = profileOfTrigger(id)
        val existing = if (profileId != null) {
            state.profileManager.profiles.value.first { it.id == profileId }.triggers.first { it.id == id }
        } else {
            state.triggers.value.find { it.id == id } ?: return false
        }
        @Suppress("UNCHECKED_CAST")
        val updated = existing.copy(
            name = changes["name"] as? String ?: existing.name,
            pattern = (changes["pattern"] as? String)?.toRegex() ?: existing.pattern,
            commands = (changes["commands"] as? List<String>) ?: existing.commands,
            enabled = changes["enabled"] as? Boolean ?: existing.enabled,
            gag = changes["gag"] as? Boolean ?: existing.gag,
            priority = (changes["priority"] as? Number)?.toInt() ?: existing.priority
        )
        if (profileId != null) {
            state.profileManager.updateTriggerInProfile(profileId, updated)
        } else {
            state.updateTrigger(updated)
        }
        return true
    }

    override fun deleteTrigger(id: String): Boolean {
        // Триггер может лежать как в базовом наборе, так и в профиле персонажа
        val profileId = profileOfTrigger(id)
        if (profileId != null) {
            state.profileManager.removeTriggerFromProfile(profileId, id)
            return true
        }
        if (state.triggers.value.none { it.id == id }) return false
        state.removeTrigger(id)
        return true
    }

    // --- Алиасы ---

    override fun listAliases(): List<Map<String, Any?>> = state.aliases.value.map {
        mapOf(
            "id" to it.id,
            "name" to it.name,
            "pattern" to it.pattern.pattern,
            "commands" to it.commands,
            "enabled" to it.enabled,
            "priority" to it.priority
        )
    }

    override fun createAlias(name: String, pattern: String, commands: List<String>, enabled: Boolean): String {
        val alias = com.bylins.client.aliases.Alias(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            pattern = pattern.toRegex(),
            commands = commands,
            enabled = enabled
        )
        state.addAlias(alias)
        return alias.id
    }

    override fun updateAlias(id: String, changes: Map<String, Any?>): Boolean {
        val existing = state.aliases.value.find { it.id == id } ?: return false
        @Suppress("UNCHECKED_CAST")
        val updated = existing.copy(
            name = changes["name"] as? String ?: existing.name,
            pattern = (changes["pattern"] as? String)?.toRegex() ?: existing.pattern,
            commands = (changes["commands"] as? List<String>) ?: existing.commands,
            enabled = changes["enabled"] as? Boolean ?: existing.enabled,
            priority = (changes["priority"] as? Number)?.toInt() ?: existing.priority
        )
        // Замена по тому же id: отдельного updateAlias в ClientState нет
        state.removeAlias(id)
        state.addAlias(updated)
        return true
    }

    override fun deleteAlias(id: String): Boolean {
        if (state.aliases.value.none { it.id == id }) return false
        state.removeAlias(id)
        return true
    }

    // --- Хоткеи ---

    override fun listHotkeys(): List<Map<String, Any?>> = state.hotkeys.value.map {
        mapOf(
            "id" to it.id,
            "key" to com.bylins.client.hotkeys.Hotkey.getKeyName(it.key),
            "ctrl" to it.ctrl,
            "alt" to it.alt,
            "shift" to it.shift,
            "commands" to it.commands,
            "enabled" to it.enabled
        )
    }

    override fun createHotkey(
        name: String,
        key: String,
        commands: List<String>,
        ctrl: Boolean,
        alt: Boolean,
        shift: Boolean,
        enabled: Boolean
    ): String {
        val parsedKey = com.bylins.client.hotkeys.Hotkey.parseKey(key)
            ?: throw IllegalArgumentException("Неизвестная клавиша: $key")
        val hotkey = com.bylins.client.hotkeys.Hotkey(
            id = java.util.UUID.randomUUID().toString(),
            key = parsedKey,
            ctrl = ctrl,
            alt = alt,
            shift = shift,
            commands = commands,
            enabled = enabled
        )
        state.addHotkey(hotkey)
        return hotkey.id
    }

    override fun updateHotkey(id: String, changes: Map<String, Any?>): Boolean {
        val existing = state.hotkeys.value.find { it.id == id } ?: return false
        val newKey = (changes["key"] as? String)?.let {
            com.bylins.client.hotkeys.Hotkey.parseKey(it)
                ?: throw IllegalArgumentException("Неизвестная клавиша: $it")
        } ?: existing.key
        @Suppress("UNCHECKED_CAST")
        val updated = existing.copy(
            key = newKey,
            ctrl = changes["ctrl"] as? Boolean ?: existing.ctrl,
            alt = changes["alt"] as? Boolean ?: existing.alt,
            shift = changes["shift"] as? Boolean ?: existing.shift,
            commands = (changes["commands"] as? List<String>) ?: existing.commands,
            enabled = changes["enabled"] as? Boolean ?: existing.enabled
        )
        state.removeHotkey(id)
        state.addHotkey(updated)
        return true
    }

    override fun deleteHotkey(id: String): Boolean {
        if (state.hotkeys.value.none { it.id == id }) return false
        state.removeHotkey(id)
        return true
    }

    // --- Вкладки вывода ---

    override fun listTabs(): List<Map<String, Any?>> = state.tabs.value.map {
        mapOf(
            "id" to it.id,
            "name" to it.name,
            "patterns" to it.filters.map { f -> f.pattern.pattern },
            "captureMode" to it.captureMode.name,
            "profileTab" to it.profileTab,
            "profileLog" to it.profileLog,
            "persistContent" to it.persistContent,
            "isPluginTab" to it.isPluginTab
        )
    }

    override fun createTab(
        name: String,
        patterns: List<String>,
        captureMode: String,
        profileTab: Boolean,
        profileLog: Boolean,
        persistContent: Boolean
    ): String {
        val filters = patterns.map {
            com.bylins.client.tabs.TabFilter(pattern = it.toRegex())
        }
        val mode = try {
            com.bylins.client.tabs.CaptureMode.valueOf(captureMode.uppercase())
        } catch (e: IllegalArgumentException) {
            com.bylins.client.tabs.CaptureMode.COPY
        }
        val tab = com.bylins.client.tabs.Tab(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            filters = filters,
            captureMode = mode,
            profileTab = profileTab,
            profileLog = profileLog || profileTab,
            persistContent = persistContent
        )
        state.addTab(tab)
        return tab.id
    }

    override fun deleteTab(id: String): Boolean {
        if (state.tabs.value.none { it.id == id }) return false
        state.removeTab(id)
        return true
    }

    // --- Профили персонажей ---

    override fun listCharacterProfiles(): List<Map<String, Any?>> {
        val active = state.profileManager.activeStack.value
        return state.profileManager.profiles.value.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "active" to (it.id in active),
                "requires" to it.requires,
                "triggers" to it.triggers.size,
                "aliases" to it.aliases.size,
                "hotkeys" to it.hotkeys.size
            )
        }
    }

    override fun createCharacterProfile(name: String, description: String, requires: List<String>): String {
        val profile = state.profileManager.createProfile(name, description)
        if (requires.isNotEmpty()) {
            state.profileManager.updateProfileDependencies(profile.id, requires)
        }
        return profile.id
    }

    override fun setCharacterProfileDependencies(id: String, requires: List<String>): Boolean {
        if (state.profileManager.profiles.value.none { it.id == id }) return false
        state.profileManager.updateProfileDependencies(id, requires)
        return true
    }

    override fun pushCharacterProfile(id: String): Boolean {
        if (state.profileManager.profiles.value.none { it.id == id }) return false
        // Возвращаем реальный результат: активация может не пройти из-за
        // ненайденных зависимостей, и «тихий» true это скрывал.
        val result = state.profileManager.pushProfile(id)
        if (!result.success) {
            throw IllegalStateException(result.errorMessage ?: "Не удалось активировать профиль: $id")
        }
        state.saveConfig()
        return true
    }

    override fun popCharacterProfile(id: String): Boolean {
        if (state.profileManager.activeStack.value.none { it == id }) return false
        state.profileManager.removeFromStack(id)
        state.saveConfig()
        return true
    }
}
