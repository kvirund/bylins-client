package com.bylins.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.mapper.Room
import com.bylins.client.status.StatusElement
import com.bylins.client.status.FlagItem

@Composable
fun StatusPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val elements by clientState.statusManager.elements.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (elements.isNotEmpty()) {
            // Отображаем элементы в порядке их order
            val orderedElements = clientState.statusManager.getOrderedElements()

            orderedElements.forEach { element ->
                when (element) {
                    is StatusElement.Bar -> StatusBarElement(element)
                    is StatusElement.Text -> StatusTextElement(element)
                    is StatusElement.Flags -> StatusFlagsElement(element)
                    is StatusElement.MiniMap -> StatusMiniMapElement(element, clientState)
                }
            }
        }
    }
}

@Composable
private fun StatusBarElement(bar: StatusElement.Bar) {
    val color = parseColor(bar.color)
    val progress = if (bar.max > 0) bar.value.toFloat() / bar.max.toFloat() else 0f

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = bar.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (bar.showText) {
                Text(
                    text = "${bar.value} / ${bar.max}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun StatusTextElement(text: StatusElement.Text) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${text.label}:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = text.value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusFlagsElement(flags: StatusElement.Flags) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = flags.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        // Отображаем флаги в виде чипов
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            flags.flags.forEach { flag ->
                FlagChip(flag)
            }
        }
    }
}

@Composable
private fun FlagChip(flag: FlagItem) {
    val color = parseColor(flag.color)
    val backgroundColor = if (flag.active) {
        color.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    val textColor = if (flag.active) {
        color
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = flag.name,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontSize = 10.sp
            )
            // Показываем таймер если есть
            flag.timer?.let { timer ->
                Text(
                    text = timer,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StatusMiniMapElement(
    miniMap: StatusElement.MiniMap,
    clientState: ClientState
) {
    if (!miniMap.visible) return

    val rooms by clientState.mapRooms.collectAsState()
    val currentRoomId by clientState.currentRoomId.collectAsState()
    val mapEnabled by clientState.mapEnabled.collectAsState()

    var hoveredRoom by remember { mutableStateOf<Room?>(null) }
    var mousePosition by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }

    val roomSize = 20f
    val roomSpacing = 30f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        if (!mapEnabled) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Карта отключена",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            return
        }

        if (rooms.isEmpty() || currentRoomId == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет данных",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            return
        }

        Box(modifier = Modifier.fillMaxSize()) {
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
                } else emptyMap()
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .onPointerEvent(PointerEventType.Move) { event ->
                        mousePosition = event.changes.first().position
                        hoveredRoom = findRoomAtPosition(displayRooms, mousePosition.x, mousePosition.y, roomSize)
                    }
                    .onPointerEvent(PointerEventType.Exit) { hoveredRoom = null }
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
    }
}

/**
 * Парсит цвет из строки
 * Поддерживает: "green", "red", "yellow", "blue", "white", "#RRGGBB", "#AARRGGBB"
 */
private fun parseColor(colorString: String): Color {
    return when (colorString.lowercase()) {
        "green" -> Color(0xFF4CAF50)
        "red" -> Color(0xFFF44336)
        "yellow" -> Color(0xFFFFC107)
        "blue" -> Color(0xFF2196F3)
        "white" -> Color.White
        "cyan" -> Color(0xFF00BCD4)
        "magenta" -> Color(0xFFE91E63)
        "orange" -> Color(0xFFFF9800)
        "purple" -> Color(0xFF9C27B0)
        "gray", "grey" -> Color(0xFF9E9E9E)
        else -> {
            // Пробуем распарсить как hex
            try {
                if (colorString.startsWith("#")) {
                    val hex = colorString.removePrefix("#")
                    when (hex.length) {
                        6 -> {
                            val r = hex.substring(0, 2).toInt(16)
                            val g = hex.substring(2, 4).toInt(16)
                            val b = hex.substring(4, 6).toInt(16)
                            Color(r, g, b)
                        }
                        8 -> {
                            val a = hex.substring(0, 2).toInt(16)
                            val r = hex.substring(2, 4).toInt(16)
                            val g = hex.substring(4, 6).toInt(16)
                            val b = hex.substring(6, 8).toInt(16)
                            Color(r, g, b, a)
                        }
                        else -> Color.White
                    }
                } else {
                    Color.White
                }
            } catch (e: Exception) {
                Color.White
            }
        }
    }
}
