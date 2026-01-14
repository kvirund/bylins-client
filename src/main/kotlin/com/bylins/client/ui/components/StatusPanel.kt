package com.bylins.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import java.awt.Cursor
import com.bylins.client.mapper.Room
import com.bylins.client.status.StatusElement
import com.bylins.client.status.FlagItem
import com.bylins.client.ui.theme.LocalAppColorScheme

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
        // All elements (including path panels) are added by scripts via statusManager
        if (elements.isNotEmpty()) {
            // Отображаем элементы в порядке их order
            val orderedElements = clientState.statusManager.getOrderedElements()

            orderedElements.forEach { element ->
                when (element) {
                    is StatusElement.Bar -> StatusBarElement(element)
                    is StatusElement.Text -> StatusTextElement(element)
                    is StatusElement.Flags -> StatusFlagsElement(element)
                    is StatusElement.MiniMap -> StatusMiniMapElement(element, clientState)
                    is StatusElement.PathPanel -> StatusPathPanelElement(element, clientState)
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

    val colorScheme = LocalAppColorScheme.current
    val rooms by clientState.mapRooms.collectAsState()
    val currentRoomId by clientState.currentRoomId.collectAsState()
    val mapEnabled by clientState.mapEnabled.collectAsState()
    val miniMapHeight by clientState.miniMapHeight.collectAsState()
    // Path highlighting from scripts
    val pathRoomIds by clientState.pathHighlightRoomIds.collectAsState()
    val pathTargetRoomId by clientState.pathHighlightTargetId.collectAsState()
    // Zone notes
    val zoneNotesMap by clientState.zoneNotes.collectAsState()

    var hoveredRoom by remember { mutableStateOf<Room?>(null) }
    var mousePosition by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }

    // Drag offset state for map panning
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Reset offset when current room changes
    LaunchedEffect(currentRoomId) {
        offsetX = 0f
        offsetY = 0f
    }

    // Context menu state
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuRoom by remember { mutableStateOf<Room?>(null) }
    var contextMenuPosition by remember { mutableStateOf(Offset.Zero) }

    val roomSize = 20f
    val roomSpacing = 40f  // Increased for better labyrinth visibility
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Minimap content box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(miniMapHeight.dp)
                .background(colorScheme.background)
        ) {
            if (!mapEnabled) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Карта отключена",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else if (rooms.isEmpty() || currentRoomId == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет данных",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val roomPositionsResult = remember(rooms, currentRoomId, canvasSize, offsetX, offsetY) {
                    if (canvasSize.first > 0 && canvasSize.second > 0 && currentRoomId != null) {
                        calculateRoomPositions(
                            rooms = rooms,
                            startRoomId = currentRoomId,
                            centerX = canvasSize.first / 2 + offsetX,
                            centerY = canvasSize.second / 2 + offsetY,
                            roomSize = roomSize,
                            roomSpacing = roomSpacing,
                            canvasWidth = canvasSize.first,
                            canvasHeight = canvasSize.second
                        )
                    } else RoomPositionsResult(emptyMap(), true, null)
                }
                val displayRooms = roomPositionsResult.displayRooms
                val currentZone = currentRoomId?.let { rooms[it]?.zone }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newOffsetX = offsetX + dragAmount.x
                                val newOffsetY = offsetY + dragAmount.y

                                val canvasW = canvasSize.first
                                val canvasH = canvasSize.second

                                if (canvasW > 0 && canvasH > 0 && displayRooms.isNotEmpty()) {
                                    val margin = roomSize / 2
                                    val anyVisible = displayRooms.values.any { roomInfo ->
                                        val newX = roomInfo.screenX + dragAmount.x
                                        val newY = roomInfo.screenY + dragAmount.y
                                        newX >= -margin && newX <= canvasW + margin &&
                                        newY >= -margin && newY <= canvasH + margin
                                    }

                                    if (anyVisible) {
                                        offsetX = newOffsetX
                                        offsetY = newOffsetY
                                    }
                                } else {
                                    offsetX = newOffsetX
                                    offsetY = newOffsetY
                                }
                            }
                        }
                        .onPointerEvent(PointerEventType.Move) { event ->
                            mousePosition = event.changes.first().position
                            hoveredRoom = findRoomAtPosition(displayRooms, mousePosition.x, mousePosition.y, roomSize)
                        }
                        .onPointerEvent(PointerEventType.Exit) { hoveredRoom = null }
                        .onPointerEvent(PointerEventType.Press) { event ->
                            // Right-click context menu
                            val change = event.changes.first()
                            if (event.button == PointerButton.Secondary) {
                                val room = findRoomAtPosition(displayRooms, change.position.x, change.position.y, roomSize)
                                if (room != null) {
                                    contextMenuRoom = room
                                    contextMenuPosition = change.position
                                    showContextMenu = true
                                }
                            }
                        }
                ) {
                    canvasSize = Pair(size.width, size.height)
                    if (displayRooms.isNotEmpty()) {
                        drawMap(
                            displayRooms = displayRooms,
                            allRooms = rooms,
                            currentRoomId = currentRoomId,
                            currentZone = currentZone,
                            hoveredRoomId = hoveredRoom?.id,
                            roomSize = roomSize,
                            zoom = 1f,
                            pathRoomIds = pathRoomIds,
                            pathTargetRoomId = pathTargetRoomId,
                            colors = MapRenderColors.fromColorScheme(colorScheme)
                        )
                    }
                }

                if (hoveredRoom != null) {
                    val hoveredZoneId = hoveredRoom!!.zone ?: ""
                    RoomTooltip(
                        room = hoveredRoom!!,
                        allRooms = rooms,
                        mouseX = mousePosition.x,
                        mouseY = mousePosition.y,
                        zoneNotes = zoneNotesMap[hoveredZoneId] ?: "",
                        maxWidth = 350
                    )
                }

                // Context menu
                if (showContextMenu && contextMenuRoom != null) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(contextMenuPosition.x.toInt(), contextMenuPosition.y.toInt()) }
                    ) {
                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false }
                        ) {
                            // Script-registered commands
                            val customCommands = clientState.getMapContextCommands()
                            customCommands.forEach { (name, _) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        contextMenuRoom?.let { room ->
                                            clientState.executeMapCommand(name, room)
                                        }
                                        showContextMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Drag handle for vertical resizing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colorScheme.border)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newHeight = (miniMapHeight + dragAmount.y / density.density).toInt()
                        clientState.setMiniMapHeight(newHeight)
                    }
                }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusPathPanelElement(
    pathPanel: StatusElement.PathPanel,
    clientState: ClientState
) {
    val colorScheme = LocalAppColorScheme.current
    val focusManager = LocalFocusManager.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: target name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Путь к:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = pathPanel.targetName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Steps count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Шагов:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "${pathPanel.stepsCount}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            // Next direction (first in list)
            if (pathPanel.directions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Следующий:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = pathPanel.directions.first().uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.warning  // Gold color for next direction
                    )
                }

                // Path preview (first 8 directions)
                val preview = pathPanel.directions.take(8).joinToString(" → ")
                val suffix = if (pathPanel.directions.size > 8) " ..." else ""
                Text(
                    text = preview + suffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Buttons row - only show if callbacks are provided
            if (pathPanel.hasFollowCallback || pathPanel.hasClearCallback) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (pathPanel.hasFollowCallback) {
                        TextButton(
                            onClick = {
                                clientState.statusManager.invokePathPanelCallback(pathPanel.id, "follow")
                                focusManager.clearFocus()
                                clientState.requestInputFocus()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Следовать",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.success
                            )
                        }
                    }
                    if (pathPanel.hasClearCallback) {
                        TextButton(
                            onClick = {
                                clientState.statusManager.invokePathPanelCallback(pathPanel.id, "clear")
                                focusManager.clearFocus()
                                clientState.requestInputFocus()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Очистить",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
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

