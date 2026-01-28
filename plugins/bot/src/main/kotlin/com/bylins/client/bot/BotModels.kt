package com.bylins.client.bot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Модели данных для бота - информация о мобах, предметах, аффектах и т.д.
 * Используются для обмена между парсерами, API и ядром бота
 */

// ============================================
// Информация о мобах и игроках в комнате
// ============================================

/**
 * Информация о мобе, видимом в комнате
 */
@Serializable
data class MobInfo(
    val name: String,                    // Полное имя моба
    val shortName: String? = null,       // Короткое имя для команд (kill mob.short)
    val level: Int? = null,              // Уровень (если известен)
    val hpPercent: Int? = null,          // Процент HP (из prompt или осмотра)
    val condition: MobCondition? = null, // Состояние (невредим, ранен, при смерти)
    val position: Position? = null,      // Позиция (стоит, сидит, лежит, сражается)
    val isAggressive: Boolean? = null,   // Агрессивен ли
    val isFighting: Boolean = false,     // В бою ли сейчас
    val fightingTarget: String? = null,  // С кем сражается (имя)
    val affects: List<String> = emptyList(), // Видимые аффекты
    val roomIndex: Int = 0               // Индекс в комнате (для команд типа kill 2.mob)
)

/**
 * Состояние здоровья моба
 */
@Serializable
enum class MobCondition {
    EXCELLENT,      // Превосходное / Невредим
    GOOD,           // Хорошее
    SLIGHTLY_HURT,  // Слегка ранен
    HURT,           // Ранен
    BADLY_HURT,     // Тяжело ранен
    AWFUL,          // Ужасное / При смерти
    UNKNOWN
}

/**
 * Позиция существа
 */
@Serializable
enum class Position {
    STANDING,   // Стоит
    SITTING,    // Сидит
    RESTING,    // Отдыхает
    SLEEPING,   // Спит
    FIGHTING,   // Сражается
    STUNNED,    // Оглушён
    DEAD,       // Мёртв
    UNKNOWN
}

/**
 * Информация об игроке в комнате
 */
@Serializable
data class PlayerInfo(
    val name: String,
    val title: String? = null,
    val clan: String? = null,
    val level: Int? = null,
    val className: String? = null,
    val position: Position? = null,
    val isFighting: Boolean = false,
    val fightingTarget: String? = null,
    val isGroupMember: Boolean = false,
    val affects: List<String> = emptyList()
)

// ============================================
// Информация о предметах
// ============================================

/**
 * Информация о предмете (в инвентаре, на земле, в экипировке)
 */
@Serializable
data class ItemInfo(
    val name: String,                    // Полное имя предмета
    val shortName: String? = null,       // Короткое имя для команд
    val type: ItemType? = null,          // Тип предмета
    val level: Int? = null,              // Уровень предмета
    val condition: ItemCondition? = null, // Состояние
    val count: Int = 1,                  // Количество (для стекаемых)
    val weight: Double? = null,          // Вес
    val value: Int? = null,              // Цена
    val equipped: Boolean = false,       // Надет ли
    val wearSlot: WearSlot? = null,      // Слот экипировки
    val stats: Map<String, Int> = emptyMap(), // Статы (+str, +int и т.д.)
    val affects: List<String> = emptyList(),  // Аффекты
    val flags: List<String> = emptyList()     // Флаги (magic, glow, hum и т.д.)
)

@Serializable
enum class ItemType {
    WEAPON,
    ARMOR,
    LIGHT,
    SCROLL,
    WAND,
    STAFF,
    POTION,
    TREASURE,
    FOOD,
    DRINK,
    CONTAINER,
    KEY,
    BOAT,
    MONEY,
    OTHER
}

@Serializable
enum class ItemCondition {
    PERFECT,    // Отличное
    GOOD,       // Хорошее
    WORN,       // Поношенное
    DAMAGED,    // Повреждённое
    BROKEN      // Сломано
}

@Serializable
enum class WearSlot {
    LIGHT,          // Источник света
    FINGER_R,       // Правый палец
    FINGER_L,       // Левый палец
    NECK_1,         // Шея 1
    NECK_2,         // Шея 2
    BODY,           // Тело
    HEAD,           // Голова
    LEGS,           // Ноги
    FEET,           // Ступни
    HANDS,          // Кисти
    ARMS,           // Руки
    SHIELD,         // Щит
    ABOUT,          // Вокруг тела
    WAIST,          // Пояс
    WRIST_R,        // Правое запястье
    WRIST_L,        // Левое запястье
    WIELD,          // Оружие
    HOLD,           // В руке
    BOTH_HANDS,     // Обе руки
    QUIVER          // Колчан
}

// ============================================
// Информация о персонаже
// ============================================

/**
 * Активный аффект на персонаже
 */
@Serializable
data class AffectInfo(
    val name: String,               // Название аффекта
    val type: AffectType? = null,   // Тип (бафф/дебафф)
    val duration: Int? = null,      // Оставшееся время (раунды или секунды)
    val level: Int? = null,         // Уровень заклинания
    val modifier: String? = null,   // Модификатор (+5 str и т.д.)
    val source: String? = null      // Источник (имя заклинания)
)

@Serializable
enum class AffectType {
    BUFF,       // Положительный эффект
    DEBUFF,     // Отрицательный эффект
    NEUTRAL     // Нейтральный
}

/**
 * Информация о навыке персонажа
 *
 * Примечание: В Былинах нет маны. Заклинания требуют заучивания (команда "запомн").
 */
@Serializable
data class SkillInfo(
    val name: String,               // Название навыка
    val level: Int? = null,         // Уровень владения (%)
    val cooldown: Int? = null,      // Кулдаун (раунды до готовности)
    val isReady: Boolean = true,    // Готов к использованию
    val moveCost: Int? = null,      // Стоимость движения
    val memorized: Int? = null      // Заучено копий заклинания
)

// ============================================
// Боевая информация
// ============================================

/**
 * Боевое событие (для лога боя и обучения)
 */
@Serializable
data class CombatEvent(
    val type: CombatEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String? = null,      // Кто совершил действие
    val target: String? = null,      // Цель действия
    val damage: Int? = null,         // Нанесённый/полученный урон
    val damageType: String? = null,  // Тип урона (огонь, холод, физический)
    val skill: String? = null,       // Использованный навык
    val isCritical: Boolean = false, // Критический удар
    val isMiss: Boolean = false,     // Промах
    val isBlocked: Boolean = false,  // Заблокировано
    val isParried: Boolean = false,  // Парировано
    val isDodged: Boolean = false,   // Уклонение
    val message: String? = null      // Оригинальное сообщение
)

// ============================================
// Состояние персонажа для ML
// ============================================

/**
 * Полное состояние персонажа для принятия решений
 *
 * Примечание: В Былинах нет маны - используется система заучивания заклинаний.
 * Заклинания заучиваются командой "запомн" и имеют таймаут.
 */
@Serializable
data class CharacterState(
    // Базовые характеристики
    val hp: Int,
    val maxHp: Int,
    val move: Int,
    val maxMove: Int,
    val level: Int,
    val experience: Long,

    // Позиция
    val position: Position,
    val roomId: String?,
    val zoneId: String?,

    // Комната
    val terrain: String? = null,        // Тип поверхности (TERRAIN из MSDP)
    val exits: List<String> = emptyList(), // Выходы (EXITS из MSDP)

    // Боевое состояние
    val isInCombat: Boolean,
    val targetName: String? = null,
    val targetHpPercent: Int? = null,

    // Аффекты
    val affects: List<String> = emptyList(),

    // Ресурсы
    val gold: Int = 0,
    val inventoryCount: Int = 0,
    val inventoryWeight: Double = 0.0,
    val maxWeight: Double = 0.0,

    // Группа
    val groupSize: Int = 1,
    val tankHpPercent: Int? = null
) {
    val hpPercent: Int get() = if (maxHp > 0) (hp * 100) / maxHp else 0
    val movePercent: Int get() = if (maxMove > 0) (move * 100) / maxMove else 0

    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): CharacterState = Json.decodeFromString(serializer(), json)
    }
}

// ============================================
// Результаты парсинга LLM
// ============================================

/**
 * Результат парсинга описания комнаты
 */
@Serializable
data class RoomParseResult(
    val mobs: List<MobInfo> = emptyList(),
    val players: List<PlayerInfo> = emptyList(),
    val items: List<ItemInfo> = emptyList(),
    val exits: List<String> = emptyList(),
    val features: List<String> = emptyList(),  // Особенности комнаты
    val isDangerous: Boolean? = null,
    val confidence: Double = 1.0
)

/**
 * Результат парсинга боевого сообщения
 */
@Serializable
data class CombatParseResult(
    val event: CombatEvent?,
    val confidence: Double = 1.0,
    val rawText: String
)

/**
 * Результат парсинга результата "осмотреть"
 */
@Serializable
data class InspectParseResult(
    val mobInfo: MobInfo? = null,
    val playerInfo: PlayerInfo? = null,
    val itemInfo: ItemInfo? = null,
    val estimatedLevel: Int? = null,
    val estimatedHp: Int? = null,
    val confidence: Double = 1.0
)
