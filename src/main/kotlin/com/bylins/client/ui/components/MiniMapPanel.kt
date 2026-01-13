package com.bylins.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.mapper.Room

/**
 * Мини-карта для отображения на главной вкладке
 * Показывает карту от текущей комнаты
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MiniMapPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val rooms by clientState.mapRooms.collectAsState()
    val currentRoomId by clientState.currentRoomId.collectAsState()
    val mapEnabled by clientState.mapEnabled.collectAsState()

    // Состояние для тултипа
    var hoveredRoom by remember { mutableStateOf<Room?>(null) }
    var mousePosition by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }

    // Параметры отрисовки
    val roomSize = 24f
    val roomSpacing = 36f

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        // Заголовок
        Text(
            text = "Мини-карта",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (!mapEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Карта отключена",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            return
        }

        if (rooms.isEmpty() || currentRoomId == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет данных",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            return
        }

        val currentRoom = rooms[currentRoomId]
        if (currentRoom == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Комната не найдена",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            return
        }

        // Мини-карта
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Вычисляем позиции комнат
            val displayRooms = remember(rooms, currentRoomId, canvasSize) {
                if (canvasSize.first > 0 && canvasSize.second > 0) {
                    calculateRoomPositions(
                        rooms = rooms,
                        startRoomId = currentRoomId,
                        centerX = canvasSize.first / 2,
                        centerY = canvasSize.second / 2,
                        roomSize = roomSize,
                        roomSpacing = roomSpacing,
                        canvasWidth = canvasSize.first,
                        canvasHeight = canvasSize.second
                    )
                } else {
                    emptyMap()
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .onPointerEvent(PointerEventType.Move) { event ->
                        mousePosition = event.changes.first().position
                        hoveredRoom = findRoomAtPosition(displayRooms, mousePosition.x, mousePosition.y, roomSize)
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        hoveredRoom = null
                    }
            ) {
                canvasSize = Pair(size.width, size.height)

                if (displayRooms.isNotEmpty()) {
                    drawMap(
                        displayRooms = displayRooms,
                        allRooms = rooms,
                        currentRoomId = currentRoomId,
                        hoveredRoomId = hoveredRoom?.id,
                        roomSize = roomSize,
                        zoom = 1f
                    )
                }
            }

            // Тултип
            if (hoveredRoom != null) {
                RoomTooltip(
                    room = hoveredRoom!!,
                    allRooms = rooms,
                    mouseX = mousePosition.x,
                    mouseY = mousePosition.y,
                    maxWidth = 180
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Информация о текущей комнате
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = currentRoom.name,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (currentRoom.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📝 ${currentRoom.notes}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = Color(0xFFFFD700),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Выходы
            if (currentRoom.exits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Выходы: ${currentRoom.exits.keys.joinToString(", ") { it.shortName }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
