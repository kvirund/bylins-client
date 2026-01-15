package com.bylins.client.config

import com.bylins.client.aliases.Alias
import com.bylins.client.connection.ConnectionProfile
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.triggers.Trigger
import com.bylins.client.triggers.TriggerColorize
import kotlinx.serialization.Serializable

@Serializable
data class TriggerDto(
    val id: String,
    val name: String,
    val pattern: String, // Regex pattern as string
    val commands: List<String>,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val colorize: TriggerColorizeDto? = null,
    val gag: Boolean = false,
    val once: Boolean = false
) {
    fun toTrigger(): Trigger {
        return Trigger(
            id = id,
            name = name,
            pattern = pattern.toRegex(),
            commands = commands,
            enabled = enabled,
            priority = priority,
            colorize = colorize?.toTriggerColorize(),
            gag = gag,
            once = once
        )
    }

    companion object {
        fun fromTrigger(trigger: Trigger): TriggerDto {
            return TriggerDto(
                id = trigger.id,
                name = trigger.name,
                pattern = trigger.pattern.pattern,
                commands = trigger.commands,
                enabled = trigger.enabled,
                priority = trigger.priority,
                colorize = trigger.colorize?.let { TriggerColorizeDto.fromTriggerColorize(it) },
                gag = trigger.gag,
                once = trigger.once
            )
        }
    }
}

@Serializable
data class TriggerColorizeDto(
    val foreground: String? = null,
    val background: String? = null,
    val bold: Boolean = false
) {
    fun toTriggerColorize(): TriggerColorize {
        return TriggerColorize(
            foreground = foreground,
            background = background,
            bold = bold
        )
    }

    companion object {
        fun fromTriggerColorize(colorize: TriggerColorize): TriggerColorizeDto {
            return TriggerColorizeDto(
                foreground = colorize.foreground,
                background = colorize.background,
                bold = colorize.bold
            )
        }
    }
}

@Serializable
data class AliasDto(
    val id: String,
    val name: String,
    val pattern: String, // Regex pattern as string
    val commands: List<String>,
    val enabled: Boolean = true,
    val priority: Int = 0
) {
    fun toAlias(): Alias {
        return Alias(
            id = id,
            name = name,
            pattern = pattern.toRegex(),
            commands = commands,
            enabled = enabled,
            priority = priority
        )
    }

    companion object {
        fun fromAlias(alias: Alias): AliasDto {
            return AliasDto(
                id = alias.id,
                name = alias.name,
                pattern = alias.pattern.pattern,
                commands = alias.commands,
                enabled = alias.enabled,
                priority = alias.priority
            )
        }
    }
}

@Serializable
data class HotkeyDto(
    val id: String,
    val key: String, // Название клавиши (для отображения/обратной совместимости)
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val commands: List<String>,
    val enabled: Boolean = true,
    val ignoreNumLock: Boolean = false,
    // Для обратной совместимости (не используется)
    val name: String? = null,
    // Точный код клавиши (для правильного восстановления NumPad)
    val keyCode: Long? = null
) {
    fun toHotkey(): Hotkey? {
        // Если есть keyCode - используем его напрямую
        val parsedKey = if (keyCode != null) {
            androidx.compose.ui.input.key.Key(keyCode)
        } else {
            // Fallback на старый формат
            Hotkey.parseKey(key) ?: return null
        }
        return Hotkey(
            id = id,
            key = parsedKey,
            ctrl = ctrl,
            alt = alt,
            shift = shift,
            commands = commands,
            enabled = enabled,
            ignoreNumLock = ignoreNumLock
        )
    }

    companion object {
        fun fromHotkey(hotkey: Hotkey): HotkeyDto {
            return HotkeyDto(
                id = hotkey.id,
                key = Hotkey.getKeyName(hotkey.key),
                ctrl = hotkey.ctrl,
                alt = hotkey.alt,
                shift = hotkey.shift,
                commands = hotkey.commands,
                enabled = hotkey.enabled,
                ignoreNumLock = hotkey.ignoreNumLock,
                keyCode = hotkey.key.keyCode // Сохраняем точный код
            )
        }
    }
}

@Serializable
data class ConnectionProfileDto(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val encoding: String = "UTF-8"
) {
    fun toConnectionProfile(): ConnectionProfile {
        return ConnectionProfile(
            id = id,
            name = name,
            host = host,
            port = port,
            encoding = encoding
        )
    }

    companion object {
        fun fromConnectionProfile(profile: ConnectionProfile): ConnectionProfileDto {
            return ConnectionProfileDto(
                id = profile.id,
                name = profile.name,
                host = profile.host,
                port = profile.port,
                encoding = profile.encoding
            )
        }
    }
}

@Serializable
data class ClientConfig(
    val triggers: List<TriggerDto> = emptyList(),
    val aliases: List<AliasDto> = emptyList(),
    val hotkeys: List<HotkeyDto> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val tabs: List<com.bylins.client.tabs.TabDto> = emptyList(),
    val encoding: String = "UTF-8",  // Кодировка для telnet (UTF-8, windows-1251, и т.д.)
    val miniMapWidth: Int = 250,  // Ширина боковой панели с миникартой в dp
    val miniMapHeight: Int = 300,  // Высота миникарты в статус-панели в dp
    val theme: String = "DARK",  // Название темы оформления
    val fontFamily: String = "MONOSPACE",  // Семейство шрифтов для вывода игры
    val fontSize: Int = 14,  // Размер шрифта в sp
    val connectionProfiles: List<ConnectionProfileDto> = emptyList(),  // Список профилей подключений
    val currentProfileId: String? = null,  // ID текущего выбранного профиля подключения
    val ignoreNumLock: Boolean = false,  // Игнорировать состояние NumLock для хоткеев
    val activeProfileStack: List<String> = emptyList(),  // Стек активных профилей персонажей
    val hiddenTabs: Set<String> = emptySet(),  // Скрытые вкладки (по ID)
    val lastMapRoomId: String? = null  // Последняя текущая комната на карте
)
