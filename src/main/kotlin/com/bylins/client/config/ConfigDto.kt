package com.bylins.client.config

import com.bylins.client.aliases.Alias
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
data class ClientConfig(
    val triggers: List<TriggerDto> = emptyList(),
    val aliases: List<AliasDto> = emptyList()
)
