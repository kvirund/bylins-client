package com.bylins.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.mapper.Room

/**
 * Мини-карта для отображения на главной вкладке
 * Показывает окружающие комнаты в радиусе 3-4 шагов
 */
@Composable
fun MiniMapPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val rooms by clientState.mapRooms.collectAsState()
    val currentRoomId by clientState.currentRoomId.collectAsState()
    val mapEnabled by clientState.mapEnabled.collectAsState()

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
                    .height(200.dp),
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
                    .height(200.dp),
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
                    .height(200.dp),
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
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF1E1E1E))
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val cellSize = 20f
            val radius = 4 // Показываем 4 клетки в каждую сторону

            // Рисуем сетку
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val x = centerX + dx * cellSize
                    val y = centerY + dy * cellSize

                    // Рисуем фоновую клетку
                    drawRect(
                        color = Color(0xFF2D2D2D),
                        topLeft = Offset(x - cellSize / 2, y - cellSize / 2),
                        size = androidx.compose.ui.geometry.Size(cellSize - 2, cellSize - 2)
                    )
                }
            }

            // Находим комнаты в радиусе
            val nearbyRooms = rooms.values.filter { room ->
                val dx = room.x - currentRoom.x
                val dy = room.y - currentRoom.y
                room.z == currentRoom.z && Math.abs(dx) <= radius && Math.abs(dy) <= radius
            }

            // Рисуем комнаты
            for (room in nearbyRooms) {
                val dx = room.x - currentRoom.x
                val dy = room.y - currentRoom.y
                val x = centerX + dx * cellSize
                val y = centerY + dy * cellSize

                // Цвет комнаты
                val roomColor = when {
                    room.id == currentRoomId -> Color(0xFF00FF00) // Текущая - зелёная
                    room.visited -> Color(0xFF4169E1) // Посещённая - синяя
                    else -> Color(0xFFFFFF00) // Непосещённая - жёлтая
                }

                // Рисуем комнату
                drawCircle(
                    color = roomColor,
                    radius = cellSize / 3,
                    center = Offset(x, y)
                )

                // Рисуем выходы
                for ((direction, _) in room.exits) {
                    val exitDx = direction.dx * cellSize / 2
                    val exitDy = direction.dy * cellSize / 2

                    drawLine(
                        color = roomColor.copy(alpha = 0.5f),
                        start = Offset(x, y),
                        end = Offset(x + exitDx, y + exitDy),
                        strokeWidth = 1f
                    )
                }
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

            Spacer(modifier = Modifier.height(4.dp))

            // Координаты
            Text(
                text = "Координаты: (${currentRoom.x}, ${currentRoom.y}, ${currentRoom.z})",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Выходы
            if (currentRoom.exits.isNotEmpty()) {
                Text(
                    text = "Выходы: ${currentRoom.exits.keys.joinToString(", ") { it.shortName }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
