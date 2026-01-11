package com.bylins.client.ui.components

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.bylins.client.ClientState
import com.bylins.client.mapper.Direction
import com.bylins.client.mapper.Room
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// Функция для парсинга HEX цвета
private fun parseHexColor(hex: String): Color? {
    return try {
        val cleanHex = hex.removePrefix("#")
        val r = cleanHex.substring(0, 2).toInt(16) / 255f
        val g = cleanHex.substring(2, 4).toInt(16) / 255f
        val b = cleanHex.substring(4, 6).toInt(16) / 255f
        Color(r, g, b)
    } catch (e: Exception) {
        null
    }
}

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
    var currentLevel by remember { mutableStateOf(0) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var showRoomDialog by remember { mutableStateOf(false) }

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
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Уровень: $currentLevel", color = Color.White)

                Button(
                    onClick = { currentLevel++ },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+")
                }

                Button(
                    onClick = { currentLevel-- },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("-")
                }

                Button(
                    onClick = { currentLevel = 0 },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("0")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { zoom = (zoom * 1.2f).coerceAtMost(5f) },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+")
                }

                Text("${(zoom * 100).toInt()}%", color = Color.White)

                Button(
                    onClick = { zoom = (zoom / 1.2f).coerceAtLeast(0.2f) },
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
                        // Экспорт карты
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
                                println("Карта экспортирована в ${fileChooser.selectedFile.absolutePath}")
                            } catch (e: Exception) {
                                println("Ошибка экспорта: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("Экспорт")
                }

                Button(
                    onClick = {
                        // Импорт карты
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
                                println("Карта импортирована из ${fileChooser.selectedFile.absolutePath}")
                            } catch (e: Exception) {
                                println("Ошибка импорта: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("Импорт")
                }
            }
        }

        // Canvas для отображения карты
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
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        // Определяем на какую комнату кликнули
                        val roomsOnLevel = rooms.values.filter { it.z == currentLevel }
                        val roomSize = 40f * zoom
                        val roomSpacing = 60f * zoom
                        val centerX = size.width / 2 + offsetX
                        val centerY = size.height / 2 + offsetY

                        roomsOnLevel.forEach { room ->
                            val roomX = centerX + room.x * roomSpacing
                            val roomY = centerY + room.y * roomSpacing

                            if (abs(tapOffset.x - roomX) < roomSize / 2 &&
                                abs(tapOffset.y - roomY) < roomSize / 2) {
                                selectedRoom = room
                                showRoomDialog = true
                            }
                        }
                    }
                }
        ) {
            val roomsOnLevel = rooms.values.filter { it.z == currentLevel }

            if (roomsOnLevel.isEmpty()) {
                // Показываем сообщение если нет комнат
                return@Canvas
            }

            val roomSize = 40f * zoom
            val roomSpacing = 60f * zoom

            // Центрируем карту
            val centerX = size.width / 2 + offsetX
            val centerY = size.height / 2 + offsetY

            // Рисуем соединения между комнатами
            roomsOnLevel.forEach { room ->
                val roomX = centerX + room.x * roomSpacing
                val roomY = centerY + room.y * roomSpacing

                room.exits.forEach { (direction, exit) ->
                    val targetRoom = rooms[exit.targetRoomId]
                    if (targetRoom != null && targetRoom.z == currentLevel) {
                        val targetX = centerX + targetRoom.x * roomSpacing
                        val targetY = centerY + targetRoom.y * roomSpacing

                        // Рисуем линию
                        drawLine(
                            color = Color(0xFF666666),
                            start = Offset(roomX, roomY),
                            end = Offset(targetX, targetY),
                            strokeWidth = 2f * zoom
                        )
                    }
                }
            }

            // Рисуем комнаты
            roomsOnLevel.forEach { room ->
                val roomX = centerX + room.x * roomSpacing
                val roomY = centerY + room.y * roomSpacing

                // Цвет комнаты (пользовательский или стандартный)
                val roomColor = if (room.color != null) {
                    parseHexColor(room.color) ?: when {
                        room.id == currentRoomId -> Color(0xFF00FF00)
                        room.visited -> Color(0xFF4444FF)
                        else -> Color(0xFF888888)
                    }
                } else {
                    when {
                        room.id == currentRoomId -> Color(0xFF00FF00) // Текущая комната - зеленая
                        room.visited -> Color(0xFF4444FF) // Посещенная - синяя
                        else -> Color(0xFF888888) // Непосещенная - серая
                    }
                }

                // Рисуем квадрат комнаты
                drawRect(
                    color = roomColor,
                    topLeft = Offset(roomX - roomSize / 2, roomY - roomSize / 2),
                    size = Size(roomSize, roomSize),
                    style = Stroke(width = 2f * zoom)
                )

                // Рисуем заполнение для текущей комнаты или комнаты с цветом
                if (room.id == currentRoomId) {
                    drawRect(
                        color = roomColor.copy(alpha = 0.3f),
                        topLeft = Offset(roomX - roomSize / 2, roomY - roomSize / 2),
                        size = Size(roomSize, roomSize)
                    )
                } else if (room.color != null) {
                    drawRect(
                        color = roomColor.copy(alpha = 0.2f),
                        topLeft = Offset(roomX - roomSize / 2, roomY - roomSize / 2),
                        size = Size(roomSize, roomSize)
                    )
                }

                // Индикатор заметки
                if (room.notes.isNotEmpty()) {
                    drawCircle(
                        color = Color(0xFFFFFF00), // Жёлтый кружок
                        radius = 4f * zoom,
                        center = Offset(roomX + roomSize / 2 - 6f * zoom, roomY - roomSize / 2 + 6f * zoom)
                    )
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
                            text = "Координаты: (${currentRoom.x}, ${currentRoom.y}, ${currentRoom.z})",
                            color = Color(0xFFBBBBBB),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Выходы: ${currentRoom.getAvailableDirections().joinToString(", ") { it.russianName }}",
                            color = Color(0xFFBBBBBB),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (currentRoom.notes.isNotEmpty()) {
                            Text(
                                text = "Заметки: ${currentRoom.notes}",
                                color = Color(0xFFFFFF00),
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
                    text = "Комнат на уровне $currentLevel: ${rooms.values.count { it.z == currentLevel }}",
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
            }
        )
    }
}
