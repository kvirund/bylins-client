package com.bylins.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.contextcommands.ContextScope

/**
 * Выбор области действия правила (триггера, хоткея).
 *
 * Один компонент на все сущности: модель области общая, значит и UI должен
 * быть общим — иначе разъедутся формулировки и поведение.
 *
 * @param availableZones пары (id зоны, подпись вида «Название (53)») для подсказки
 * @param currentRoomId комната, где игрок сейчас — чтобы добавить её одним кликом
 */
@Composable
fun ScopeSelector(
    scope: ContextScope?,
    availableZones: List<Pair<String, String>> = emptyList(),
    currentRoomId: String? = null,
    onScopeChange: (ContextScope?) -> Unit
) {
    // Текстовый ввод удобнее списков: id зон и комнат приходят из карты и логов,
    // их обычно копируют, а не выбирают мышью
    var zonesText by remember(scope) {
        mutableStateOf((scope as? ContextScope.Zone)?.zones?.joinToString(", ") ?: "")
    }
    var roomsText by remember(scope) {
        mutableStateOf((scope as? ContextScope.Room)?.roomIds?.joinToString(", ") ?: "")
    }
    var propsText by remember(scope) {
        mutableStateOf((scope as? ContextScope.Room)?.roomPropertyKeys?.joinToString(", ") ?: "")
    }

    fun parse(text: String): Set<String> =
        text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Область действия",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScopeChip("Везде", scope == null || scope is ContextScope.World) {
                onScopeChange(null)
            }
            ScopeChip("Зоны", scope is ContextScope.Zone) {
                onScopeChange(ContextScope.Zone(parse(zonesText)))
            }
            ScopeChip("Комнаты", scope is ContextScope.Room) {
                onScopeChange(ContextScope.Room(parse(roomsText), parse(propsText)))
            }
        }

        when (scope) {
            is ContextScope.Zone -> {
                OutlinedTextField(
                    value = zonesText,
                    onValueChange = {
                        zonesText = it
                        onScopeChange(ContextScope.Zone(parse(it)))
                    },
                    label = { Text("ID зон через запятую", fontSize = 11.sp) },
                    placeholder = { Text("759, 50", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
                if (availableZones.isNotEmpty()) {
                    // Подсказка: клик добавляет зону в список
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableZones.take(12).forEach { (id, label) ->
                            ScopeChip(label, selected = false) {
                                val next = (parse(zonesText) + id).joinToString(", ")
                                zonesText = next
                                onScopeChange(ContextScope.Zone(parse(next)))
                            }
                        }
                    }
                }
            }

            is ContextScope.Room -> {
                OutlinedTextField(
                    value = roomsText,
                    onValueChange = {
                        roomsText = it
                        onScopeChange(ContextScope.Room(parse(it), parse(propsText)))
                    },
                    label = { Text("ID комнат через запятую", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = propsText,
                    onValueChange = {
                        propsText = it
                        onScopeChange(ContextScope.Room(parse(roomsText), parse(it)))
                    },
                    label = { Text("или свойства комнаты через запятую", fontSize = 11.sp) },
                    placeholder = { Text("shop, safe", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
                if (currentRoomId != null) {
                    ScopeChip("+ текущая комната ($currentRoomId)", selected = false) {
                        val next = (parse(roomsText) + currentRoomId).joinToString(", ")
                        roomsText = next
                        onScopeChange(ContextScope.Room(parse(next), parse(propsText)))
                    }
                }
            }

            else -> {
                Text(
                    text = "Работает везде, независимо от местоположения",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ScopeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (selected) Color(0xFF3A6EA5) else Color(0xFF3A3A3A)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp),
        modifier = Modifier.height(26.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}
