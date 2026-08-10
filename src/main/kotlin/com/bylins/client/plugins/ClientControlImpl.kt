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


    // --- Перенос между профилями ---

    /** Куда переносим правило; null в поле to означает базовый набор. */
    private class MoveTarget(val to: String?)

    /**
     * Разбирает `profileId` в изменениях: правило остаётся тем же (тот же id),
     * но переезжает в другой профиль или в базовый набор.
     *
     * Без этого перенос сводился к «удалить и создать заново» — правило меняло
     * id, и всё, что на него ссылалось, приходилось чинить руками.
     *
     * @return null, если переносить не надо (ключа нет или профиль тот же)
     */
    private fun moveTarget(changes: Map<String, Any?>, currentProfileId: String?): MoveTarget? {
        if (!changes.containsKey("profileId")) return null
        val target = changes["profileId"] as? String
        if (target == currentProfileId) return null
        if (target != null) {
            require(state.profileManager.profiles.value.any { it.id == target }) {
                "Профиль персонажа не найден: $target"
            }
        }
        return MoveTarget(target)
    }

    // --- Область действия (общая для триггеров, хоткеев, контекстных правил) ---

    /**
     * Разбирает область из простого Map, как её присылает плагин/ИИ:
     * {"type":"zone","zones":[...]} / {"type":"room","roomIds":[...]} / null.
     */
    private fun parseScope(raw: Map<String, Any?>?): com.bylins.client.contextcommands.ContextScope? {
        if (raw == null) return null
        @Suppress("UNCHECKED_CAST")
        fun strings(key: String): Set<String> =
            (raw[key] as? Collection<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
        return when ((raw["type"] as? String)?.lowercase()) {
            "zone" -> com.bylins.client.contextcommands.ContextScope.Zone(strings("zones"))
            "room" -> com.bylins.client.contextcommands.ContextScope.Room(
                roomIds = strings("roomIds"),
                roomPropertyKeys = strings("roomPropertyKeys")
            )
            else -> com.bylins.client.contextcommands.ContextScope.World
        }
    }

    private fun scopeToMap(scope: com.bylins.client.contextcommands.ContextScope?): Map<String, Any?>? =
        when (scope) {
            null -> null
            is com.bylins.client.contextcommands.ContextScope.World -> mapOf("type" to "world")
            is com.bylins.client.contextcommands.ContextScope.Zone ->
                mapOf("type" to "zone", "zones" to scope.zones.toList())
            is com.bylins.client.contextcommands.ContextScope.Room -> mapOf(
                "type" to "room",
                "roomIds" to scope.roomIds.toList(),
                "roomPropertyKeys" to scope.roomPropertyKeys.toList()
            )
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
            "profileId" to profileId,
            "scope" to scopeToMap(scope)
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
        profileId: String?,
        scope: Map<String, Any?>?
    ): String {
        val trigger = com.bylins.client.triggers.Trigger(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            pattern = pattern.toRegex(),
            commands = commands,
            enabled = enabled,
            gag = gag,
            priority = priority,
            scope = parseScope(scope)
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
            priority = (changes["priority"] as? Number)?.toInt() ?: existing.priority,
            // Область приходит вложенным объектом, а не скаляром — без разбора
            // она молча терялась, и проставить её существующему правилу было нельзя
            scope = if (changes.containsKey("scope")) {
                parseScope(changes["scope"] as? Map<String, Any?>)
            } else existing.scope
        )
        val move = moveTarget(changes, profileId)
        if (move != null) {
            if (profileId != null) state.profileManager.removeTriggerFromProfile(profileId, id)
            else state.removeTrigger(id)
            if (move.to != null) state.profileManager.addTriggerToProfile(move.to, updated)
            else state.addTrigger(updated)
        } else if (profileId != null) {
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

    /** Базовые алиасы + алиасы профилей персонажей (с пометкой profileId). */
    override fun listAliases(): List<Map<String, Any?>> {
        fun com.bylins.client.aliases.Alias.toMap(profileId: String?) = mapOf(
            "id" to id,
            "name" to name,
            "pattern" to pattern.pattern,
            "commands" to commands,
            "enabled" to enabled,
            "priority" to priority,
            "profileId" to profileId
        )
        val base = state.aliases.value.map { it.toMap(null) }
        val fromProfiles = state.profileManager.profiles.value.flatMap { profile ->
            profile.aliases.map { it.toMap(profile.id) }
        }
        return base + fromProfiles
    }

    private fun profileOfAlias(aliasId: String): String? =
        state.profileManager.profiles.value
            .find { profile -> profile.aliases.any { it.id == aliasId } }?.id

    override fun createAlias(
        name: String,
        pattern: String,
        commands: List<String>,
        enabled: Boolean,
        profileId: String?
    ): String {
        val alias = com.bylins.client.aliases.Alias(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            pattern = pattern.toRegex(),
            commands = commands,
            enabled = enabled
        )
        if (profileId != null) {
            require(state.profileManager.profiles.value.any { it.id == profileId }) {
                "Профиль персонажа не найден: $profileId"
            }
            state.profileManager.addAliasToProfile(profileId, alias)
        } else {
            state.addAlias(alias)
        }
        return alias.id
    }

    override fun updateAlias(id: String, changes: Map<String, Any?>): Boolean {
        val profileId = profileOfAlias(id)
        val existing = if (profileId != null) {
            state.profileManager.profiles.value.first { it.id == profileId }.aliases.first { it.id == id }
        } else {
            state.aliases.value.find { it.id == id } ?: return false
        }
        @Suppress("UNCHECKED_CAST")
        val updated = existing.copy(
            name = changes["name"] as? String ?: existing.name,
            pattern = (changes["pattern"] as? String)?.toRegex() ?: existing.pattern,
            commands = (changes["commands"] as? List<String>) ?: existing.commands,
            enabled = changes["enabled"] as? Boolean ?: existing.enabled,
            priority = (changes["priority"] as? Number)?.toInt() ?: existing.priority
        )
        val move = moveTarget(changes, profileId)
        if (move != null) {
            if (profileId != null) state.profileManager.removeAliasFromProfile(profileId, id)
            else state.removeAlias(id)
            if (move.to != null) state.profileManager.addAliasToProfile(move.to, updated)
            else state.addAlias(updated)
        } else if (profileId != null) {
            state.profileManager.updateAliasInProfile(profileId, updated)
        } else {
            // Замена по тому же id: отдельного updateAlias в ClientState нет
            state.removeAlias(id)
            state.addAlias(updated)
        }
        return true
    }

    override fun deleteAlias(id: String): Boolean {
        val profileId = profileOfAlias(id)
        if (profileId != null) {
            state.profileManager.removeAliasFromProfile(profileId, id)
            return true
        }
        if (state.aliases.value.none { it.id == id }) return false
        state.removeAlias(id)
        return true
    }

    // --- Хоткеи ---

    /** Базовые хоткеи + хоткеи профилей персонажей (с пометкой profileId). */
    override fun listHotkeys(): List<Map<String, Any?>> {
        fun com.bylins.client.hotkeys.Hotkey.toMap(profileId: String?) = mapOf(
            "id" to id,
            "key" to com.bylins.client.hotkeys.Hotkey.getKeyName(key),
            "ctrl" to ctrl,
            "alt" to alt,
            "shift" to shift,
            "commands" to commands,
            "enabled" to enabled,
            "profileId" to profileId,
            "scope" to scopeToMap(scope)
        )
        val base = state.hotkeys.value.map { it.toMap(null) }
        val fromProfiles = state.profileManager.profiles.value.flatMap { profile ->
            profile.hotkeys.map { it.toMap(profile.id) }
        }
        return base + fromProfiles
    }

    private fun profileOfHotkey(hotkeyId: String): String? =
        state.profileManager.profiles.value
            .find { profile -> profile.hotkeys.any { it.id == hotkeyId } }?.id

    override fun createHotkey(
        name: String,
        key: String,
        commands: List<String>,
        ctrl: Boolean,
        alt: Boolean,
        shift: Boolean,
        enabled: Boolean,
        profileId: String?,
        scope: Map<String, Any?>?
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
            enabled = enabled,
            scope = parseScope(scope)
        )
        if (profileId != null) {
            require(state.profileManager.profiles.value.any { it.id == profileId }) {
                "Профиль персонажа не найден: $profileId"
            }
            state.profileManager.addHotkeyToProfile(profileId, hotkey)
        } else {
            state.addHotkey(hotkey)
        }
        return hotkey.id
    }

    override fun updateHotkey(id: String, changes: Map<String, Any?>): Boolean {
        val profileId = profileOfHotkey(id)
        val existing = if (profileId != null) {
            state.profileManager.profiles.value.first { it.id == profileId }.hotkeys.first { it.id == id }
        } else {
            state.hotkeys.value.find { it.id == id } ?: return false
        }
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
            enabled = changes["enabled"] as? Boolean ?: existing.enabled,
            scope = if (changes.containsKey("scope")) {
                parseScope(changes["scope"] as? Map<String, Any?>)
            } else existing.scope
        )
        val move = moveTarget(changes, profileId)
        if (move != null) {
            if (profileId != null) state.profileManager.removeHotkeyFromProfile(profileId, id)
            else state.removeHotkey(id)
            if (move.to != null) state.profileManager.addHotkeyToProfile(move.to, updated)
            else state.addHotkey(updated)
        } else if (profileId != null) {
            state.profileManager.updateHotkeyInProfile(profileId, updated)
        } else {
            state.removeHotkey(id)
            state.addHotkey(updated)
        }
        return true
    }

    override fun deleteHotkey(id: String): Boolean {
        val profileId = profileOfHotkey(id)
        if (profileId != null) {
            state.profileManager.removeHotkeyFromProfile(profileId, id)
            return true
        }
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

    // --- Контекстные команды ---

    private val ctx get() = state.contextCommandManager

    override fun listContextRules(): List<Map<String, Any?>> {
        fun com.bylins.client.contextcommands.ContextCommandRule.toMap(profileId: String?) = mapOf(
            "id" to id,
            "enabled" to enabled,
            // Pattern — по строке вывода, Permanent — просто «пока игрок здесь»
            "pattern" to (triggerType as? com.bylins.client.contextcommands.ContextTriggerType.Pattern)
                ?.regex?.pattern,
            "permanent" to (triggerType is com.bylins.client.contextcommands.ContextTriggerType.Permanent),
            "scope" to scopeToMap(scope),
            "command" to command,
            "ttl" to ttlToString(ttl),
            "priority" to priority,
            "profileId" to profileId
        )
        val base = ctx.rules.value.map { it.toMap(null) }
        val fromProfiles = state.profileManager.profiles.value.flatMap { profile ->
            profile.contextCommandRules.map { it.toMap(profile.id) }
        }
        return base + fromProfiles
    }

    private fun profileOfContextRule(ruleId: String): String? =
        state.profileManager.profiles.value
            .find { profile -> profile.contextCommandRules.any { it.id == ruleId } }?.id

    override fun createContextRule(
        command: String,
        pattern: String?,
        scope: Map<String, Any?>?,
        ttl: String,
        ttlMinutes: Int?,
        priority: Int,
        enabled: Boolean,
        profileId: String?
    ): String {
        val rule = com.bylins.client.contextcommands.ContextCommandRule(
            enabled = enabled,
            triggerType = if (pattern != null) {
                com.bylins.client.contextcommands.ContextTriggerType.Pattern(pattern.toRegex())
            } else {
                com.bylins.client.contextcommands.ContextTriggerType.Permanent
            },
            scope = parseScope(scope) ?: com.bylins.client.contextcommands.ContextScope.World,
            command = command,
            ttl = parseTtl(ttl, ttlMinutes),
            priority = priority
        )
        if (profileId != null) {
            require(state.profileManager.profiles.value.any { it.id == profileId }) {
                "Профиль персонажа не найден: $profileId"
            }
            state.profileManager.addContextRuleToProfile(profileId, rule)
        } else {
            ctx.addRule(rule)
        }
        state.saveConfig()
        return rule.id
    }

    override fun updateContextRule(id: String, changes: Map<String, Any?>): Boolean {
        val profileId = profileOfContextRule(id)
        val existing = if (profileId != null) {
            state.profileManager.profiles.value.first { it.id == profileId }
                .contextCommandRules.first { it.id == id }
        } else {
            ctx.rules.value.find { it.id == id } ?: return false
        }
        val updated = existing.copy(
            command = changes["command"] as? String ?: existing.command,
            enabled = changes["enabled"] as? Boolean ?: existing.enabled,
            priority = (changes["priority"] as? Number)?.toInt() ?: existing.priority,
            triggerType = if (changes.containsKey("pattern")) {
                (changes["pattern"] as? String)?.let {
                    com.bylins.client.contextcommands.ContextTriggerType.Pattern(it.toRegex())
                } ?: com.bylins.client.contextcommands.ContextTriggerType.Permanent
            } else existing.triggerType,
            scope = if (changes.containsKey("scope")) {
                parseScope(changes["scope"] as? Map<String, Any?>)
                    ?: com.bylins.client.contextcommands.ContextScope.World
            } else existing.scope,
            ttl = if (changes.containsKey("ttl")) {
                parseTtl(changes["ttl"] as? String ?: "room", (changes["ttlMinutes"] as? Number)?.toInt())
            } else existing.ttl
        )

        val move = moveTarget(changes, profileId)
        if (move != null) {
            if (profileId != null) state.profileManager.removeContextRuleFromProfile(profileId, id)
            else ctx.removeRule(id)
            if (move.to != null) state.profileManager.addContextRuleToProfile(move.to, updated)
            else ctx.addRule(updated)
        } else if (profileId != null) {
            state.profileManager.updateContextRuleInProfile(profileId, updated)
        } else {
            ctx.updateRule(id) { updated }
        }
        state.saveConfig()
        return true
    }

    override fun deleteContextRule(id: String): Boolean {
        val profileId = profileOfContextRule(id)
        if (profileId != null) {
            state.profileManager.removeContextRuleFromProfile(profileId, id)
            state.saveConfig()
            return true
        }
        if (ctx.rules.value.none { it.id == id }) return false
        ctx.removeRule(id)
        state.saveConfig()
        return true
    }

    // --- MSDP ---

    override fun getMsdp(vars: List<String>?): Map<String, Any?> {
        val all = state.msdpData.value
        val updated = state.msdpUpdatedAt.value
        val selected = if (vars.isNullOrEmpty()) all else all.filterKeys { it in vars.toSet() }
        return mapOf(
            "enabled" to state.msdpEnabled.value,
            "variables" to selected,
            "updatedAt" to updated.filterKeys { it in selected.keys },
            // Что сервер вообще умеет присылать — чтобы не гадать, почему
            // запрошенной переменной нет в снимке
            "reportable" to state.msdpReportableVariables.value
        )
    }

    // --- Зоны карты ---

    private fun roomToMap(room: com.bylins.client.mapper.Room): Map<String, Any?> = mapOf(
        "id" to room.id,
        "name" to room.name,
        "zone" to room.zone,
        "terrain" to room.terrain,
        "visited" to room.visited,
        "notes" to room.notes,
        "properties" to room.properties,
        "exits" to room.exits.keys.map { it.name }
    )

    override fun getZone(zoneId: String): Map<String, Any?> {
        val rooms = state.mapRooms.value.values.filter { it.zone == zoneId }
        return mapOf(
            "id" to zoneId,
            "name" to state.zoneNames.value[zoneId],
            "label" to state.zoneLabel(zoneId),
            "note" to state.getZoneNotes(zoneId),
            "properties" to state.getZoneProperties(zoneId),
            "roomsCount" to rooms.size
        )
    }

    override fun listZones(): List<Map<String, Any?>> {
        val byZone = state.mapRooms.value.values.mapNotNull { it.zone }.groupingBy { it }.eachCount()
        return byZone.entries.sortedBy { it.key }.map { (id, count) ->
            mapOf(
                "id" to id,
                "name" to state.zoneNames.value[id],
                "label" to state.zoneLabel(id),
                "roomsCount" to count
            )
        }
    }

    override fun listZoneRooms(zoneId: String): List<Map<String, Any?>> =
        state.mapRooms.value.values.filter { it.zone == zoneId }.sortedBy { it.id }.map { roomToMap(it) }

    override fun setZoneNote(zoneId: String, note: String) {
        state.setZoneNotes(zoneId, note)
    }

    /** Где игрок сейчас — чтобы зону не приходилось вычислять из id комнаты. */
    override fun getLocation(): Map<String, Any?> {
        val roomId = state.currentRoomId.value
        val room = roomId?.let { state.mapRooms.value[it] }
        return mapOf(
            "roomId" to room?.id,
            "roomName" to room?.name,
            "zone" to room?.zone,
            "zoneLabel" to state.zoneLabel(room?.zone),
            "exits" to (room?.exits?.keys?.map { it.name } ?: emptyList<String>()),
            "properties" to (room?.properties ?: emptyMap<String, String>())
        )
    }

    override fun listContextQueue(): List<Map<String, Any?>> = ctx.commandQueue.value.map { cmd ->
        mapOf(
            "id" to cmd.id,
            "command" to cmd.command,
            "addedAt" to cmd.addedAt,
            "ttl" to ttlToString(cmd.ttl),
            "roomId" to cmd.roomIdWhenAdded,
            "zone" to cmd.zoneWhenAdded,
            "zoneLabel" to state.zoneLabel(cmd.zoneWhenAdded)
        )
    }

    private fun ttlToString(ttl: com.bylins.client.contextcommands.ContextCommandTTL): String = when (ttl) {
        is com.bylins.client.contextcommands.ContextCommandTTL.UntilRoomChange -> "room_change"
        is com.bylins.client.contextcommands.ContextCommandTTL.UntilZoneChange -> "zone_change"
        is com.bylins.client.contextcommands.ContextCommandTTL.FixedTime -> "fixed_time:${ttl.minutes}"
        is com.bylins.client.contextcommands.ContextCommandTTL.Permanent -> "permanent"
        is com.bylins.client.contextcommands.ContextCommandTTL.OneTime -> "one_time"
    }

    private fun parseTtl(ttl: String, minutes: Int?): com.bylins.client.contextcommands.ContextCommandTTL =
        when (ttl.lowercase()) {
            "zone_change" -> com.bylins.client.contextcommands.ContextCommandTTL.UntilZoneChange
            "fixed_time" -> com.bylins.client.contextcommands.ContextCommandTTL.FixedTime(minutes ?: 10)
            "permanent" -> com.bylins.client.contextcommands.ContextCommandTTL.Permanent
            "one_time" -> com.bylins.client.contextcommands.ContextCommandTTL.OneTime
            else -> com.bylins.client.contextcommands.ContextCommandTTL.UntilRoomChange
        }

    // --- Настройки клиента ---

    override fun getSettings(): Map<String, Any?> = mapOf(
        "theme" to state.currentTheme.value,
        "fontFamily" to state.fontFamily.value,
        "fontSize" to state.fontSize.value,
        "encoding" to (state.getCurrentProfile()?.encoding ?: "UTF-8"),
        "miniMapWidth" to state.miniMapWidth.value,
        "miniMapHeight" to state.miniMapHeight.value,
        "zonePanelWidth" to state.zonePanelWidth.value,
        "ignoreNumLock" to state.ignoreNumLock.value,
        "sidePanelCollapsed" to state.sidePanelCollapsed.value,
        // Путь к файлу и каталог живут в getLogInfo(): держать их в двух местах
        // значит рано или поздно разойтись
        "logging" to state.isLogging.value,
        "logWithColors" to state.logWithColors.value
    )

    override fun updateSettings(changes: Map<String, Any?>): Map<String, Any?> {
        val applied = mutableMapOf<String, Any?>()
        changes.forEach { (key, value) ->
            when (key) {
                "theme" -> (value as? String)?.let { state.setTheme(it); applied[key] = it }
                "fontFamily" -> (value as? String)?.let { state.setFontFamily(it); applied[key] = it }
                "fontSize" -> (value as? Number)?.toInt()?.let { state.setFontSize(it); applied[key] = it }
                "encoding" -> (value as? String)?.let { state.setEncoding(it); applied[key] = it }
                "ignoreNumLock" -> (value as? Boolean)?.let { state.setIgnoreNumLock(it); applied[key] = it }
                "sidePanelCollapsed" -> (value as? Boolean)?.let { state.setSidePanelCollapsed(it); applied[key] = it }
                "logWithColors" -> (value as? Boolean)?.let { state.setLogWithColors(it); applied[key] = it }
                "logging" -> (value as? Boolean)?.let { setLogging(it); applied[key] = it }
                // Неизвестные ключи молча пропускаем: список настроек растёт,
                // и плагин не должен падать из-за незнакомого имени
            }
        }
        return applied
    }

    override fun getLogInfo(): Map<String, Any?> = mapOf(
        "enabled" to state.isLogging.value,
        "file" to state.currentLogFile.value,
        "directory" to state.getLogsDirectory(),
        "filesCount" to state.getLogFiles().size,
        "withColors" to state.logWithColors.value
    )

    override fun setLogging(enabled: Boolean) {
        if (enabled) state.startLogging(stripAnsi = !state.logWithColors.value) else state.stopLogging()
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
