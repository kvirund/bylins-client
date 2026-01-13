package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.util.UUID

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HotkeyDialog(
    hotkey: Hotkey? = null,
    onDismiss: () -> Unit,
    onSave: (Hotkey) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    val isNewHotkey = hotkey == null

    // Состояние клавиши
    var selectedKey by remember { mutableStateOf(hotkey?.key ?: Key.F1) }
    var ctrl by remember { mutableStateOf(hotkey?.ctrl ?: false) }
    var alt by remember { mutableStateOf(hotkey?.alt ?: false) }
    var shift by remember { mutableStateOf(hotkey?.shift ?: false) }
    var commands by remember { mutableStateOf(hotkey?.commands?.joinToString("\n") ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    // Формирование строки комбинации
    val keyComboString = remember(ctrl, alt, shift, selectedKey) {
        buildString {
            if (ctrl) append("Ctrl+")
            if (alt) append("Alt+")
            if (shift) append("Shift+")
            append(Hotkey.getKeyName(selectedKey))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(500.dp),
            backgroundColor = colorScheme.surface,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Заголовок
                Text(
                    text = if (isNewHotkey) "Новая горячая клавиша" else "Редактирование",
                    color = colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Поле захвата клавиш
                Text(
                    text = "Комбинация клавиш",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Поле для захвата клавиш
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            if (isCapturing) colorScheme.primary.copy(alpha = 0.2f)
                            else colorScheme.background
                        )
                        .border(
                            width = 2.dp,
                            color = if (isCapturing) colorScheme.success else colorScheme.divider
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            isCapturing = true
                            focusRequester.requestFocus()
                        }
                        .focusRequester(focusRequester)
                        .focusable()
                        .onFocusChanged { state ->
                            // Сбрасываем захват только когда теряем фокус
                            if (!state.isFocused) {
                                isCapturing = false
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            // onPreviewKeyEvent перехватывает события ДО того как диалог их обработает
                            if (event.type == KeyEventType.KeyDown && isCapturing) {
                                val key = event.key

                                // Игнорируем чистые модификаторы
                                if (key == Key.CtrlLeft || key == Key.CtrlRight ||
                                    key == Key.AltLeft || key == Key.AltRight ||
                                    key == Key.ShiftLeft || key == Key.ShiftRight ||
                                    key == Key.MetaLeft || key == Key.MetaRight
                                ) {
                                    return@onPreviewKeyEvent false
                                }

                                val isCtrl = event.isCtrlPressed
                                val isAlt = event.isAltPressed
                                val isShift = event.isShiftPressed

                                // Принимаем любую клавишу с модификатором или непечатную
                                val hasModifier = isCtrl || isAlt
                                if (Hotkey.isNonPrintable(key, hasModifier)) {
                                    selectedKey = key
                                    ctrl = isCtrl
                                    alt = isAlt
                                    shift = isShift
                                    errorMessage = null
                                } else {
                                    errorMessage = "Нужен Ctrl/Alt или F1-F12, стрелки, NumPad"
                                }
                                true // Поглощаем событие
                            } else {
                                false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = keyComboString,
                            color = colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        if (isCapturing) {
                            Text(
                                text = "Нажмите комбинацию...",
                                color = colorScheme.success,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "Кликните для изменения",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Команды
                Text(
                    text = "Команды (каждая с новой строки)",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                OutlinedTextField(
                    value = commands,
                    onValueChange = { commands = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = colorScheme.onSurface,
                        backgroundColor = colorScheme.background,
                        cursorColor = colorScheme.onSurface,
                        focusedBorderColor = colorScheme.success,
                        unfocusedBorderColor = colorScheme.divider
                    ),
                    placeholder = {
                        Text(
                            "score\ninfo",
                            color = colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )

                // Ошибка
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = colorScheme.error,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Кнопки
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Отмена", color = colorScheme.onSurface)
                    }

                    Button(
                        onClick = {
                            // Валидация
                            val commandList = commands.split("\n")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            if (commandList.isEmpty()) {
                                errorMessage = "Нужна хотя бы одна команда"
                                return@Button
                            }

                            // Генерируем ID или используем существующий
                            val id = hotkey?.id ?: "hk-${UUID.randomUUID().toString().take(8)}"

                            val newHotkey = Hotkey(
                                id = id,
                                key = selectedKey,
                                ctrl = ctrl,
                                alt = alt,
                                shift = shift,
                                commands = commandList,
                                enabled = hotkey?.enabled ?: true,
                                ignoreNumLock = hotkey?.ignoreNumLock ?: false
                            )

                            onSave(newHotkey)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.success
                        )
                    ) {
                        Text("Сохранить", color = Color.White)
                    }
                }
            }
        }
    }
}
