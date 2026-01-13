package com.bylins.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.mapper.Direction
import com.bylins.client.mapper.Room
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Информация о позиции комнаты на экране
 */
data class RoomDisplayInfo(
    val room: Room,
    val screenX: Float,
    val screenY: Float,
    val gridX: Int,
    val gridY: Int
)

/**
 * Вычисляет позиции комнат для отображения используя BFS от стартовой комнаты.
 * Комнаты размещаются на сетке, позиция определяется направлением от родителя.
 * Останавливается когда выходим за границы или позиция уже занята.
 */
fun calculateRoomPositions(
    rooms: Map<String, Room>,
    startRoomId: String?,
    centerX: Float,
    centerY: Float,
    roomSize: Float,
    roomSpacing: Float,
    canvasWidth: Float,
    canvasHeight: Float
): Map<String, RoomDisplayInfo> {
    val result = mutableMapOf<String, RoomDisplayInfo>()

    if (startRoomId == null) return result
    val startRoom = rooms[startRoomId] ?: return result

    // Занятые позиции на сетке
    val occupiedPositions = mutableSetOf<Pair<Int, Int>>()

    // BFS очередь: roomId, gridX, gridY
    val queue = ArrayDeque<Triple<String, Int, Int>>()
    queue.add(Triple(startRoomId, 0, 0))

    val visited = mutableSetOf<String>()

    while (queue.isNotEmpty()) {
        val (roomId, gridX, gridY) = queue.removeFirst()

        if (roomId in visited) continue
        val room = rooms[roomId] ?: continue

        // Проверяем занята ли позиция
        val gridPos = Pair(gridX, gridY)
        if (gridPos in occupiedPositions) continue

        // Вычисляем экранную позицию
        val screenX = centerX + gridX * roomSpacing
        val screenY = centerY + gridY * roomSpacing

        // Проверяем границы канвы (с отступом для комнаты)
        val margin = roomSize / 2 + 5f
        if (screenX < margin || screenX > canvasWidth - margin ||
            screenY < margin || screenY > canvasHeight - margin) {
            continue
        }

        visited.add(roomId)
        occupiedPositions.add(gridPos)
        result[roomId] = RoomDisplayInfo(room, screenX, screenY, gridX, gridY)

        // Добавляем соседние комнаты в очередь
        for ((direction, exit) in room.exits) {
            if (exit.targetRoomId !in visited) {
                val newGridX = gridX + direction.dx
                val newGridY = gridY + direction.dy
                queue.add(Triple(exit.targetRoomId, newGridX, newGridY))
            }
        }
    }

    return result
}

/**
 * Парсит HEX цвет
 */
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

/**
 * Рисует карту комнат
 */
fun DrawScope.drawMap(
    displayRooms: Map<String, RoomDisplayInfo>,
    allRooms: Map<String, Room>,
    currentRoomId: String?,
    hoveredRoomId: String?,
    roomSize: Float,
    zoom: Float = 1f
) {
    // 1. Рисуем соединения между комнатами
    displayRooms.values.forEach { info ->
        val room = info.room

        room.exits.forEach { (direction, exit) ->
            val isExplored = exit.targetRoomId.isNotEmpty()
            val targetInfo = if (isExplored) displayRooms[exit.targetRoomId] else null

            if (targetInfo != null) {
                // Изведанный выход к видимой комнате - сплошная линия
                val startX = info.screenX + direction.dx * roomSize / 2
                val startY = info.screenY + direction.dy * roomSize / 2
                val endX = targetInfo.screenX - direction.dx * roomSize / 2
                val endY = targetInfo.screenY - direction.dy * roomSize / 2

                drawLine(
                    color = Color(0xFF555555),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2f * zoom
                )
            } else if (direction.dz == 0) {
                // Неизведанный выход или выход к невидимой комнате - пунктир
                val startX = info.screenX + direction.dx * roomSize / 2
                val startY = info.screenY + direction.dy * roomSize / 2
                val exitLength = roomSize * 0.6f
                val endX = startX + direction.dx * exitLength
                val endY = startY + direction.dy * exitLength

                // Оранжевый для неизведанных, серый для изведанных но невидимых
                val exitColor = if (isExplored) Color(0xFF888888) else Color(0xFFFF6600)

                drawLine(
                    color = exitColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2f * zoom,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * zoom, 3f * zoom), 0f)
                )

                // Кружок на конце (знак вопроса для неизведанных)
                drawCircle(
                    color = exitColor,
                    radius = 3f * zoom,
                    center = Offset(endX, endY)
                )
            }
        }
    }

    // 2. Рисуем комнаты
    displayRooms.values.forEach { info ->
        val room = info.room
        val isCurrentRoom = room.id == currentRoomId
        val isHovered = room.id == hoveredRoomId

        // Цвет комнаты
        val roomColor = if (room.color != null) {
            parseHexColor(room.color) ?: getDefaultRoomColor(isCurrentRoom, room.visited)
        } else {
            getDefaultRoomColor(isCurrentRoom, room.visited)
        }

        // Подсветка при наведении
        if (isHovered) {
            drawRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(info.screenX - roomSize / 2 - 3f * zoom, info.screenY - roomSize / 2 - 3f * zoom),
                size = Size(roomSize + 6f * zoom, roomSize + 6f * zoom)
            )
        }

        // Квадрат комнаты
        drawRect(
            color = roomColor,
            topLeft = Offset(info.screenX - roomSize / 2, info.screenY - roomSize / 2),
            size = Size(roomSize, roomSize),
            style = Stroke(width = 2f * zoom)
        )

        // Заполнение для текущей комнаты
        if (isCurrentRoom) {
            drawRect(
                color = roomColor.copy(alpha = 0.3f),
                topLeft = Offset(info.screenX - roomSize / 2, info.screenY - roomSize / 2),
                size = Size(roomSize, roomSize)
            )
        } else if (room.color != null) {
            drawRect(
                color = roomColor.copy(alpha = 0.15f),
                topLeft = Offset(info.screenX - roomSize / 2, info.screenY - roomSize / 2),
                size = Size(roomSize, roomSize)
            )
        }

        // Индикатор заметки
        if (room.notes.isNotEmpty()) {
            drawCircle(
                color = Color(0xFFFFD700),
                radius = 3f * zoom,
                center = Offset(info.screenX + roomSize / 2 - 5f * zoom, info.screenY - roomSize / 2 + 5f * zoom)
            )
        }

        // Индикаторы выходов вверх/вниз
        val hasUp = room.exits.keys.any { it.dz > 0 }
        val hasDown = room.exits.keys.any { it.dz < 0 }

        if (hasUp) {
            // Треугольник вверх (голубой)
            val cx = info.screenX
            val cy = info.screenY - roomSize / 2 + 5f * zoom
            drawLine(Color(0xFF00DDDD), Offset(cx - 4f * zoom, cy + 3f * zoom), Offset(cx, cy - 2f * zoom), 2f * zoom)
            drawLine(Color(0xFF00DDDD), Offset(cx, cy - 2f * zoom), Offset(cx + 4f * zoom, cy + 3f * zoom), 2f * zoom)
        }

        if (hasDown) {
            // Треугольник вниз (розовый)
            val cx = info.screenX
            val cy = info.screenY + roomSize / 2 - 5f * zoom
            drawLine(Color(0xFFDD00DD), Offset(cx - 4f * zoom, cy - 3f * zoom), Offset(cx, cy + 2f * zoom), 2f * zoom)
            drawLine(Color(0xFFDD00DD), Offset(cx, cy + 2f * zoom), Offset(cx + 4f * zoom, cy - 3f * zoom), 2f * zoom)
        }
    }
}

private fun getDefaultRoomColor(isCurrentRoom: Boolean, visited: Boolean): Color {
    return when {
        isCurrentRoom -> Color(0xFF00FF00) // Текущая - зелёная
        visited -> Color(0xFF4488FF)        // Посещённая - синяя
        else -> Color(0xFF888888)           // Непосещённая - серая
    }
}

/**
 * Находит комнату под курсором
 */
fun findRoomAtPosition(
    displayRooms: Map<String, RoomDisplayInfo>,
    mouseX: Float,
    mouseY: Float,
    roomSize: Float
): Room? {
    return displayRooms.values.firstOrNull { info ->
        abs(mouseX - info.screenX) < roomSize / 2 &&
        abs(mouseY - info.screenY) < roomSize / 2
    }?.room
}

/**
 * Компонент тултипа для комнаты
 */
@Composable
fun RoomTooltip(
    room: Room,
    allRooms: Map<String, Room>,
    mouseX: Float,
    mouseY: Float,
    maxWidth: Int = 250
) {
    val tooltipX = (mouseX + 12).coerceAtLeast(5f)
    val tooltipY = (mouseY + 12).coerceAtLeast(5f)

    Surface(
        modifier = Modifier
            .offset { IntOffset(tooltipX.roundToInt(), tooltipY.roundToInt()) }
            .widthIn(max = maxWidth.dp),
        color = Color(0xEE2B2B2B),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Название
            Text(
                text = room.name,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Color.White,
                maxLines = 2
            )

            // ID
            Text(
                text = "ID: ${room.id}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = Color(0xFFAAAAAA)
            )

            // Выходы
            if (room.exits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                room.exits.forEach { (dir, exit) ->
                    val isExplored = exit.targetRoomId.isNotEmpty()
                    val targetRoom = if (isExplored) allRooms[exit.targetRoomId] else null
                    val targetName = when {
                        !isExplored -> "???"
                        targetRoom != null -> targetRoom.name.take(20)
                        else -> "(за пределами)"
                    }
                    val color = when {
                        !isExplored -> Color(0xFFFF6600)       // Неизведанный - оранжевый
                        targetRoom != null -> Color(0xFF88CC88) // Известный - зелёный
                        else -> Color(0xFF888888)               // Известный но невидимый - серый
                    }
                    Text(
                        text = "${dir.shortName} → $targetName",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = color,
                        maxLines = 1
                    )
                }
            }

            // Зона
            if (room.zone.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Зона: ${room.zone}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = Color(0xFF00BFFF)
                )
            }

            // Заметка
            if (room.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "📝 ${room.notes}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = Color(0xFFFFD700),
                    maxLines = 2
                )
            }

            // Теги
            if (room.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Теги: ${room.tags.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = Color(0xFFCC88FF)
                )
            }
        }
    }
}
