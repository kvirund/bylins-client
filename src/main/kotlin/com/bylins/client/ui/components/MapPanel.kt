package com.bylins.client.ui.components

import mu.KotlinLogging
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState
import com.bylins.client.mapper.Room
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@OptIn(ExperimentalComposeUiApi::class)
private val logger = KotlinLogging.logger("MapPanel")
@Composable
fun MapPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val rooms by clientState.mapRooms.collectAsState()
    val currentRoomId by clientState.currentRoomId.collectAsState()
    val mapEnabled by clientState.mapEnabled.collectAsState()

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var zoom by remember { mutableStateOf(1f) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var showRoomDialog by remember { mutableStateOf(false) }
    var showDatabaseDialog by remember { mutableStateOf(false) }
    var hoveredRoom by remember { mutableStateOf<Room?>(null) }
    var mousePosition by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }

    // Центр обзора карты (может отличаться от текущей комнаты)
    var viewCenterRoomId by remember { mutableStateOf<String?>(null) }
    var followPlayer by remember { mutableStateOf(true) }

    // Автоследование за игроком
    LaunchedEffect(currentRoomId, followPlayer) {
        if (followPlayer && currentRoomId != null) {
            viewCenterRoomId = currentRoomId
            offsetX = 0f
            offsetY = 0f
        }
    }

    // Используем viewCenterRoomId или currentRoomId
    val effectiveCenterRoomId = viewCenterRoomId ?: currentRoomId

    // Параметры отрисовки (масштабируемые)
    val baseRoomSize = 32f
    val baseRoomSpacing = 50f
    val roomSize = baseRoomSize * zoom
    val roomSpacing = baseRoomSpacing * zoom

    // Вычисляем позиции комнат с учётом смещения (вынесено из Column для доступа в статистике)
    val displayRooms = remember(rooms, effectiveCenterRoomId, canvasSize, offsetX, offsetY, zoom) {
        if (canvasSize.first > 0 && canvasSize.second > 0 && effectiveCenterRoomId != null) {
            calculateRoomPositions(
                rooms = rooms,
                startRoomId = effectiveCenterRoomId,
                centerX = canvasSize.first / 2 + offsetX,
                centerY = canvasSize.second / 2 + offsetY,
                roomSize = roomSize,
                roomSpacing = roomSpacing,
                canvasWidth = canvasSize.first,
                canvasHeight = canvasSize.second
            )
        } else {
            emptyMap()
        }
    }

    Column(modifier = modifier) {
        // Панель управления
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B2B2B))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Карта", color = Color.White)

                Switch(
                    checked = mapEnabled,
                    onCheckedChange = { clientState.setMapEnabled(it) }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Следовать за игроком
                Text("Следовать", color = if (followPlayer) Color.White else Color(0xFF888888))
                Switch(
                    checked = followPlayer,
                    onCheckedChange = { followPlayer = it }
                )

                // Центрировать на текущей комнате
                if (!followPlayer) {
                    Button(
                        onClick = {
                            viewCenterRoomId = currentRoomId
                            offsetX = 0f
                            offsetY = 0f
                        },
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Text("К игроку")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { zoom = (zoom * 1.2f).coerceAtMost(3f) },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+")
                }

                Text("${(zoom * 100).toInt()}%", color = Color.White)

                Button(
                    onClick = { zoom = (zoom / 1.2f).coerceAtLeast(0.3f) },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("-")
                }

                Button(
                    onClick = {
                        offsetX = 0f
                        offsetY = 0f
                        zoom = 1f
                    },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("Сброс")
                }

                Button(
                    onClick = { clientState.clearMap() },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("Очистить")
                }

                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
                        fileChooser.selectedFile = File("map.json")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val exportedRooms = clientState.exportMap()
                                val json = Json {
                                    prettyPrint = true
                                    ignoreUnknownKeys = true
                                }
                                val jsonString = json.encodeToString(exportedRooms)
                                fileChooser.selectedFile.writeText(jsonString)
                                logger.info { "Map exported to ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Export error: ${e.message}" }
                            }
                        }
                    },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("Экспорт")
                }

                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val jsonString = fileChooser.selectedFile.readText()
                                val json = Json {
                                    prettyPrint = true
                                    ignoreUnknownKeys = true
                                }
                                val importedRooms = json.decodeFromString<Map<String, Room>>(jsonString)
                                clientState.importMap(importedRooms)
                                logger.info { "Map imported from ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Import error: ${e.message}" }
                            }
                        }
                    },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("Импорт")
                }

                Button(
                    onClick = { showDatabaseDialog = true },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("База данных")
                }
            }
        }

        // Основная область карты
        Box(
            modifier = Modifier.fillMaxSize().weight(1f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .pointerInput(displayRooms, roomSize) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                // Одинарный клик - открыть диалог редактирования
                                val room = findRoomAtPosition(displayRooms, tapOffset.x, tapOffset.y, roomSize)
                                if (room != null) {
                                    selectedRoom = room
                                    showRoomDialog = true
                                }
                            },
                            onDoubleTap = { tapOffset ->
                                // Двойной клик - центрировать на комнате
                                val room = findRoomAtPosition(displayRooms, tapOffset.x, tapOffset.y, roomSize)
                                if (room != null) {
                                    followPlayer = false
                                    viewCenterRoomId = room.id
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        )
                    }
                    .onPointerEvent(PointerEventType.Move) { event ->
                        mousePosition = event.changes.first().position
                        hoveredRoom = findRoomAtPosition(displayRooms, mousePosition.x, mousePosition.y, roomSize)
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        hoveredRoom = null
                    }
            ) {
                canvasSize = Pair(size.width, size.height)

                if (displayRooms.isEmpty() && rooms.isNotEmpty()) {
                    // Нет текущей комнаты, но есть данные
                    return@Canvas
                }

                if (displayRooms.isNotEmpty()) {
                    drawMap(
                        displayRooms = displayRooms,
                        allRooms = rooms,
                        currentRoomId = currentRoomId,
                        hoveredRoomId = hoveredRoom?.id,
                        roomSize = roomSize,
                        zoom = zoom
                    )
                }
            }

            // Тултип при наведении
            if (hoveredRoom != null) {
                RoomTooltip(
                    room = hoveredRoom!!,
                    allRooms = rooms,
                    mouseX = mousePosition.x,
                    mouseY = mousePosition.y,
                    maxWidth = 280
                )
            }

            // Информационная панель если нет текущей комнаты
            if (currentRoomId == null && rooms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Карта пуста. Начните исследование!",
                        color = Color(0xFF888888),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Кнопки навигации вверх/вниз (если есть выходы)
            val viewCenterRoom = effectiveCenterRoomId?.let { rooms[it] }
            if (viewCenterRoom != null) {
                val upExit = viewCenterRoom.exits.entries.find { it.key.dz > 0 && it.value.targetRoomId.isNotEmpty() }
                val downExit = viewCenterRoom.exits.entries.find { it.key.dz < 0 && it.value.targetRoomId.isNotEmpty() }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (upExit != null) {
                        val targetRoom = rooms[upExit.value.targetRoomId]
                        Button(
                            onClick = {
                                followPlayer = false
                                viewCenterRoomId = upExit.value.targetRoomId
                                offsetX = 0f
                                offsetY = 0f
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AAAA)),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Text("↑ ${targetRoom?.name?.take(15) ?: "Вверх"}")
                        }
                    }

                    if (downExit != null) {
                        val targetRoom = rooms[downExit.value.targetRoomId]
                        Button(
                            onClick = {
                                followPlayer = false
                                viewCenterRoomId = downExit.value.targetRoomId
                                offsetX = 0f
                                offsetY = 0f
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAA00AA)),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Text("↓ ${targetRoom?.name?.take(15) ?: "Вниз"}")
                        }
                    }
                }
            }
        }

        // Информация о текущей комнате
        if (currentRoomId != null) {
            val currentRoom = rooms[currentRoomId]
            if (currentRoom != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = Color(0xFF2B2B2B),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = currentRoom.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "ID: ${currentRoom.id}",
                            color = Color(0xFFBBBBBB),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Выходы: ${currentRoom.getAvailableDirections().joinToString(", ") { it.russianName }}",
                            color = Color(0xFFBBBBBB),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (currentRoom.zone.isNotEmpty()) {
                            Text(
                                text = "Зона: ${currentRoom.zone}",
                                color = Color(0xFF00BFFF),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (currentRoom.notes.isNotEmpty()) {
                            Text(
                                text = "Заметка: ${currentRoom.notes}",
                                color = Color(0xFFFFD700),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Статистика
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color(0xFF2B2B2B),
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Комнат на карте: ${rooms.size}",
                    color = Color(0xFFBBBBBB),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Отображается: ${displayRooms.size}",
                    color = Color(0xFFBBBBBB),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // Диалог редактирования комнаты
    if (showRoomDialog && selectedRoom != null) {
        RoomDetailsDialog(
            room = selectedRoom!!,
            onDismiss = { showRoomDialog = false },
            onSaveNote = { note ->
                clientState.setRoomNote(selectedRoom!!.id, note)
            },
            onSaveColor = { color ->
                clientState.setRoomColor(selectedRoom!!.id, color)
            },
            onSaveTags = { tags ->
                clientState.setRoomTags(selectedRoom!!.id, tags)
            },
            onSaveZone = { zone ->
                clientState.setRoomZone(selectedRoom!!.id, zone)
            }
        )
    }

    // Диалог базы данных карт
    if (showDatabaseDialog) {
        MapDatabaseDialog(
            clientState = clientState,
            onDismiss = { showDatabaseDialog = false }
        )
    }
}
