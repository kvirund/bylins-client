package com.bylins.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bylins.client.contextcommands.ContextScope
import com.bylins.client.ui.theme.LocalAppColorScheme

/**
 * Выбор области действия правила — общий для триггеров, хоткеев и контекстных
 * команд.
 *
 * Раньше в каждом из трёх мест был свой вариант (радиокнопки, чипы, выпадающий
 * список) при одинаковом смысле. Вид взят у панели контекстных команд, как
 * самый обжитой: радиокнопки типа области, выбранное — плашками, зоны из
 * списка с поиском.
 *
 * @param availableZones пары (id зоны, подпись «Название (53)»)
 * @param currentRoomId комната, где игрок сейчас — чтобы добавить её одним кликом
 */
@Composable
fun ScopeSelector(
    scope: ContextScope?,
    availableZones: List<Pair<String, String>> = emptyList(),
    currentRoomId: String? = null,
    onScopeChange: (ContextScope?) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    val selectedZones = (scope as? ContextScope.Zone)?.zones ?: emptySet()
    val selectedRooms = (scope as? ContextScope.Room)?.roomIds ?: emptySet()
    val selectedProps = (scope as? ContextScope.Room)?.roomPropertyKeys ?: emptySet()
    var showZonePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Область действия",
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScopeRadio("Везде", scope == null || scope is ContextScope.World) { onScopeChange(null) }
            ScopeRadio("Зона", scope is ContextScope.Zone) { onScopeChange(ContextScope.Zone(selectedZones)) }
            ScopeRadio("Комната", scope is ContextScope.Room) {
                onScopeChange(ContextScope.Room(selectedRooms, selectedProps))
            }
        }

        when (scope) {
            is ContextScope.Zone -> {
                ScopeChipRow(
                    items = selectedZones.map { id -> id to zoneLabelOf(id, availableZones) },
                    emptyText = "Зоны не выбраны",
                    onRemove = { id -> onScopeChange(ContextScope.Zone(selectedZones - id)) }
                )
                OutlinedButton(
                    onClick = { showZonePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать зону...", color = colorScheme.onSurface, fontSize = 12.sp)
                }
            }

            is ContextScope.Room -> {
                ScopeChipRow(
                    items = selectedRooms.map { it to it },
                    emptyText = "Комнаты не выбраны",
                    onRemove = { id -> onScopeChange(ContextScope.Room(selectedRooms - id, selectedProps)) }
                )
                if (currentRoomId != null && currentRoomId !in selectedRooms) {
                    OutlinedButton(
                        onClick = { onScopeChange(ContextScope.Room(selectedRooms + currentRoomId, selectedProps)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ текущая комната ($currentRoomId)", color = colorScheme.onSurface, fontSize = 12.sp)
                    }
                }
                // Комнат тысячи, списком их не выбрать — зато можно указать
                // свойство, и правило заработает во всех подходящих
                ScopeTextField(
                    value = selectedProps.joinToString(", "),
                    label = "Свойства комнаты через запятую (shop, safe)",
                    onValueChange = { text ->
                        val props = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        onScopeChange(ContextScope.Room(selectedRooms, props))
                    }
                )
            }

            else -> Text(
                text = "Работает везде, независимо от местоположения",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    if (showZonePicker) {
        ZonePickerDialog(
            availableZones = availableZones,
            selected = selectedZones,
            onDismiss = { showZonePicker = false },
            onPick = { id -> onScopeChange(ContextScope.Zone(selectedZones + id)) }
        )
    }
}

/**
 * Плашка области действия — того же вида, что у контекстных команд, чтобы в
 * списках триггеров и хоткеев сразу было видно: правило не глобальное.
 */
@Composable
fun ScopeBadges(scope: ContextScope?) {
    if (scope == null || scope is ContextScope.World) return
    val colorScheme = LocalAppColorScheme.current

    val (text, color) = when (scope) {
        is ContextScope.Zone -> {
            val zones = scope.zones
            (if (zones.size == 1) "Зона ${zones.first()}" else "Зоны: ${zones.size}") to colorScheme.warning
        }
        is ContextScope.Room -> {
            val parts = buildList {
                if (scope.roomIds.isNotEmpty()) add("комнат: ${scope.roomIds.size}")
                if (scope.roomPropertyKeys.isNotEmpty()) add(scope.roomPropertyKeys.joinToString(","))
            }
            ("Комната" + if (parts.isEmpty()) "" else " (${parts.joinToString("; ")})") to colorScheme.secondary
        }
        else -> return
    }

    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun zoneLabelOf(id: String, available: List<Pair<String, String>>): String =
    available.find { it.first == id }?.second ?: "Зона $id"

@Composable
private fun ScopeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50))
        )
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

/** Выбранные значения — плашками с крестиком, как теги в панели контекста. */
@Composable
private fun ScopeChipRow(
    items: List<Pair<String, String>>,
    emptyText: String,
    onRemove: (String) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    if (items.isEmpty()) {
        Text(emptyText, color = colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { (id, label) ->
            Surface(color = colorScheme.secondary.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(label, color = colorScheme.secondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(
                        text = "✕",
                        color = colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onRemove(id) }.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeTextField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.White,
            backgroundColor = Color(0xFF1E1E1E),
            cursorColor = Color.White,
            focusedBorderColor = Color(0xFF4CAF50),
            unfocusedBorderColor = Color.Gray
        ),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Список зон с поиском — как в панели контекстных команд. */
@Composable
private fun ZonePickerDialog(
    availableZones: List<Pair<String, String>>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var manualId by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(420.dp).heightIn(max = 520.dp),
            backgroundColor = Color(0xFF2D2D2D)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выбор зоны", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)

                ScopeTextField(value = query, label = "Поиск зоны", onValueChange = { query = it })

                val filtered = remember(query, availableZones) {
                    val q = query.lowercase()
                    if (q.isBlank()) availableZones
                    else availableZones.filter { (id, name) ->
                        id.lowercase().contains(q) || name.lowercase().contains(q)
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    items(filtered.size) { index ->
                        val (id, label) = filtered[index]
                        val already = id in selected
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !already) { onPick(id); onDismiss() }
                                .padding(vertical = 2.dp),
                            color = if (already) Color(0xFF3D3D3D) else Color.Transparent
                        ) {
                            Text(
                                text = label,
                                color = if (already) Color.Gray else Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                Divider(color = Color(0xFF444444))

                // Зона может быть известна по номеру, но отсутствовать в карте
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ScopeTextField(
                            value = manualId,
                            label = "id вручную",
                            onValueChange = { manualId = it.filter { c -> c.isDigit() } }
                        )
                    }
                    Button(
                        onClick = { if (manualId.isNotBlank()) { onPick(manualId.trim()); onDismiss() } },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
                    ) {
                        Text("Добавить", color = Color.White, fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Закрыть", color = Color.White) }
                }
            }
        }
    }
}
