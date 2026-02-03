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
        val showMax: Boolean = true,  // false = показывать только value без "/ max"
        val order: Int = 0,
        val hint: String? = null      // Подсказка при наведении
    ) : StatusElement()

    /**
     * Текстовый элемент (Золото, Опыт и т.д.)
     * Если value = null, показывается только label без двоеточия
     *
     * @param color Цвет текста: "white", "red", "green", "yellow", "blue", "cyan", "magenta" или "#RRGGBB"
     * @param bold Жирный шрифт
     * @param background Цвет фона карточки (null = без фона)
     * @param hint Подсказка при наведении
     */
    data class Text(
        override val id: String,
        val label: String,
        val value: String? = null,
        val color: String? = null,      // null = цвет по умолчанию из темы
        val bold: Boolean = false,
        val background: String? = null, // null = без фона, иначе карточка с закруглёнными углами
        val order: Int = 0,
        val hint: String? = null
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

    /**
     * Группа элементов (для логической группировки параметров)
     */
    data class Group(
        override val id: String,
        val label: String,                // Название группы
        val elements: List<StatusElement>, // Дочерние элементы
        val collapsed: Boolean = false,   // Свёрнута ли группа
        val order: Int = 0
    ) : StatusElement()

    /**
     * Значение с модификатором (базовое + бонус/штраф)
     * Пример: "Сила: 18 (15+3)" или "Ловкость: 12 (15-3)"
     */
    data class ModifiedValue(
        override val id: String,
        val label: String,
        val value: Int,                   // Эффективное значение
        val base: Int? = null,            // Базовое значение (если известно)
        val modifier: Int? = null,        // Модификатор (+3 или -3)
        val color: String? = null,        // Цвет значения
        val order: Int = 0,
        val hint: String? = null          // Подсказка при наведении
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
