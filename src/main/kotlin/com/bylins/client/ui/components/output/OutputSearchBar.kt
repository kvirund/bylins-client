package com.bylins.client.ui.components.output

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText

/** Строка поиска по выводу (Ctrl+F): поле, счётчик, переключатели Aa/.*, навигация, закрытие. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun OutputSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    count: Int,
    currentIndex: Int,
    regexError: Boolean,
    caseSensitive: Boolean,
    onToggleCase: () -> Unit,
    useRegex: Boolean,
    onToggleRegex: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    activationCounter: Int,
    modifier: Modifier = Modifier
) {
    // Локальное TextFieldValue, чтобы управлять выделением/курсором.
    var tfv by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    // Внешнее изменение query (не из набора в поле) — синхронизируем, курсор в конец.
    LaunchedEffect(query) {
        if (query != tfv.text) tfv = TextFieldValue(query, TextRange(query.length))
    }
    // Активация по Ctrl+F: фокус + выделить весь текст (новый ввод затрёт старый запрос).
    LaunchedEffect(activationCounter) {
        runCatching { focusRequester.requestFocus() }
        tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
    }
    val counter = when {
        query.isEmpty() -> ""
        regexError -> "ошибка regex"
        count == 0 -> "нет"
        else -> "${currentIndex + 1}/$count"
    }
    Row(
        modifier = modifier
            .background(Color(0xFF2B2B2B))
            .border(1.dp, Color(0xFF454545))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText("Поиск:", style = TextStyle(color = Color(0xFFBBBBBB), fontSize = 13.sp))
        Box(
            Modifier
                .padding(start = 6.dp)
                .width(220.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(3.dp))
                .border(1.dp, if (regexError) Color(0xFFCD5C5C) else Color(0xFF555555), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            BasicTextField(
                value = tfv,
                onValueChange = { nv ->
                    tfv = nv
                    if (nv.text != query) onQueryChange(nv.text)
                },
                singleLine = true,
                textStyle = TextStyle(color = Color(0xFFE6E6E6), fontSize = 13.sp),
                cursorBrush = SolidColor(Color(0xFFE6E6E6)),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            e.key == Key.Enter && e.isShiftPressed -> { onPrev(); true }
                            e.key == Key.Enter -> { onNext(); true }
                            e.key == Key.Escape -> { onClose(); true }
                            e.key == Key.F3 && e.isShiftPressed -> { onPrev(); true }
                            e.key == Key.F3 -> { onNext(); true }
                            else -> false
                        }
                    }
            )
        }

        BasicText(
            text = counter,
            style = TextStyle(color = if (regexError || count == 0 && query.isNotEmpty()) Color(0xFFCD5C5C) else Color(0xFF9E9E9E), fontSize = 12.sp),
            modifier = Modifier.padding(start = 8.dp).width(64.dp)
        )

        SearchToggle("Aa", caseSensitive, onToggleCase)
        SearchToggle(".*", useRegex, onToggleRegex)
        SearchButton("↑", onPrev)   // ↑ prev
        SearchButton("↓", onNext)   // ↓ next
        SearchButton("✕", onClose)  // ✕ close
    }
}

@Composable
private fun SearchToggle(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(24.dp)
            .background(if (on) Color(0xFF3A6EA5) else Color(0xFF3A3A3A), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = Color.White, fontSize = 12.sp))
    }
}

@Composable
private fun SearchButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(24.dp)
            .background(Color(0xFF3A3A3A), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = Color.White, fontSize = 13.sp))
    }
}
