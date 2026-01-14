package com.bylins.client.profiles

import com.bylins.client.aliases.Alias
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.triggers.Trigger
import java.nio.file.Path
import java.time.Instant

/**
 * Профиль персонажа - набор триггеров, алиасов, хоткеев и переменных.
 * Профили могут накладываться друг на друга в стеке.
 */
data class Profile(
    val id: String,                              // ID = имя папки профиля
    val name: String,                            // Отображаемое имя
    val description: String = "",                // Описание профиля
    val requires: List<String> = emptyList(),    // Зависимости (ID других профилей)
    val triggers: List<Trigger> = emptyList(),   // Триггеры профиля
    val aliases: List<Alias> = emptyList(),      // Алиасы профиля
    val hotkeys: List<Hotkey> = emptyList(),     // Хоткеи профиля
    val variables: Map<String, String> = emptyMap(), // Переменные профиля
    val scriptsDir: Path? = null,                // Путь к папке scripts/ профиля
    val createdAt: Instant = Instant.now(),      // Дата создания
    val updatedAt: Instant = Instant.now()       // Дата последнего изменения
)

/**
 * Результат проверки зависимостей профиля
 */
sealed class DependencyResult {
    /** Все зависимости удовлетворены */
    object Satisfied : DependencyResult()

    /** Некоторые зависимости отсутствуют в активном стеке */
    data class Missing(val missingIds: List<String>) : DependencyResult()
}

/**
 * Исключение при нарушении зависимостей профиля
 */
class DependencyException(message: String) : Exception(message)
