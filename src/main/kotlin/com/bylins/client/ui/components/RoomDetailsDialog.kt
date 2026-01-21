package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bylins.client.mapper.Direction
import com.bylins.client.mapper.Exit
import com.bylins.client.mapper.Room
import com.bylins.client.ui.theme.LocalAppColorScheme

@Composable
fun RoomDetailsDialog(
    room: Room,
    allRooms: Map<String, Room>,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String, terrain: String?, tags: Set<String>, zone: String, exits: Map<Direction, Exit>, visited: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(room.name) }
    var note by remember { mutableStateOf(room.notes) }
    // Нормализуем terrain в uppercase для единообразия
    var selectedTerrain by remember { mutableStateOf(room.terrain?.uppercase()) }
    var tags by remember { mutableStateOf(room.tags) }
    var newTag by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(room.zone ?: "") }
    var exits by remember { mutableStateOf(room.exits.toMutableMap()) }
    var visited by remember { mutableStateOf(room.visited) }

    // Для добавления нового выхода
    var showAddExitDialog by remember { mutableStateOf(false) }
    // Для редактирования существующего выхода
    var editingExit by remember { mutableStateOf<Direction?>(null) }

    // Terrain types with Russian names (все коды в uppercase)
    val terrainTypes = listOf(
        null to "Не указан",
        "INSIDE" to "Помещение",
        "INDOOR" to "Помещение (indoor)",
        "CITY" to "Город",
        "FIELD" to "Поле",
        "FOREST" to "Лес",
        "HILLS" to "Холмы",
        "MOUNTAIN" to "Горы",
        "WATER" to "Вода",
        "WATER_SWIM" to "Вода (плыть)",
        "WATER_NOSWIM" to "Вода (глубокая)",
        "WATER_SHALLOW" to "Мелководье",
        "UNDERWATER" to "Под водой",
        "FLYING" to "Воздух (летать)",
        "AIR" to "Воздух",
        "ROAD" to "Дорога",
        "DUNGEON" to "Подземелье",
        "JUNGLE" to "Джунгли",
        "SWAMP" to "Болото",
        "BEACH" to "Пляж",
        "DESERT" to "Пустыня",
        "ICE" to "Лёд",
        "CAVE" to "Пещера",
        "TUNDRA" to "Тундра",
        "LAVA" to "Лава",
        "DARK" to "Тьма",
        "DEATH" to "Смерть",
        "SECRET" to "Секрет"
    )

    val colorScheme = LocalAppColorScheme.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(550.dp)
                .heightIn(max = 700.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Заголовок
                Text(
                    text = "Редактирование комнаты",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Прокручиваемый контент
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                // VNUM (ID комнаты - только для чтения)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VNUM:",
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        text = room.id,
                        color = colorScheme.secondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Флаг "Исследована" (visited) - комнаты с ? на карте
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = visited,
                        onCheckedChange = { visited = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colorScheme.success,
                            uncheckedColor = colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "Исследована",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "(снимите галочку для отметки ?)",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Название комнаты (редактируемое)
                Column {
                    Text(
                        text = "Название:",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.success,
                            unfocusedBorderColor = colorScheme.onSurfaceVariant
                        ),
                        singleLine = true
                    )
                }

                Divider(color = colorScheme.onSurfaceVariant)

                // Выходы
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Выходы:",
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = { showAddExitDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.success
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("+ Добавить", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (exits.isEmpty()) {
                        Text(
                            text = "Нет выходов",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            exits.forEach { (direction, exit) ->
                                val targetRoom = allRooms[exit.targetRoomId]
                                val targetName = when {
                                    exit.targetRoomId.isEmpty() -> "(неизведан)"
                                    targetRoom != null -> targetRoom.name
                                    else -> "(#${exit.targetRoomId})"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = direction.russianName,
                                                color = colorScheme.success,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (exit.door != null) {
                                                Text(
                                                    text = "[дверь: ${exit.door}]",
                                                    color = colorScheme.primaryVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        Text(
                                            text = "-> $targetName",
                                            color = colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (exit.targetRoomId.isNotEmpty()) {
                                            Text(
                                                text = "#${exit.targetRoomId}",
                                                color = colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Кнопка редактирования
                                        TextButton(
                                            onClick = { editingExit = direction },
                                            contentPadding = PaddingValues(4.dp)
                                        ) {
                                            Text("Изм.", color = colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                        }
                                        // Кнопка удаления
                                        TextButton(
                                            onClick = {
                                                exits = exits.toMutableMap().apply { remove(direction) }
                                            },
                                            contentPadding = PaddingValues(4.dp)
                                        ) {
                                            Text("X", color = colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(color = colorScheme.onSurfaceVariant)

                // Тип поверхности (terrain)
                Column {
                    Text(
                        text = "Тип поверхности:",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    var terrainExpanded by remember { mutableStateOf(false) }

                    val currentTerrainName = terrainTypes.find { it.first == selectedTerrain }?.second ?: "Не указан"
                    val currentTerrainColor = TerrainColors.getColor(selectedTerrain) ?: Color.Gray

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(currentTerrainColor, MaterialTheme.shapes.small)
                        )

                        OutlinedButton(
                            onClick = { terrainExpanded = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.onSurface
                            )
                        ) {
                            Text(currentTerrainName)
                        }

                        DropdownMenu(
                            expanded = terrainExpanded,
                            onDismissRequest = { terrainExpanded = false }
                        ) {
                            terrainTypes.forEach { (terrainCode, terrainName) ->
                                val terrainColor = TerrainColors.getColor(terrainCode) ?: Color.Gray
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .background(terrainColor, MaterialTheme.shapes.small)
                                            )
                                            Text(terrainName)
                                        }
                                    },
                                    onClick = {
                                        selectedTerrain = terrainCode
                                        terrainExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Заметка
                Column {
                    Text(
                        text = "Заметка:",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        placeholder = { Text("Добавить заметку...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.success,
                            unfocusedBorderColor = colorScheme.onSurfaceVariant,
                            focusedPlaceholderColor = colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = colorScheme.onSurfaceVariant
                        )
                    )
                }

                // Теги
                Column {
                    Text(
                        text = "Теги:",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.forEach { tag ->
                                Button(
                                    onClick = { tags = tags - tag },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.surfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(tag, color = colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                    Text(" X", color = colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Новый тег...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface,
                                focusedBorderColor = colorScheme.success,
                                unfocusedBorderColor = colorScheme.onSurfaceVariant,
                                focusedPlaceholderColor = colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = colorScheme.onSurfaceVariant
                            ),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newTag.isNotBlank() && newTag !in tags) {
                                    tags = tags + newTag.trim()
                                    newTag = ""
                                }
                            },
                            enabled = newTag.isNotBlank() && newTag !in tags,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.success
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("+")
                        }
                    }
                }

                // Зона
                Column {
                    Text(
                        text = "Зона:",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedTextField(
                        value = zone,
                        onValueChange = { zone = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Название зоны...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.success,
                            unfocusedBorderColor = colorScheme.onSurfaceVariant,
                            focusedPlaceholderColor = colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = colorScheme.onSurfaceVariant
                        ),
                        singleLine = true
                    )
                }

                } // Конец прокручиваемого контента

                // Разделитель и кнопки (sticky)
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = colorScheme.onSurface)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onSave(name, note, selectedTerrain, tags, zone, exits, visited)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.success
                        )
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }

    // Диалог добавления выхода
    if (showAddExitDialog) {
        ExitEditDialog(
            direction = null,
            exit = null,
            existingDirections = exits.keys,
            allRooms = allRooms,
            onDismiss = { showAddExitDialog = false },
            onSave = { dir, ex ->
                exits = exits.toMutableMap().apply { put(dir, ex) }
                showAddExitDialog = false
            }
        )
    }

    // Диалог редактирования выхода
    editingExit?.let { dir ->
        ExitEditDialog(
            direction = dir,
            exit = exits[dir],
            existingDirections = exits.keys - dir,
            allRooms = allRooms,
            onDismiss = { editingExit = null },
            onSave = { newDir, ex ->
                exits = exits.toMutableMap().apply {
                    remove(dir)
                    put(newDir, ex)
                }
                editingExit = null
            }
        )
    }
}

@Composable
private fun ExitEditDialog(
    direction: Direction?,
    exit: Exit?,
    existingDirections: Set<Direction>,
    allRooms: Map<String, Room>,
    onDismiss: () -> Unit,
    onSave: (Direction, Exit) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    var selectedDirection by remember { mutableStateOf(direction ?: Direction.NORTH) }
    var targetRoomId by remember { mutableStateOf(exit?.targetRoomId ?: "") }
    var door by remember { mutableStateOf(exit?.door ?: "") }
    var searchQuery by remember { mutableStateOf("") }

    var directionExpanded by remember { mutableStateOf(false) }

    // Доступные направления (которых ещё нет)
    val availableDirections = if (direction != null) {
        Direction.entries
    } else {
        Direction.entries.filter { it !in existingDirections }
    }

    // Фильтруем комнаты по поисковому запросу
    val filteredRooms = remember(searchQuery, allRooms) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            allRooms.values.filter { room ->
                room.id.contains(searchQuery, ignoreCase = true) ||
                room.name.contains(searchQuery, ignoreCase = true)
            }.take(10)
        }
    }

    // Выбранная комната для отображения
    val selectedRoom = allRooms[targetRoomId]

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(450.dp).heightIn(max = 500.dp).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (direction != null) "Редактирование выхода" else "Добавление выхода",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )

                // Направление
                Column {
                    Text("Направление:", color = colorScheme.onSurface)
                    OutlinedButton(
                        onClick = { directionExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
                    ) {
                        Text(selectedDirection.russianName)
                    }
                    DropdownMenu(
                        expanded = directionExpanded,
                        onDismissRequest = { directionExpanded = false }
                    ) {
                        availableDirections.forEach { dir ->
                            DropdownMenuItem(
                                text = { Text("${dir.russianName} (${dir.shortName})") },
                                onClick = {
                                    selectedDirection = dir
                                    directionExpanded = false
                                }
                            )
                        }
                    }
                }

                // Целевая комната - поиск
                Column {
                    Text("Целевая комната:", color = colorScheme.onSurface)

                    // Отображаем выбранную комнату
                    if (targetRoomId.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedRoom?.name ?: "(комната не найдена)",
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "#$targetRoomId",
                                        color = colorScheme.secondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(
                                    onClick = { targetRoomId = "" },
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text("X", color = colorScheme.error)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "(неизведанный выход)",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Поиск комнаты
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск по ID или названию...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.success,
                            unfocusedBorderColor = colorScheme.onSurfaceVariant,
                            focusedPlaceholderColor = colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = colorScheme.onSurfaceVariant
                        ),
                        singleLine = true
                    )

                    // Результаты поиска
                    if (filteredRooms.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            filteredRooms.forEach { room ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            targetRoomId = room.id
                                            searchQuery = ""
                                        },
                                    color = colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "#${room.id}",
                                            color = colorScheme.secondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = room.name,
                                            color = colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Дверь
                Column {
                    Text("Название двери (если есть):", color = colorScheme.onSurface)
                    OutlinedTextField(
                        value = door,
                        onValueChange = { door = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Пусто = нет двери") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.success,
                            unfocusedBorderColor = colorScheme.onSurfaceVariant,
                            focusedPlaceholderColor = colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = colorScheme.onSurfaceVariant
                        ),
                        singleLine = true
                    )
                }

                Divider(color = colorScheme.onSurfaceVariant)

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                selectedDirection,
                                Exit(
                                    targetRoomId = targetRoomId.trim(),
                                    door = door.ifBlank { null }
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.success)
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
