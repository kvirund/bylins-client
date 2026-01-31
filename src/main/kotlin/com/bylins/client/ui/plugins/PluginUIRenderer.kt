package com.bylins.client.ui.plugins

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.plugins.ui.PluginTab
import com.bylins.client.plugins.ui.PluginUINode
import com.bylins.client.ui.theme.LocalAppColorScheme

/**
 * Renders a PluginUINode tree to Compose UI.
 * This allows plugins to define their UI using universal primitives,
 * and the client renders them consistently with the app theme.
 *
 * @param onTextFieldFocusChanged callback when a text field gains/loses focus
 */
@Composable
fun RenderPluginUI(
    node: PluginUINode,
    modifier: Modifier = Modifier,
    onTextFieldFocusChanged: ((Boolean) -> Unit)? = null
) {
    val colorScheme = LocalAppColorScheme.current

    when (node) {
        is PluginUINode.Empty -> {
            // Empty - render nothing
        }

        is PluginUINode.Column -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(node.spacing.dp)
            ) {
                node.children.forEach { child ->
                    RenderPluginUI(child, onTextFieldFocusChanged = onTextFieldFocusChanged)
                }
            }
        }

        is PluginUINode.Row -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(node.spacing.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                node.children.forEach { child ->
                    RenderPluginUI(child, onTextFieldFocusChanged = onTextFieldFocusChanged)
                }
            }
        }

        is PluginUINode.Box -> {
            Box(
                modifier = modifier.padding(node.padding.dp)
            ) {
                RenderPluginUI(node.child, onTextFieldFocusChanged = onTextFieldFocusChanged)
            }
        }

        is PluginUINode.Scrollable -> {
            val scrollState = rememberScrollState()
            val maxHeightValue = node.maxHeight
            val scrollModifier = if (maxHeightValue != null) {
                modifier.heightIn(max = maxHeightValue.dp)
            } else {
                modifier
            }
            Box(modifier = scrollModifier) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)
                ) {
                    RenderPluginUI(node.child, onTextFieldFocusChanged = onTextFieldFocusChanged)
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState)
                )
            }
        }

        is PluginUINode.Text -> {
            val (fontSize, fontWeight) = when (node.style) {
                PluginUINode.TextStyle.TITLE -> 16.sp to FontWeight.Bold
                PluginUINode.TextStyle.SUBTITLE -> 14.sp to FontWeight.SemiBold
                PluginUINode.TextStyle.BODY -> 12.sp to FontWeight.Normal
                PluginUINode.TextStyle.CAPTION -> 10.sp to FontWeight.Normal
                PluginUINode.TextStyle.MONOSPACE -> 12.sp to FontWeight.Normal
            }
            val fontFamily = when (node.style) {
                PluginUINode.TextStyle.MONOSPACE -> FontFamily.Monospace
                else -> FontFamily.Default
            }
            val textColor = when (node.style) {
                PluginUINode.TextStyle.CAPTION -> colorScheme.onSurfaceVariant
                else -> colorScheme.onSurface
            }

            Text(
                text = node.text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                modifier = modifier
            )
        }

        is PluginUINode.SelectableText -> {
            val (fontSize, fontWeight) = when (node.style) {
                PluginUINode.TextStyle.TITLE -> 16.sp to FontWeight.Bold
                PluginUINode.TextStyle.SUBTITLE -> 14.sp to FontWeight.SemiBold
                PluginUINode.TextStyle.BODY -> 12.sp to FontWeight.Normal
                PluginUINode.TextStyle.CAPTION -> 10.sp to FontWeight.Normal
                PluginUINode.TextStyle.MONOSPACE -> 12.sp to FontWeight.Normal
            }
            val fontFamily = when (node.style) {
                PluginUINode.TextStyle.MONOSPACE -> FontFamily.Monospace
                else -> FontFamily.Default
            }

            val maxHeightValue = node.maxHeight
            val scrollState = rememberScrollState()

            Box(
                modifier = modifier
                    .then(if (maxHeightValue != null) Modifier.heightIn(max = maxHeightValue.dp) else Modifier)
                    .background(colorScheme.surface)
                    .border(1.dp, colorScheme.border)
            ) {
                SelectionContainer {
                    Text(
                        text = node.text,
                        color = colorScheme.onSurface,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        lineHeight = fontSize * 1.2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (maxHeightValue != null) Modifier.verticalScroll(scrollState) else Modifier)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (maxHeightValue != null) {
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState)
                    )
                }
            }
        }

        is PluginUINode.Button -> {
            Button(
                onClick = node.onClick,
                enabled = node.enabled,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colorScheme.primary,
                    disabledBackgroundColor = colorScheme.surface
                ),
                modifier = modifier
            ) {
                Text(
                    text = node.text,
                    color = if (node.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant
                )
            }
        }

        is PluginUINode.TextField -> {
            // Используем локальное состояние чтобы ввод не сбрасывался при обновлении UI
            val key = node.label ?: node.placeholder ?: "textfield"
            var localValue by remember(key) { mutableStateOf(node.value) }
            var hasFocus by remember { mutableStateOf(false) }

            // Синхронизируем с внешним значением только когда нет фокуса
            LaunchedEffect(node.value) {
                if (!hasFocus) {
                    localValue = node.value
                }
            }

            // Используем placeholder вместо label для компактности
            val placeholderText = node.placeholder ?: node.label

            OutlinedTextField(
                value = localValue,
                onValueChange = { newValue ->
                    localValue = newValue
                    // Не вызываем onValueChange на каждый символ - только при потере фокуса
                },
                placeholder = placeholderText?.let { { Text(it, color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) } },
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = colorScheme.onSurface,
                    focusedBorderColor = colorScheme.success,
                    unfocusedBorderColor = colorScheme.border,
                    backgroundColor = colorScheme.surface
                ),
                modifier = modifier.onFocusChanged { focusState ->
                    val wasFocused = hasFocus
                    hasFocus = focusState.isFocused
                    onTextFieldFocusChanged?.invoke(focusState.isFocused)

                    // Коммитим значение при потере фокуса
                    if (wasFocused && !focusState.isFocused && localValue != node.value) {
                        node.onValueChange(localValue)
                    }
                }
            )
        }

        is PluginUINode.Checkbox -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
            ) {
                Checkbox(
                    checked = node.checked,
                    onCheckedChange = node.onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = colorScheme.success,
                        uncheckedColor = colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = node.label,
                    color = colorScheme.onSurface,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        is PluginUINode.Slider -> {
            val sliderLabel = node.label
            Column(modifier = modifier) {
                if (sliderLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = sliderLabel,
                            color = colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(node.value * 100).toInt()}%",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                Slider(
                    value = node.value,
                    onValueChange = node.onValueChange,
                    valueRange = node.range,
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.success,
                        activeTrackColor = colorScheme.success,
                        inactiveTrackColor = colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }
        }

        is PluginUINode.ProgressBar -> {
            val progressLabel = node.label
            Column(modifier = modifier) {
                if (progressLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = progressLabel,
                            color = colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(node.progress * 100).toInt()}%",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = node.progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = colorScheme.success,
                    backgroundColor = colorScheme.surface
                )
            }
        }

        is PluginUINode.Dropdown -> {
            var expanded by remember { mutableStateOf(false) }
            val dropdownLabel = node.label

            Column(modifier = modifier) {
                if (dropdownLabel != null) {
                    Text(
                        text = dropdownLabel,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = node.options.getOrElse(node.selectedIndex) { "" },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.success,
                            unfocusedBorderColor = colorScheme.border
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(colorScheme.surface)
                    ) {
                        node.options.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                onClick = {
                                    node.onSelect(index)
                                    expanded = false
                                },
                                modifier = Modifier.background(colorScheme.surface)
                            ) {
                                Text(option, color = colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        is PluginUINode.Divider -> {
            Divider(
                color = colorScheme.divider,
                thickness = node.thickness.dp,
                modifier = modifier
            )
        }

        is PluginUINode.Spacer -> {
            Spacer(modifier = modifier.height(node.height.dp))
        }
    }
}

/**
 * Convenience Composable for rendering a PluginTab's content.
 * Automatically observes the content StateFlow and re-renders on changes.
 */
@Composable
fun RenderPluginTab(
    tab: PluginTab,
    modifier: Modifier = Modifier,
    onTextFieldFocusChanged: ((Boolean) -> Unit)? = null
) {
    val content by tab.content.collectAsState()
    val colorScheme = LocalAppColorScheme.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(16.dp)
    ) {
        // SelectionContainer убран - он мешает кликам по кнопкам
        RenderPluginUI(content, onTextFieldFocusChanged = onTextFieldFocusChanged)
    }
}
