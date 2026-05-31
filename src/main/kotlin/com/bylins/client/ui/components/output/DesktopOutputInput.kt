package com.bylins.client.ui.components.output

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ui.AnsiParser
import com.bylins.client.ui.scroll.BufferGeometry
import com.bylins.client.ui.scroll.ContentSnapshot
import com.bylins.client.ui.scroll.ScrollTarget

private val SELECTION_COLOR = Color(0x553A6EA5)
private val DIVIDER_COLOR = Color(0xFF555555)
private val DIVIDER_HEIGHT = 6.dp

/**
 * Панель вывода со split-scrollback и собственным выделением (desktop-слой ввода).
 *
 * Внизу — одно окно с автоскроллом. При скролле вверх делится: верхняя панель —
 * скроллбэк (заякорена), нижняя — живой хвост (всегда автоскролл), между ними
 * перетаскиваемый разделитель. Докрутка скроллбэка до низа схлопывает обратно.
 * Выделение (по всему буферу) и drag живут на корне и не рвутся при раздвоении/
 * схлопывании и приходе нового текста. Копирование Ctrl+C/Ctrl+Insert, Ctrl+A.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScrollbackOutputView(
    snapshot: ContentSnapshot,
    holder: OutputViewHolder,
    splitFraction: Float,
    onSplitFractionChange: (Float) -> Unit,
    fontFamily: FontFamily,
    fontSize: Int,
    emptyPlaceholder: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val controller = holder.controller
    val selection = holder.selection
    val ansiParser = remember { AnsiParser() }

    val limitedRaw = remember(snapshot.text) {
        if (snapshot.text.length > 100_000) lastLines(snapshot.text, 1000) else snapshot.text
    }
    val effectiveFirstSeq = remember(snapshot, limitedRaw) {
        snapshot.firstSeq + (snapshot.lineCount - ContentSnapshot.countLines(limitedRaw))
    }
    val annotated = remember(limitedRaw, emptyPlaceholder) {
        if (limitedRaw.isEmpty()) emptyPlaceholder else ansiParser.parse(limitedRaw)
    }
    val plainText = annotated.text
    val isEmpty = limitedRaw.isEmpty()
    val geometry = BufferGeometry(
        firstSeq = effectiveFirstSeq,
        lineCount = if (isEmpty) 0 else ContentSnapshot.countLines(limitedRaw)
    )

    val style = remember(fontFamily, fontSize) {
        TextStyle(
            color = Color(0xFFBBBBBB),
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 4).sp
        )
    }
    val lineHeightPx = with(density) { (fontSize + 4).sp.toPx() }
    val dividerPx = with(density) { DIVIDER_HEIGHT.toPx() }

    val split = holder.split && !isEmpty

    Box(modifier.background(Color.Black).padding(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scrollbarStrip = 12.dp
            val scrollbarStripPx = with(density) { scrollbarStrip.toPx() }
            val widthPx = (with(density) { maxWidth.toPx() } - scrollbarStripPx).toInt().coerceAtLeast(1)
            val fullViewportPx = with(density) { maxHeight.toPx() }

            val layout = remember(annotated, widthPx, style) {
                measurer.measure(
                    text = annotated,
                    style = style,
                    softWrap = true,
                    constraints = Constraints(maxWidth = widthPx)
                )
            }
            holder.lastLayout = layout
            val contentHeight = layout.size.height.toFloat()

            // Высоты панелей считаем из доли (разделитель фиксирован) — без лага измерения
            val available = (fullViewportPx - dividerPx).coerceAtLeast(0f)
            val bottomPaneHeightPx = if (split) available * splitFraction else 0f
            val topPaneHeightPx = if (split) available - bottomPaneHeightPx else fullViewportPx
            val activeViewportPx = if (split) topPaneHeightPx else fullViewportPx
            val maxScrollActive = maxScrollOf(contentHeight, activeViewportPx)
            val bottomScrollPx = maxScrollOf(contentHeight, bottomPaneHeightPx)

            // Применяем целевую позицию скроллбэка при изменении контента/режима/размеров
            LaunchedEffect(snapshot, layout, fullViewportPx, split, splitFraction) {
                val ms = maxScrollOf(contentHeight, activeViewportPx)
                when (val target = controller.onContentChanged(geometry)) {
                    ScrollTarget.Bottom -> holder.scrollbackScrollPx = ms
                    is ScrollTarget.ToLine -> holder.scrollbackScrollPx =
                        seqToTopPx(layout, plainText, effectiveFirstSeq, target.seq).coerceIn(0f, ms)
                    ScrollTarget.None -> {}
                }
            }

            val scrollbackPx = holder.scrollbackScrollPx.coerceIn(0f, maxScrollActive)

            val selRevision = holder.selectionRevision
            val selectionPath = remember(selRevision, layout, effectiveFirstSeq, plainText) {
                if (isEmpty) null
                else selection.charRange(effectiveFirstSeq, plainText)
                    ?.let { r -> layout.getPathForRange(r.first, r.last + 1) }
            }

            // --- Действия (пересоздаются каждую рекомпозицию, видят актуальные значения) ---
            val userScrollTo: (Float) -> Unit = { target ->
                val clamped = target.coerceIn(0f, maxScrollActive)
                holder.scrollbackScrollPx = clamped
                val atBottom = clamped >= maxScrollActive - lineHeightPx
                controller.onUserScroll(atBottom, topSeqAt(layout, plainText, effectiveFirstSeq, clamped))
                holder.syncSplit()
            }
            val collapse: () -> Unit = {
                controller.jumpToBottom()
                holder.syncSplit()
                holder.scrollbackScrollPx = maxScrollOf(contentHeight, fullViewportPx)
            }
            // Указатель -> точка выделения (с учётом того, в какой панели курсор)
            val pointToSel: (Offset) -> com.bylins.client.ui.scroll.SelPoint = { pos ->
                val inBottom = split && pos.y >= topPaneHeightPx + dividerPx
                val contentY = if (inBottom) {
                    (pos.y - topPaneHeightPx - dividerPx) + bottomScrollPx
                } else {
                    pos.y + scrollbackPx
                }
                pointToSelPoint(layout, plainText, effectiveFirstSeq, pos.x, contentY)
            }
            val copySelection: () -> Unit = {
                if (!isEmpty) {
                    val text = selection.copyText(effectiveFirstSeq, plainText)
                    if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text))
                }
            }
            val handleKey: (KeyEvent) -> Boolean = handleKey@{ event ->
                if (event.type != KeyEventType.KeyDown) return@handleKey false
                when {
                    event.isCtrlPressed && event.key == Key.A -> {
                        selection.selectAll(effectiveFirstSeq, geometry.lineCount); holder.bumpSelection(); true
                    }
                    event.isCtrlPressed && (event.key == Key.C || event.key == Key.Insert) -> { copySelection(); true }
                    event.key == Key.PageDown -> { userScrollTo(scrollbackPx + activeViewportPx); true }
                    event.key == Key.PageUp -> { userScrollTo(scrollbackPx - activeViewportPx); true }
                    event.key == Key.DirectionDown -> { userScrollTo(scrollbackPx + lineHeightPx); true }
                    event.key == Key.DirectionUp -> { userScrollTo(scrollbackPx - lineHeightPx); true }
                    event.key == Key.MoveHome -> { userScrollTo(0f); true }
                    event.key == Key.MoveEnd -> { userScrollTo(maxScrollActive); true }
                    else -> false
                }
            }

            // Ссылки на актуальные значения для долгоживущего drag-жеста выделения
            val pointToSelRef by rememberUpdatedState(pointToSel)
            val userScrollToRef by rememberUpdatedState(userScrollTo)
            val scrollbackRef by rememberUpdatedState(scrollbackPx)
            val activeViewportRef by rememberUpdatedState(activeViewportPx)
            val isEmptyRef by rememberUpdatedState(isEmpty)
            // Актуальные значения для долгоживущего drag разделителя (иначе захватится
            // устаревшая доля и разделитель «дёргается», а не двигается)
            val splitFractionRef by rememberUpdatedState(splitFraction)
            val dividerAvailRef by rememberUpdatedState(available.coerceAtLeast(1f))

            // --- Корневой контейнер: ввод (колесо/клавиши/drag) на нём, панели — дети ---
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent(handleKey)
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) userScrollTo(scrollbackPx + dy * lineHeightPx * 3f)
                    }
                    .pointerInput(Unit) {
                        // Одиночный клик без перетаскивания сбрасывает выделение
                        detectTapGestures(onTap = {
                            focusRequester.requestFocus()
                            if (!selection.isEmpty) {
                                selection.clear()
                                holder.bumpSelection()
                            }
                        })
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                if (isEmptyRef) return@detectDragGestures
                                focusRequester.requestFocus()
                                selection.start(pointToSelRef(pos))
                                holder.bumpSelection()
                            },
                            onDrag = { change, _ ->
                                if (isEmptyRef) return@detectDragGestures
                                val pos = change.position
                                val edge = lineHeightPx
                                if (pos.y < edge) userScrollToRef(scrollbackRef - lineHeightPx)
                                else if (pos.y < activeViewportRef && pos.y > activeViewportRef - edge)
                                    userScrollToRef(scrollbackRef + lineHeightPx)
                                selection.extendTo(pointToSelRef(pos))
                                holder.bumpSelection()
                            }
                        )
                    }
            ) {
                // Контент панелей, сужен под полосу скроллбара справа
                Box(Modifier.fillMaxSize().padding(end = scrollbarStrip)) {
                    if (split) {
                        Column(Modifier.fillMaxSize()) {
                            OutputCanvas(
                                layout = layout,
                                scrollPx = scrollbackPx,
                                selectionPath = selectionPath,
                                selectionColor = SELECTION_COLOR,
                                modifier = Modifier.fillMaxWidth().height(with(density) { topPaneHeightPx.toDp() })
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(DIVIDER_HEIGHT)
                                    .background(DIVIDER_COLOR)
                                    .pointerHoverIcon(PointerIcon.Default)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            onSplitFractionChange(splitFractionRef - dragAmount / dividerAvailRef)
                                        }
                                    }
                            )
                            OutputCanvas(
                                layout = layout,
                                scrollPx = bottomScrollPx,
                                selectionPath = selectionPath,
                                selectionColor = SELECTION_COLOR,
                                modifier = Modifier.fillMaxWidth().height(with(density) { bottomPaneHeightPx.toDp() })
                            )
                        }
                    } else {
                        OutputCanvas(
                            layout = layout,
                            scrollPx = scrollbackPx,
                            selectionPath = selectionPath,
                            selectionColor = SELECTION_COLOR,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Единый интерактивный скроллбар на всю высоту (общий для обеих панелей)
                OutputScrollbar(
                    scrollPx = scrollbackPx,
                    maxScroll = maxScrollActive,
                    viewportPx = activeViewportPx,
                    contentHeightPx = contentHeight,
                    onScrollTo = userScrollTo,
                    modifier = Modifier.align(Alignment.CenterEnd).width(scrollbarStrip).fillMaxHeight()
                )

                // Кнопка возврата в одно-панельный режим одним кликом (левее скроллбара)
                if (split) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = scrollbarStrip + 4.dp, bottom = 12.dp)
                            .size(30.dp)
                            .background(Color(0xCC2E2E2E), CircleShape)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(Unit) { detectTapGestures(onTap = { collapse() }) },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "▼",
                            style = TextStyle(color = Color.White, fontSize = 14.sp)
                        )
                    }
                }
            }
        }
    }
}
