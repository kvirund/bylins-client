package com.bylins.client.ui.components.output

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextLayoutResult
import com.bylins.client.ui.scroll.OutputScrollController
import com.bylins.client.ui.scroll.OutputSelection

/**
 * Долгоживущее состояние панели вывода для одной вкладки.
 *
 * Хранится в ClientState (а не в remember внутри OutputPanel), чтобы переживать
 * уход OutputPanel из композиции при переключении верхнеуровневых вкладок —
 * тогда позиция скролла, режим follow/split и выделение не сбрасываются.
 *
 * Инкапсулирует Compose-зависимости (mutableStateOf, TextLayoutResult), поэтому
 * пакет ui.scroll остаётся чистым и тестируемым.
 */
class OutputViewHolder {
    val controller = OutputScrollController()
    val selection = OutputSelection()

    // Позиции скролла в пикселях (snapshot-state — изменение перерисовывает Canvas)
    private val _liveScrollPx = mutableStateOf(0f)
    var liveScrollPx: Float
        get() = _liveScrollPx.value
        set(value) { _liveScrollPx.value = value }

    private val _scrollbackScrollPx = mutableStateOf(0f)
    var scrollbackScrollPx: Float
        get() = _scrollbackScrollPx.value
        set(value) { _scrollbackScrollPx.value = value }

    // Последний результат измерения текста активной панели (для маппинга seq<->px).
    // Обычный var — используется императивно, не должен триггерить рекомпозицию.
    var lastLayout: TextLayoutResult? = null

    // Идёт ли сейчас выделение мышью. Пока true — автоскролл (follow) к низу
    // заморожен, чтобы заякоренное выделение не уезжало за экран при новом тексте.
    var isSelecting = false

    // Ревизия выделения: инкремент заставляет панель перерисовать подсветку,
    // т.к. сама OutputSelection — не snapshot-state.
    private val _selectionRevision = mutableStateOf(0)
    val selectionRevision: Int get() = _selectionRevision.value
    fun bumpSelection() { _selectionRevision.value++ }

    // Наблюдаемое зеркало controller.isSplit (controller — чистый, не snapshot-state).
    // Синхронизируется после изменения режима, чтобы перестроить раздвоение панелей.
    private val _split = mutableStateOf(false)
    var split: Boolean
        get() = _split.value
        set(value) { _split.value = value }

    /** Синхронизирует наблюдаемый split с состоянием контроллера. */
    fun syncSplit() { _split.value = controller.isSplit }
}
