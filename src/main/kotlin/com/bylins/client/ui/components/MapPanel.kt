package com.bylins.client.ui.components

import mu.KotlinLogging
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import java.awt.Cursor
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState
import com.bylins.client.mapper.Direction
import com.bylins.client.mapper.Room
import com.bylins.client.ui.theme.LocalAppColorScheme
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
    val colorScheme = LocalAppColorScheme.current
    val rooms by clientState.mapRooms.collectAsState()
    val currentRoomId by clientState.currentRoomId.collectAsState()
    val mapEnabled by clientState.mapEnabled.collectAsState()
    // Path highlighting from scripts
    val pathRoomIds by clientState.pathHighlightRoomIds.collectAsState()
    val pathTargetRoomId by clientState.pathHighlightTargetId.collectAsState()
    // Zone notes
    val zoneNotesMap by clientState.zoneNotes.collectAsState()
    // Сохранённый центр обзора карты из MapManager
    val savedViewCenterRoomId by clientState.mapViewCenterRoomId.collectAsState()

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var zoom by remember { mutableStateOf(1f) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var showRoomDialog by remember { mutableStateOf(false) }
    var showDatabaseDialog by remember { mutableStateOf(false) }
    var showGoToRoomDialog by remember { mutableStateOf(false) }
    var hoveredRoom by remember { mutableStateOf<Room?>(null) }
    var mousePosition by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }

    // Центр обзора карты (инициализируется из сохранённого значения)
    var viewCenterRoomId by remember(savedViewCenterRoomId) { mutableStateOf(savedViewCenterRoomId) }
    var followPlayer by remember { mutableStateOf(savedViewCenterRoomId == null) }

    // Сохраняем viewCenterRoomId в mapManager при изменении
    LaunchedEffect(viewCenterRoomId) {
        clientState.setMapViewCenterRoom(viewCenterRoomId)
    }

    // Context menu state
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuRoom by remember { mutableStateOf<Room?>(null) }
    var contextMenuPosition by remember { mutableStateOf(Offset.Zero) }

    // Zone panel width (resizable)
    var zonePanelWidth by remember { mutableStateOf(220f) }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

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
    val baseRoomSpacing = 64f  // Increased for better labyrinth visibility
    val roomSize = baseRoomSize * zoom
    val roomSpacing = baseRoomSpacing * zoom

    // Вычисляем позиции комнат с учётом смещения
    val roomPositionsResult = remember(rooms, effectiveCenterRoomId, canvasSize, offsetX, offsetY, zoom) {
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
            RoomPositionsResult(emptyMap(), true, null)
        }
    }
    val displayRooms = roomPositionsResult.displayRooms
    val viewCenterDirection = roomPositionsResult.startRoomDirection

    // Keep updated reference for use in gesture handlers
    val currentDisplayRooms by rememberUpdatedState(displayRooms)

    Column(modifier = modifier) {
        // Панель управления
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.surface)
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
                Text("Следовать", color = if (followPlayer) Color.White else colorScheme.onSurfaceVariant)
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

                    // Перейти к комнате по ID
                    Button(
                        onClick = { showGoToRoomDialog = true },
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Text("Перейти к...")
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

        // Основная область карты с панелью зоны
        // Получаем текущую зону из комнаты
        val currentZoneId = effectiveCenterRoomId?.let { rooms[it]?.zone } ?: ""
        val currentAreaName = effectiveCenterRoomId?.let { rooms[it]?.area }
        // Формат: "Area (Zone ID)" или просто Zone ID если нет area
        val currentZoneName = when {
            currentAreaName != null && currentZoneId.isNotEmpty() -> "$currentAreaName ($currentZoneId)"
            currentAreaName != null -> currentAreaName
            currentZoneId.isNotEmpty() -> currentZoneId
            else -> "Неизвестная зона"
        }
        val currentZoneNotes = zoneNotesMap[currentZoneId] ?: ""

        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            // Карта
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Get current zone for border styling
                val currentZone = effectiveCenterRoomId?.let { rooms[it]?.zone }

            // Click tracking via onPointerEvent
            var pendingClickRoom by remember { mutableStateOf<Room?>(null) }
            var pendingClickTime by remember { mutableStateOf(0L) }
            var pressPos by remember { mutableStateOf(Offset.Zero) }
            var totalDragDist by remember { mutableStateOf(0f) }
            var isPressed by remember { mutableStateOf(false) }
            val doubleClickTimeout = 300L
            val dragThreshold = 10f

            // Execute pending single click after timeout
            LaunchedEffect(pendingClickRoom, pendingClickTime) {
                if (pendingClickRoom != null) {
                    kotlinx.coroutines.delay(doubleClickTimeout)
                    if (pendingClickRoom != null) {
                        followPlayer = false
                        viewCenterRoomId = pendingClickRoom!!.id
                        offsetX = 0f
                        offsetY = 0f
                        pendingClickRoom = null
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .onPointerEvent(PointerEventType.Press) { event ->
                        val change = event.changes.first()
                        if (event.button == PointerButton.Primary) {
                            pressPos = change.position
                            totalDragDist = 0f
                            isPressed = true
                        } else if (event.button == PointerButton.Secondary) {
                            // Right-click context menu
                            val room = findRoomAtPosition(displayRooms, change.position.x, change.position.y, roomSize)
                            if (room != null) {
                                contextMenuRoom = room
                                contextMenuPosition = change.position
                                showContextMenu = true
                            }
                        }
                    }
                    .onPointerEvent(PointerEventType.Move) { event ->
                        val change = event.changes.first()
                        mousePosition = change.position
                        hoveredRoom = findRoomAtPosition(displayRooms, mousePosition.x, mousePosition.y, roomSize)

                        // Handle drag
                        if (isPressed && change.pressed) {
                            val delta = change.position - pressPos
                            val dist = delta.getDistance()

                            if (dist > dragThreshold || totalDragDist > dragThreshold) {
                                // It's a drag
                                totalDragDist += (change.position - (if (totalDragDist > 0) change.previousPosition else pressPos)).getDistance()
                                pendingClickRoom = null  // Cancel pending click

                                val dragDelta = change.position - change.previousPosition
                                val newOffsetX = offsetX + dragDelta.x
                                val newOffsetY = offsetY + dragDelta.y

                                val canvasW = canvasSize.first
                                val canvasH = canvasSize.second
                                val rooms = currentDisplayRooms

                                if (canvasW > 0 && canvasH > 0 && rooms.isNotEmpty()) {
                                    val margin = roomSize / 2
                                    val anyVisible = rooms.values.any { roomInfo ->
                                        val newX = roomInfo.screenX + dragDelta.x
                                        val newY = roomInfo.screenY + dragDelta.y
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
                    }
                    .onPointerEvent(PointerEventType.Release) { event ->
                        if (event.button == PointerButton.Primary && isPressed) {
                            isPressed = false
                            val wasDrag = totalDragDist > dragThreshold

                            if (!wasDrag) {
                                val clickedRoom = findRoomAtPosition(displayRooms, pressPos.x, pressPos.y, roomSize)

                                if (clickedRoom != null) {
                                    if (pendingClickRoom?.id == clickedRoom.id) {
                                        // Double click - open dialog only
                                        pendingClickRoom = null
                                        selectedRoom = clickedRoom
                                        showRoomDialog = true
                                    } else {
                                        // Schedule single click (delayed to check for double-click)
                                        pendingClickRoom = clickedRoom
                                        pendingClickTime = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        hoveredRoom = null
                        isPressed = false
                    }
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val delta = event.changes.first().scrollDelta.y
                        zoom = if (delta < 0) {
                            (zoom * 1.15f).coerceAtMost(3f)
                        } else {
                            (zoom / 1.15f).coerceAtLeast(0.3f)
                        }
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
                        viewCenterRoomId = effectiveCenterRoomId,
                        currentZone = currentZone,
                        hoveredRoomId = hoveredRoom?.id,
                        roomSize = roomSize,
                        zoom = zoom,
                        pathRoomIds = pathRoomIds,
                        pathTargetRoomId = pathTargetRoomId,
                        colors = MapRenderColors.fromColorScheme(colorScheme)
                    )
                }

                // Индикатор направления к комнате обзора (простые стрелки в середине краёв)
                if (viewCenterDirection != null) {
                    val arrowSize = 12f
                    val margin = 15f
                    val dirX = viewCenterDirection.first
                    val dirY = viewCenterDirection.second
                    val color = colorScheme.secondary.copy(alpha = 0.7f)

                    // Определяем основное направление (только одно из 4)
                    val isHorizontal = kotlin.math.abs(dirX) > kotlin.math.abs(dirY)

                    if (isHorizontal) {
                        // Стрелка на левом или правом краю
                        val edgeX = if (dirX > 0) size.width - margin else margin
                        val centerY = size.height / 2
                        val pointDir = if (dirX > 0) 1f else -1f

                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(edgeX + pointDir * arrowSize / 2, centerY)
                            lineTo(edgeX - pointDir * arrowSize / 2, centerY - arrowSize / 2)
                            lineTo(edgeX - pointDir * arrowSize / 2, centerY + arrowSize / 2)
                            close()
                        }
                        drawPath(path, color)
                    } else {
                        // Стрелка на верхнем или нижнем краю
                        val centerX = size.width / 2
                        val edgeY = if (dirY > 0) size.height - margin else margin
                        val pointDir = if (dirY > 0) 1f else -1f

                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(centerX, edgeY + pointDir * arrowSize / 2)
                            lineTo(centerX - arrowSize / 2, edgeY - pointDir * arrowSize / 2)
                            lineTo(centerX + arrowSize / 2, edgeY - pointDir * arrowSize / 2)
                            close()
                        }
                        drawPath(path, color)
                    }
                }
            }

            // Тултип при наведении
            if (hoveredRoom != null) {
                val hoveredZoneId = hoveredRoom!!.zone ?: ""
                RoomTooltip(
                    room = hoveredRoom!!,
                    allRooms = rooms,
                    mouseX = mousePosition.x,
                    mouseY = mousePosition.y,
                    zoneNotes = zoneNotesMap[hoveredZoneId] ?: "",
                    maxWidth = 280,
                    canvasWidth = canvasSize.first,
                    canvasHeight = canvasSize.second
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
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Context menu at mouse position
            if (showContextMenu && contextMenuRoom != null) {
                // Use Box with offset for proper positioning
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

                        // Built-in: Edit room
                        if (customCommands.isNotEmpty()) {
                            Divider()
                        }
                        DropdownMenuItem(
                            text = { Text("Редактировать") },
                            onClick = {
                                selectedRoom = contextMenuRoom
                                showRoomDialog = true
                                showContextMenu = false
                            }
                        )
                    }
                }
            }

            // Direction navigation pad (6 directions: N, W, E, S, U, D)
            val viewCenterRoom = effectiveCenterRoomId?.let { rooms[it] }
            if (viewCenterRoom != null) {
                DirectionPad(
                    room = viewCenterRoom,
                    allRooms = rooms,
                    onNavigate = { targetRoomId ->
                        followPlayer = false
                        viewCenterRoomId = targetRoomId
                        offsetX = 0f
                        offsetY = 0f
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
            } // End of Box

            // Панель зоны справа с ручкой для ресайза
            if (currentZoneId.isNotEmpty()) {
                // Ручка для изменения ширины (между картой и панелью)
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(colorScheme.border)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Drag to the left = increase panel width, to the right = decrease
                                val newWidth = zonePanelWidth - dragAmount.x
                                zonePanelWidth = newWidth.coerceIn(150f, 500f)
                            }
                        }
                )

                ZonePanel(
                    zoneName = currentZoneName,
                    zoneNotes = currentZoneNotes,
                    onNotesChanged = { newNotes ->
                        clientState.setZoneNotes(currentZoneId, newNotes)
                    },
                    onFocusChanged = { focused ->
                        clientState.setSecondaryTextFieldFocused(focused)
                    },
                    width = with(density) { zonePanelWidth.toDp() }
                )
            }
        } // End of Row

        // Информация о текущей комнате
        if (currentRoomId != null) {
            val currentRoom = rooms[currentRoomId]
            if (currentRoom != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = colorScheme.surface,
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
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Выходы: ${currentRoom.getAvailableDirections().joinToString(", ") { it.russianName }}",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!currentRoom.zone.isNullOrEmpty()) {
                            Text(
                                text = "Зона: ${currentRoom.zone}",
                                color = colorScheme.secondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (currentRoom.notes.isNotEmpty()) {
                            Text(
                                text = "Заметка: ${currentRoom.notes}",
                                color = colorScheme.warning,
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
            color = colorScheme.surface,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Комнат на карте: ${rooms.size}",
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Отображается: ${displayRooms.size}",
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // Диалог редактирования комнаты
    if (showRoomDialog && selectedRoom != null) {
        RoomDetailsDialog(
            room = selectedRoom!!,
            allRooms = rooms,
            onDismiss = { showRoomDialog = false },
            onSave = { name, note, terrain, tags, zone, exits, visited ->
                clientState.updateRoom(
                    roomId = selectedRoom!!.id,
                    name = name,
                    note = note,
                    terrain = terrain,
                    tags = tags,
                    zone = zone,
                    exits = exits,
                    visited = visited
                )
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

    // Диалог перехода к комнате
    if (showGoToRoomDialog) {
        GoToRoomDialog(
            rooms = rooms,
            onDismiss = { showGoToRoomDialog = false },
            onNavigate = { roomId ->
                viewCenterRoomId = roomId
                offsetX = 0f
                offsetY = 0f
                showGoToRoomDialog = false
            }
        )
    }
}

/**
 * Direction navigation pad - 6 directions: N, W, E, S, U, D
 * Layout:
 *   [ ]  [N]  [U]
 *   [W]  [●]  [E]
 *   [ ]  [S]  [D]
 */
@Composable
private fun DirectionPad(
    room: Room,
    allRooms: Map<String, Room>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalAppColorScheme.current
    // Map directions to grid positions: null = empty, "center" marker for current position
    // UP and DOWN use directions with dz != 0
    data class GridCell(val direction: Direction?, val isCenter: Boolean = false, val isEmpty: Boolean = false)

    val directionGrid = listOf(
        listOf(GridCell(null, isEmpty = true), GridCell(Direction.NORTH), GridCell(Direction.UP)),
        listOf(GridCell(Direction.WEST), GridCell(null, isCenter = true), GridCell(Direction.EAST)),
        listOf(GridCell(null, isEmpty = true), GridCell(Direction.SOUTH), GridCell(Direction.DOWN))
    )

    Surface(
        modifier = modifier,
        color = colorScheme.surface.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            directionGrid.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    row.forEach { cell ->
                        when {
                            cell.isCenter -> {
                                // Center cell - current position indicator
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(colorScheme.success, MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("●", color = Color.White)
                                }
                            }
                            cell.isEmpty -> {
                                // Empty cell
                                Box(modifier = Modifier.size(32.dp))
                            }
                            else -> {
                                val direction = cell.direction!!
                                // For UP/DOWN, find any exit with matching dz
                                val exit = if (direction.dz != 0) {
                                    room.exits.entries.find { it.key.dz == direction.dz && it.value.targetRoomId.isNotEmpty() }?.value
                                } else {
                                    room.exits[direction]
                                }
                                val hasExit = exit != null && exit.targetRoomId.isNotEmpty()
                                val targetRoom = if (hasExit) allRooms[exit!!.targetRoomId] else null

                                // Colors for UP/DOWN - keep directional colors for up/down
                                val buttonColor = when {
                                    !hasExit -> colorScheme.divider
                                    direction.dz > 0 -> Color(0xFF00AAAA) // UP - cyan
                                    direction.dz < 0 -> Color(0xFFAA00AA) // DOWN - magenta
                                    else -> colorScheme.primary // N/S/E/W
                                }

                                Button(
                                    onClick = {
                                        if (hasExit && targetRoom != null) {
                                            onNavigate(targetRoom.id)
                                        }
                                    },
                                    enabled = hasExit && targetRoom != null,
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = buttonColor,
                                        disabledContainerColor = colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = direction.shortName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasExit) Color.White else colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for navigating to a room by ID or name
 */
@Composable
private fun GoToRoomDialog(
    rooms: Map<String, Room>,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    var searchQuery by remember { mutableStateOf("") }

    // Filter rooms by search query (match ID or name)
    val filteredRooms = remember(searchQuery, rooms) {
        if (searchQuery.isBlank()) {
            rooms.values.take(50).toList() // Show first 50 rooms when no search
        } else {
            rooms.values.filter { room ->
                room.id.contains(searchQuery, ignoreCase = true) ||
                room.name.contains(searchQuery, ignoreCase = true)
            }.take(50)
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(450.dp)
                .heightIn(max = 500.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Перейти к комнате",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск по ID или названию...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = colorScheme.success,
                        unfocusedBorderColor = Color.Gray,
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray
                    ),
                    singleLine = true
                )

                Text(
                    text = "Найдено: ${filteredRooms.size} комнат",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )

                // Room list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredRooms.forEach { room ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(room.id) },
                            color = colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = room.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "#${room.id}",
                                    color = colorScheme.secondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Панель заметок зоны
 */
@Composable
private fun ZonePanel(
    zoneName: String,
    zoneNotes: String,
    onNotesChanged: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    width: Dp = 220.dp,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalAppColorScheme.current
    var notes by remember(zoneName) { mutableStateOf(zoneNotes) }

    // Обновляем локальное состояние при изменении внешнего
    LaunchedEffect(zoneNotes) {
        notes = zoneNotes
    }

    // Сбрасываем фокус при размонтировании
    DisposableEffect(Unit) {
        onDispose {
            onFocusChanged(false)
        }
    }

    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(colorScheme.surface)
            .padding(8.dp)
    ) {
        // Заголовок зоны
        Text(
            text = zoneName,
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface
        )

        Divider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = colorScheme.divider
        )

        // Поле заметок с дебаунсом для автосохранения
        OutlinedTextField(
            value = notes,
            onValueChange = { newValue ->
                notes = newValue
                onNotesChanged(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onFocusChanged { focusState ->
                    onFocusChanged(focusState.isFocused)
                },
            placeholder = {
                Text(
                    "Заметки о зоне...\n\nПоддерживается **жирный** и *курсив*",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurface),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.border,
                cursorColor = colorScheme.primary
            )
        )

        // Превью markdown
        if (notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Превью:",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp),
                color = colorScheme.background,
                shape = MaterialTheme.shapes.small
            ) {
                Box(modifier = Modifier.padding(4.dp)) {
                    MarkdownText(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}
