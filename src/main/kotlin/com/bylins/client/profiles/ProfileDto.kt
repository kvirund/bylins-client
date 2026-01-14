package com.bylins.client.profiles

import com.bylins.client.config.AliasDto
import com.bylins.client.config.HotkeyDto
import com.bylins.client.config.TriggerDto
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.time.Instant

/**
 * DTO для сериализации профиля в JSON (profile.json)
 */
@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val description: String = "",
    val requires: List<String> = emptyList(),
    val triggers: List<TriggerDto> = emptyList(),
    val aliases: List<AliasDto> = emptyList(),
    val hotkeys: List<HotkeyDto> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val createdAt: String? = null,  // ISO-8601 timestamp
    val updatedAt: String? = null   // ISO-8601 timestamp
) {
    /**
     * Конвертирует DTO в доменную модель Profile
     * @param scriptsDir путь к папке scripts/ профиля (если есть)
     */
    fun toProfile(scriptsDir: Path? = null): Profile {
        return Profile(
            id = id,
            name = name,
            description = description,
            requires = requires,
            triggers = triggers.map { it.toTrigger() },
            aliases = aliases.map { it.toAlias() },
            hotkeys = hotkeys.mapNotNull { it.toHotkey() },  // toHotkey() может вернуть null
            variables = variables,
            scriptsDir = scriptsDir,
            createdAt = createdAt?.let { parseInstant(it) } ?: Instant.now(),
            updatedAt = updatedAt?.let { parseInstant(it) } ?: Instant.now()
        )
    }

    companion object {
        /**
         * Создает DTO из доменной модели Profile
         */
        fun fromProfile(profile: Profile): ProfileDto {
            return ProfileDto(
                id = profile.id,
                name = profile.name,
                description = profile.description,
                requires = profile.requires,
                triggers = profile.triggers.map { TriggerDto.fromTrigger(it) },
                aliases = profile.aliases.map { AliasDto.fromAlias(it) },
                hotkeys = profile.hotkeys.map { HotkeyDto.fromHotkey(it) },
                variables = profile.variables,
                createdAt = profile.createdAt.toString(),
                updatedAt = profile.updatedAt.toString()
            )
        }

        private fun parseInstant(str: String): Instant {
            return try {
                Instant.parse(str)
            } catch (e: Exception) {
                Instant.now()
            }
        }
    }
}
