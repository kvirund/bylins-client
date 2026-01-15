package com.bylins.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.mapper.Room
import com.bylins.client.ui.theme.LocalAppColorScheme

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
    val colorScheme = LocalAppColorScheme.current
    val rooms by clientState.mapRooms.collectAsState()
    val currentRoomId by clientState.currentRoomId.collectAsState()
    val mapEnabled by clientState.mapEnabled.collectAsState()
    // Path highlighting from scripts
    val pathRoomIds by clientState.pathHighlightRoomIds.collectAsState()
    val pathTargetRoomId by clientState.pathHighlightTargetId.collectAsState()
    // Zone notes
    val zoneNotesMap by clientState.zoneNotes.collectAsState()

    // Состояние для тултипа
    var hoveredRoom by remember { mutableStateOf<Room?>(null) }
    var mousePosition by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }

    // Drag offset state
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

    // Параметры отрисовки
    val roomSize = 24f
    val roomSpacing = 48f  // Increased for better labyrinth visibility

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
                } else {
                    RoomPositionsResult(emptyMap(), true, null)
                }
            }
            val displayRooms = roomPositionsResult.displayRooms

            // Get current zone for border styling
            val currentZone = currentRoomId?.let { rooms[it]?.zone }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newOffsetX = offsetX + dragAmount.x
                            val newOffsetY = offsetY + dragAmount.y

                            val canvasW = canvasSize.first
                            val canvasH = canvasSize.second

                            if (canvasW > 0 && canvasH > 0 && displayRooms.isNotEmpty()) {
                                // Check if at least one room would be visible after drag
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
                    .onPointerEvent(PointerEventType.Exit) {
                        hoveredRoom = null
                    }
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

            // Тултип
            if (hoveredRoom != null) {
                val hoveredZoneId = hoveredRoom!!.zone ?: ""
                RoomTooltip(
                    room = hoveredRoom!!,
                    allRooms = rooms,
                    mouseX = mousePosition.x,
                    mouseY = mousePosition.y,
                    zoneNotes = zoneNotesMap[hoveredZoneId] ?: "",
                    maxWidth = 220,
                    canvasWidth = canvasSize.first,
                    canvasHeight = canvasSize.second
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
                        // Script-registered commands (pathfinding, navigation, etc.)
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
                    color = colorScheme.warning,
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
