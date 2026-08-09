package com.bylins.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.contextcommands.ContextScope
import com.bylins.client.ui.theme.LocalAppColorScheme

/**
 * Выбор области действия правила (триггера, хоткея).
 *
 * Один компонент на все сущности: модель области общая, значит и UI должен
 * быть общим — иначе разъедутся формулировки и поведение.
 *
 * @param availableZones пары (id зоны, подпись вида «Название (53)»)
 * @param currentRoomId комната, где игрок сейчас — чтобы добавить её одним кликом
 */
@Composable
fun ScopeSelector(
    scope: ContextScope?,
    availableZones: List<Pair<String, String>> = emptyList(),
    currentRoomId: String? = null,
    onScopeChange: (ContextScope?) -> Unit
) {
    val selectedZones = (scope as? ContextScope.Zone)?.zones ?: emptySet()
    val selectedRooms = (scope as? ContextScope.Room)?.roomIds ?: emptySet()
    val selectedProps = (scope as? ContextScope.Room)?.roomPropertyKeys ?: emptySet()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Область действия",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScopeChip("Везде", scope == null || scope is ContextScope.World) { onScopeChange(null) }
            ScopeChip("Зоны", scope is ContextScope.Zone) {
                onScopeChange(ContextScope.Zone(selectedZones))
            }
            ScopeChip("Комнаты", scope is ContextScope.Room) {
                onScopeChange(ContextScope.Room(selectedRooms, selectedProps))
            }
        }

        when (scope) {
            is ContextScope.Zone -> ZonePicker(
                available = availableZones,
                selected = selectedZones,
                onChange = { onScopeChange(ContextScope.Zone(it)) }
            )

            is ContextScope.Room -> RoomPicker(
                selectedRooms = selectedRooms,
                selectedProps = selectedProps,
                currentRoomId = currentRoomId,
                onChange = { rooms, props -> onScopeChange(ContextScope.Room(rooms, props)) }
            )

            else -> Text(
                text = "Работает везде, независимо от местоположения",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Цвета полей ввода из темы клиента: Material по умолчанию красит фокус
 * фиолетовым, а рамку — почти в цвет фона, из-за чего поля не видно.
 */
@Composable
private fun scopeFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = LocalAppColorScheme.current.onSurface,
    cursorColor = LocalAppColorScheme.current.onSurface,
    focusedBorderColor = LocalAppColorScheme.current.primary,
    unfocusedBorderColor = LocalAppColorScheme.current.onSurfaceVariant.copy(alpha = 0.6f),
    focusedLabelColor = LocalAppColorScheme.current.primary,
    unfocusedLabelColor = LocalAppColorScheme.current.onSurfaceVariant
)

/**
 * Выбор зон списком с галочками: id зон запоминать неудобно, а названия
 * клиент уже знает из карты.
 */
@Composable
private fun ZonePicker(
    available: List<Pair<String, String>>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var manualId by remember { mutableStateOf("") }

    val summary = when {
        selected.isEmpty() -> "Зоны не выбраны"
        else -> selected.joinToString(", ") { id ->
            available.find { it.first == id }?.second ?: "Зона $id"
        }
    }

    Box {
        val colors = LocalAppColorScheme.current
        OutlinedButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface),
            border = BorderStroke(1.dp, colors.onSurfaceVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = summary,
                color = if (selected.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.onSurface)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            if (available.isEmpty()) {
                DropdownMenuItem(onClick = { expanded = false }) {
                    Text("Зон на карте пока нет", fontSize = 11.sp)
                }
            }
            available.forEach { (id, label) ->
                DropdownMenuItem(onClick = {
                    // Меню не закрываем: обычно выбирают несколько зон подряд
                    onChange(if (id in selected) selected - id else selected + id)
                }) {
                    Checkbox(
                        checked = id in selected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontSize = 11.sp)
                }
            }
        }
    }

    // Зона может быть известна по номеру, но отсутствовать в карте — тогда её
    // нет в списке, и остаётся ввести id руками
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = manualId,
            onValueChange = { manualId = it.filter { c -> c.isDigit() } },
            label = { Text("id вручную", fontSize = 10.sp) },
            singleLine = true,
            colors = scopeFieldColors(),
            modifier = Modifier.weight(1f),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        )
        ScopeChip("Добавить", selected = false) {
            if (manualId.isNotBlank()) {
                onChange(selected + manualId.trim())
                manualId = ""
            }
        }
    }
}

/**
 * Комнат тысячи, поэтому списком их не выбрать: берём текущую одним кликом
 * либо задаём свойство (например, shop) — тогда правило работает во всех
 * подходящих комнатах.
 */
@Composable
private fun RoomPicker(
    selectedRooms: Set<String>,
    selectedProps: Set<String>,
    currentRoomId: String?,
    onChange: (Set<String>, Set<String>) -> Unit
) {
    var roomsText by remember(selectedRooms) { mutableStateOf(selectedRooms.joinToString(", ")) }
    var propsText by remember(selectedProps) { mutableStateOf(selectedProps.joinToString(", ")) }

    fun parse(text: String): Set<String> =
        text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    OutlinedTextField(
        value = roomsText,
        onValueChange = {
            roomsText = it
            onChange(parse(it), parse(propsText))
        },
        label = { Text("ID комнат через запятую", fontSize = 11.sp) },
        singleLine = true,
        colors = scopeFieldColors(),
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    )

    if (currentRoomId != null) {
        ScopeChip("+ текущая комната ($currentRoomId)", selected = false) {
            val next = (parse(roomsText) + currentRoomId).joinToString(", ")
            roomsText = next
            onChange(parse(next), parse(propsText))
        }
    }

    OutlinedTextField(
        value = propsText,
        onValueChange = {
            propsText = it
            onChange(parse(roomsText), parse(it))
        },
        label = { Text("или свойства комнаты (shop, safe)", fontSize = 11.sp) },
        singleLine = true,
        colors = scopeFieldColors(),
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    )
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
