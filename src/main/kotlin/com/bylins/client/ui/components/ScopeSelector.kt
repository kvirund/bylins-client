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
import androidx.compose.foundation.BorderStroke
import com.bylins.client.contextcommands.ContextScope
import com.bylins.client.ui.theme.LocalAppColorScheme

/** Сколько строк показываем в пикере: комнат тысячи, весь список бесполезен. */
private const val MAX_PICKER_ITEMS = 100

/**
 * Как подписывать зоны и комнаты: «Название (53)» и «Название [4341]».
 *
 * Через CompositionLocal, потому что подписи нужны и в диалогах, и в плашках
 * внутри строк списков — тащить их параметром через каждый элемент значило бы
 * менять сигнатуры половины панелей ради двух функций.
 */
class ScopeLabels(
    val zone: (String) -> String = { "Зона $it" },
    val room: (String) -> String = { "Комната $it" }
)

val LocalScopeLabels = compositionLocalOf { ScopeLabels() }

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
 * @param searchRooms поиск комнат по запросу; комнат тысячи, готовым списком их
 *   не передать — пикер спрашивает по мере ввода
 */
@Composable
fun ScopeSelector(
    scope: ContextScope?,
    availableZones: List<Pair<String, String>> = emptyList(),
    currentRoomId: String? = null,
    searchRooms: ((String) -> List<Pair<String, String>>)? = null,
    onScopeChange: (ContextScope?) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    val labels = LocalScopeLabels.current
    val selectedZones = (scope as? ContextScope.Zone)?.zones ?: emptySet()
    val selectedRooms = (scope as? ContextScope.Room)?.roomIds ?: emptySet()
    val selectedProps = (scope as? ContextScope.Room)?.roomPropertyKeys ?: emptySet()
    var showZonePicker by remember { mutableStateOf(false) }
    var showRoomPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Область действия",
            color = colorScheme.onSurfaceVariant,
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
                    items = selectedZones.map { id -> id to labels.zone(id) },
                    emptyText = "Зоны не выбраны",
                    onRemove = { id -> onScopeChange(ContextScope.Zone(selectedZones - id)) }
                )
                ScopeButton("Выбрать зону…") { showZonePicker = true }
            }

            is ContextScope.Room -> {
                ScopeChipRow(
                    items = selectedRooms.map { id -> id to labels.room(id) },
                    emptyText = "Комнаты не выбраны",
                    onRemove = { id -> onScopeChange(ContextScope.Room(selectedRooms - id, selectedProps)) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) {
                        ScopeButton("Выбрать комнату…") { showRoomPicker = true }
                    }
                    if (currentRoomId != null && currentRoomId !in selectedRooms) {
                        Box(Modifier.weight(1f)) {
                            ScopeButton("+ текущая: ${labels.room(currentRoomId)}") {
                                onScopeChange(ContextScope.Room(selectedRooms + currentRoomId, selectedProps))
                            }
                        }
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
        ScopePickerDialog(
            title = "Выбор зоны",
            searchLabel = "Поиск зоны по названию или номеру",
            selected = selectedZones,
            search = { query ->
                val q = query.lowercase()
                if (q.isBlank()) availableZones
                else availableZones.filter { (id, name) -> id.lowercase().contains(q) || name.lowercase().contains(q) }
            },
            onDismiss = { showZonePicker = false },
            onPick = { id -> onScopeChange(ContextScope.Zone(selectedZones + id)) }
        )
    }

    if (showRoomPicker && searchRooms != null) {
        ScopePickerDialog(
            title = "Выбор комнаты",
            searchLabel = "Поиск комнаты по названию или номеру",
            selected = selectedRooms,
            search = searchRooms,
            onDismiss = { showRoomPicker = false },
            onPick = { id -> onScopeChange(ContextScope.Room(selectedRooms + id, selectedProps)) }
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
    val labels = LocalScopeLabels.current

    val (text, color) = when (scope) {
        is ContextScope.Zone -> {
            val zones = scope.zones
            // Одна зона — пишем её целиком: по голому номеру в списке правил
            // не понять, о какой зоне речь
            val text = when {
                zones.isEmpty() -> "Зона не выбрана"
                zones.size == 1 -> labels.zone(zones.first())
                else -> "Зоны: ${zones.size}"
            }
            text to colorScheme.warning
        }
        is ContextScope.Room -> {
            val rooms = scope.roomIds
            val parts = buildList {
                when {
                    rooms.size == 1 -> add(labels.room(rooms.first()))
                    rooms.size > 1 -> add("комнат: ${rooms.size}")
                }
                if (scope.roomPropertyKeys.isNotEmpty()) add(scope.roomPropertyKeys.joinToString(","))
            }
            (if (parts.isEmpty()) "Комната" else parts.joinToString("; ")) to colorScheme.secondary
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

@Composable
private fun ScopeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    val colorScheme = LocalAppColorScheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = colorScheme.success,
                unselectedColor = colorScheme.onSurfaceVariant
            )
        )
        Text(label, color = colorScheme.onSurface, fontSize = 12.sp)
    }
}

/** Кнопка выбора — одна на все области, чтобы вид не расходился. */
@Composable
private fun ScopeButton(label: String, onClick: () -> Unit) {
    val colorScheme = LocalAppColorScheme.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, colorScheme.border),
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurface
        )
    ) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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
    val colorScheme = LocalAppColorScheme.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp, color = colorScheme.onSurfaceVariant) },
        singleLine = true,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = colorScheme.onSurface,
            backgroundColor = colorScheme.background,
            cursorColor = colorScheme.onSurface,
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.border
        ),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Список с поиском — один и тот же для зон и для комнат, чтобы выбор области
 * выглядел одинаково независимо от того, что выбирают.
 *
 * @param search выдаёт пары (id, подпись) по запросу; для зон это фильтр по
 *   готовому списку, для комнат — поиск по карте (их тысячи, списком не отдать)
 */
@Composable
private fun ScopePickerDialog(
    title: String,
    searchLabel: String,
    selected: Set<String>,
    search: (String) -> List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    var query by remember { mutableStateOf("") }
    var manualId by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(420.dp).heightIn(max = 520.dp),
            backgroundColor = colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = colorScheme.onSurface, fontSize = 14.sp, fontFamily = FontFamily.Monospace)

                ScopeTextField(value = query, label = searchLabel, onValueChange = { query = it })

                // Показываем ограниченное число: по пустому запросу комнат
                // могут быть тысячи, и список превращается в бесполезную кашу
                val found = remember(query) { search(query) }
                val shown = found.take(MAX_PICKER_ITEMS)

                if (found.isEmpty()) {
                    Text(
                        "Ничего не найдено",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    items(shown.size) { index ->
                        val (id, label) = shown[index]
                        val already = id in selected
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !already) { onPick(id); onDismiss() }
                                .padding(vertical = 2.dp),
                            color = if (already) colorScheme.surfaceVariant else Color.Transparent
                        ) {
                            Text(
                                text = label,
                                color = if (already) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                if (found.size > shown.size) {
                    Text(
                        "Показаны первые ${shown.size} из ${found.size} — уточните запрос",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Divider(color = colorScheme.divider)

                // Зона или комната может быть известна по номеру, но отсутствовать в карте
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
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Добавить", fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Закрыть", color = colorScheme.onSurface) }
                }
            }
        }
    }
}
