package com.bylins.client.ui.components.output

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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

/**
 * Панель вывода со split-scrollback и собственным выделением (desktop-слой ввода).
 *
 * В этом коммите реализован одно-панельный режим: корректный follow/anchor
 * (нет дёрганья к низу при скролле вверх; при вытеснении строки позиция ползёт
 * к началу), выделение мышью по всему буферу, копирование Ctrl+C/Ctrl+Insert,
 * Ctrl+A. Раздвоение (живой хвост) добавляется отдельно.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScrollbackOutputView(
    snapshot: ContentSnapshot,
    holder: OutputViewHolder,
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

    // Ограничиваем парсинг последними строками для больших буферов
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

    androidx.compose.foundation.layout.Box(
        modifier = modifier.background(Color.Black).padding(8.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
            val viewportPx = with(density) { maxHeight.toPx() }

            val layout = remember(annotated, widthPx, style) {
                measurer.measure(
                    text = annotated,
                    style = style,
                    softWrap = true,
                    constraints = Constraints(maxWidth = widthPx)
                )
            }
            holder.lastLayout = layout
            val maxScroll = maxScrollOf(layout.size.height.toFloat(), viewportPx)

            // Применяем целевую позицию при изменении контента/размеров
            LaunchedEffect(snapshot, layout, viewportPx) {
                val ms = maxScrollOf(layout.size.height.toFloat(), viewportPx)
                when (val target = controller.onContentChanged(geometry)) {
                    ScrollTarget.Bottom -> holder.scrollbackScrollPx = ms
                    is ScrollTarget.ToLine -> holder.scrollbackScrollPx =
                        seqToTopPx(layout, plainText, effectiveFirstSeq, target.seq).coerceIn(0f, ms)
                    ScrollTarget.None -> {}
                }
            }

            val scrollPx = holder.scrollbackScrollPx.coerceIn(0f, maxScroll)

            // Подсветка выделения (зависит от ревизии выделения для перерисовки)
            val selRevision = holder.selectionRevision
            val selectionPath = remember(selRevision, layout, effectiveFirstSeq, plainText) {
                if (isEmpty) null
                else selection.charRange(effectiveFirstSeq, plainText)
                    ?.let { r -> layout.getPathForRange(r.first, r.last + 1) }
            }

            fun userScrollTo(target: Float) {
                val clamped = target.coerceIn(0f, maxScroll)
                holder.scrollbackScrollPx = clamped
                val atBottom = clamped >= maxScroll - lineHeightPx
                val topSeq = topSeqAt(layout, plainText, effectiveFirstSeq, clamped)
                controller.onUserScroll(atBottom, topSeq)
            }

            fun copySelection() {
                if (isEmpty) return
                val text = selection.copyText(effectiveFirstSeq, plainText)
                if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text))
            }

            fun handleKey(event: KeyEvent): Boolean {
                if (event.type != KeyEventType.KeyDown) return false
                return when {
                    event.isCtrlPressed && event.key == Key.A -> {
                        selection.selectAll(effectiveFirstSeq, geometry.lineCount)
                        holder.bumpSelection(); true
                    }
                    event.isCtrlPressed && (event.key == Key.C || event.key == Key.Insert) -> {
                        copySelection(); true
                    }
                    event.key == Key.PageDown -> { userScrollTo(scrollPx + viewportPx); true }
                    event.key == Key.PageUp -> { userScrollTo(scrollPx - viewportPx); true }
                    event.key == Key.DirectionDown -> { userScrollTo(scrollPx + lineHeightPx); true }
                    event.key == Key.DirectionUp -> { userScrollTo(scrollPx - lineHeightPx); true }
                    event.key == Key.MoveHome -> { userScrollTo(0f); true }
                    event.key == Key.MoveEnd -> { userScrollTo(maxScroll); true }
                    else -> false
                }
            }

            OutputCanvas(
                layout = layout,
                scrollPx = scrollPx,
                selectionPath = selectionPath,
                selectionColor = SELECTION_COLOR,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent(::handleKey)
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) userScrollTo(scrollPx + dy * lineHeightPx * 3f)
                    }
                    .pointerInput(layout, effectiveFirstSeq, plainText, maxScroll, isEmpty) {
                        if (isEmpty) return@pointerInput
                        detectDragGestures(
                            onDragStart = { pos ->
                                focusRequester.requestFocus()
                                selection.start(
                                    pointToSelPoint(layout, plainText, effectiveFirstSeq, pos.x, pos.y + holder.scrollbackScrollPx)
                                )
                                holder.bumpSelection()
                            },
                            onDrag = { change, _ ->
                                val pos = change.position
                                // Авто-скролл при выходе указателя за границы вьюпорта
                                val edge = lineHeightPx
                                if (pos.y < edge) userScrollTo(holder.scrollbackScrollPx - lineHeightPx)
                                else if (pos.y > viewportPx - edge) userScrollTo(holder.scrollbackScrollPx + lineHeightPx)
                                selection.extendTo(
                                    pointToSelPoint(layout, plainText, effectiveFirstSeq, pos.x, pos.y + holder.scrollbackScrollPx)
                                )
                                holder.bumpSelection()
                            }
                        )
                    }
            )
        }
    }
}
