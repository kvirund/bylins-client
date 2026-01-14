package com.bylins.client.status

/**
 * Базовый sealed class для элементов статус-панели
 */
sealed class StatusElement {
    abstract val id: String

    /**
     * Прогресс-бар (HP, Mana, Movement и т.д.)
     */
    data class Bar(
        override val id: String,
        val label: String,
        val value: Int,
        val max: Int,
        val color: String = "green",  // "green", "red", "yellow", "blue" или "#RRGGBB"
        val showText: Boolean = true,
        val order: Int = 0
    ) : StatusElement()

    /**
     * Текстовый элемент (Золото, Опыт и т.д.)
     */
    data class Text(
        override val id: String,
        val label: String,
        val value: String,
        val order: Int = 0
    ) : StatusElement()

    /**
     * Список флагов/аффектов
     */
    data class Flags(
        override val id: String,
        val label: String,
        val flags: List<FlagItem>,
        val order: Int = 0
    ) : StatusElement()

    /**
     * Миникарта
     */
    data class MiniMap(
        override val id: String,
        val currentRoomId: String? = null,  // null = использовать текущую комнату из маппера
        val visible: Boolean = true,
        val order: Int = 0
    ) : StatusElement()

    /**
     * Панель пути (маршрут к цели)
     */
    data class PathPanel(
        override val id: String,
        val targetName: String,           // Название цели
        val stepsCount: Int,              // Количество шагов
        val directions: List<String>,     // Список направлений
        val hasClearCallback: Boolean = false,   // Показывать кнопку "Очистить"
        val hasFollowCallback: Boolean = false,  // Показывать кнопку "Следовать"
        val order: Int = 0
    ) : StatusElement()
}

/**
 * Элемент флага/аффекта
 */
data class FlagItem(
    val name: String,
    val active: Boolean = true,
    val color: String = "white",
    val timer: String? = null  // Опционально: "2:30" (игровые часы), "5м" и т.д.
)
