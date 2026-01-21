package com.bylins.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.mapper.Direction
import com.bylins.client.mapper.Room
import com.bylins.client.ui.theme.ColorScheme
import com.bylins.client.ui.theme.LocalAppColorScheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
 * Theme colors needed for map rendering in DrawScope.
 * Since DrawScope cannot access composable locals, these colors must be passed as parameters.
 */
data class MapRenderColors(
    val success: Color,           // Current room (green)
    val warning: Color,           // Path/notes/gold highlights
    val secondary: Color,         // Info highlights (cyan/blue)
    val primary: Color,           // Visited rooms
    val border: Color,            // Connection lines
    val divider: Color,           // Alternative for border
    val surface: Color,           // Background fills
    val onSurfaceVariant: Color   // Unexplored/gray elements
) {
    companion object {
        /**
         * Create MapRenderColors from a ColorScheme
         */
        fun fromColorScheme(colorScheme: ColorScheme): MapRenderColors {
            return MapRenderColors(
                success = colorScheme.success,
                warning = colorScheme.warning,
                secondary = colorScheme.secondary,
                primary = colorScheme.primary,
                border = colorScheme.border,
                divider = colorScheme.divider,
                surface = colorScheme.surface,
                onSurfaceVariant = colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Context for room rendering
 */
data class RoomRenderContext(
    val currentRoomId: String?,       // Room where the player is
    val viewCenterRoomId: String?,    // Room from which the map is drawn
    val currentZone: String?,
    val hoveredRoomId: String?,
    val roomSize: Float,
    val zoom: Float,
    val allRooms: Map<String, Room>,
    val colors: MapRenderColors,                     // Theme colors for rendering
    val pathRoomIds: Set<String> = emptySet(),      // Rooms on the active path
    val pathTargetRoomId: String? = null,           // Final destination of the path
    val centerGridX: Int = 0,                        // Grid X of view center for distance fading
    val centerGridY: Int = 0                         // Grid Y of view center for distance fading
)

/**
 * Interface for room rendering strategies
 */
interface RoomRenderer {
    fun DrawScope.drawRoom(
        info: RoomDisplayInfo,
        context: RoomRenderContext
    )

    fun DrawScope.drawConnection(
        from: RoomDisplayInfo,
        to: RoomDisplayInfo?,
        direction: Direction,
        exit: com.bylins.client.mapper.Exit,
        context: RoomRenderContext
    )
}

/**
 * Default room renderer with terrain colors, zone borders, one-way arrows
 */
object DefaultRoomRenderer : RoomRenderer {
    override fun DrawScope.drawRoom(
        info: RoomDisplayInfo,
        context: RoomRenderContext
    ) {
        val room = info.room
        val isCurrentRoom = room.id == context.currentRoomId  // Where player is
        val isViewCenter = room.id == context.viewCenterRoomId  // From which map is drawn
        val isHovered = room.id == context.hoveredRoomId
        val isSameZone = context.currentZone == null || room.zone == context.currentZone
        val isOnPath = room.id in context.pathRoomIds  // Is this room on the active path?
        val isPathTarget = room.id == context.pathTargetRoomId  // Is this the destination?
        val roomSize = context.roomSize
        val zoom = context.zoom

        // Calculate distance-based alpha fading
        // Use Chebyshev distance (max of |dx|, |dy|)
        val dx = abs(info.gridX - context.centerGridX)
        val dy = abs(info.gridY - context.centerGridY)
        val distance = maxOf(dx, dy)
        // Fade from 1.0 at center to 0.4 at distance 8+
        // Formula: alpha = max(0.4, 1.0 - distance * 0.08)
        val distanceAlpha = (1.0f - distance * 0.08f).coerceIn(0.4f, 1.0f)
        // Current room and path rooms are always fully visible
        val effectiveAlpha = if (isCurrentRoom || isOnPath || isPathTarget || isHovered) 1.0f else distanceAlpha

        val colors = context.colors

        // Room color - use terrain color, then custom color, then default
        val baseRoomColor = when {
            isCurrentRoom -> colors.success
            room.color != null -> parseHexColor(room.color) ?: getTerrainOrDefaultColor(room, colors)
            else -> getTerrainOrDefaultColor(room, colors)
        }
        val roomColor = baseRoomColor.copy(alpha = baseRoomColor.alpha * effectiveAlpha)

        // Path target highlight (gold glow)
        if (isPathTarget && !isCurrentRoom) {
            drawRect(
                color = colors.warning.copy(alpha = 0.4f),
                topLeft = Offset(info.screenX - roomSize / 2 - 6f * zoom, info.screenY - roomSize / 2 - 6f * zoom),
                size = Size(roomSize + 12f * zoom, roomSize + 12f * zoom)
            )
            drawRect(
                color = colors.warning,
                topLeft = Offset(info.screenX - roomSize / 2 - 4f * zoom, info.screenY - roomSize / 2 - 4f * zoom),
                size = Size(roomSize + 8f * zoom, roomSize + 8f * zoom),
                style = Stroke(width = 2f * zoom)
            )
        }
        // Path room highlight (cyan glow)
        else if (isOnPath && !isCurrentRoom) {
            drawRect(
                color = colors.secondary.copy(alpha = 0.3f),
                topLeft = Offset(info.screenX - roomSize / 2 - 4f * zoom, info.screenY - roomSize / 2 - 4f * zoom),
                size = Size(roomSize + 8f * zoom, roomSize + 8f * zoom)
            )
        }

        // Hover highlight
        if (isHovered) {
            drawRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = Offset(info.screenX - roomSize / 2 - 4f * zoom, info.screenY - roomSize / 2 - 4f * zoom),
                size = Size(roomSize + 8f * zoom, roomSize + 8f * zoom)
            )
        }

        // Border style based on zone
        val borderStroke = if (isSameZone) {
            Stroke(width = if (isCurrentRoom) 3f * zoom else 2f * zoom)
        } else {
            Stroke(
                width = if (isCurrentRoom) 3f * zoom else 2f * zoom,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * zoom, 2f * zoom), 0f)
            )
        }

        // Double border for VIEW CENTER room (from which map is drawn)
        if (isViewCenter && !isCurrentRoom) {
            // Outer border (secondary color, semi-transparent)
            drawRect(
                color = colors.secondary.copy(alpha = 0.6f),
                topLeft = Offset(info.screenX - roomSize / 2 - 3f * zoom, info.screenY - roomSize / 2 - 3f * zoom),
                size = Size(roomSize + 6f * zoom, roomSize + 6f * zoom),
                style = Stroke(width = 2f * zoom)
            )
        }

        // Room square border (inner)
        drawRect(
            color = roomColor,
            topLeft = Offset(info.screenX - roomSize / 2, info.screenY - roomSize / 2),
            size = Size(roomSize, roomSize),
            style = borderStroke
        )

        // Fill for current room or rooms with terrain/color
        if (isCurrentRoom) {
            drawRect(
                color = roomColor.copy(alpha = 0.5f),
                topLeft = Offset(info.screenX - roomSize / 2, info.screenY - roomSize / 2),
                size = Size(roomSize, roomSize)
            )
        } else if (room.terrain != null || room.color != null) {
            drawRect(
                color = roomColor.copy(alpha = 0.25f),
                topLeft = Offset(info.screenX - roomSize / 2, info.screenY - roomSize / 2),
                size = Size(roomSize, roomSize)
            )
        }

        // Question mark for UNVISITED rooms
        if (!room.visited) {
            drawQuestionMarkInRoom(info.screenX, info.screenY, roomSize, zoom, colors.warning)
        }

        // Note indicator
        if (room.notes.isNotEmpty()) {
            drawCircle(
                color = colors.warning,
                radius = 4f * zoom,
                center = Offset(info.screenX + roomSize / 2 - 5f * zoom, info.screenY - roomSize / 2 + 5f * zoom)
            )
        }

        // Up/Down exit indicators with path highlighting
        val upExit = room.exits.entries.find { it.key.dz > 0 }
        val downExit = room.exits.entries.find { it.key.dz < 0 }

        if (upExit != null) {
            val cx = info.screenX
            val cy = info.screenY - roomSize / 2 + 5f * zoom
            // Check if up exit is on path
            val upTargetOnPath = upExit.value.targetRoomId in context.pathRoomIds ||
                                 upExit.value.targetRoomId == context.pathTargetRoomId
            val roomOnPath = room.id in context.pathRoomIds || room.id == context.currentRoomId
            val isUpOnPath = roomOnPath && upTargetOnPath
            // Keep directional cyan for up, but use warning color when on path
            val upColor = if (isUpOnPath) colors.warning else Color(0xFF00DDDD)
            val upWidth = if (isUpOnPath) 4f * zoom else 2f * zoom
            drawLine(upColor, Offset(cx - 4f * zoom, cy + 3f * zoom), Offset(cx, cy - 2f * zoom), upWidth)
            drawLine(upColor, Offset(cx, cy - 2f * zoom), Offset(cx + 4f * zoom, cy + 3f * zoom), upWidth)
        }

        if (downExit != null) {
            val cx = info.screenX
            val cy = info.screenY + roomSize / 2 - 5f * zoom
            // Check if down exit is on path
            val downTargetOnPath = downExit.value.targetRoomId in context.pathRoomIds ||
                                   downExit.value.targetRoomId == context.pathTargetRoomId
            val roomOnPath = room.id in context.pathRoomIds || room.id == context.currentRoomId
            val isDownOnPath = roomOnPath && downTargetOnPath
            // Keep directional magenta for down, but use warning color when on path
            val downColor = if (isDownOnPath) colors.warning else Color(0xFFDD00DD)
            val downWidth = if (isDownOnPath) 4f * zoom else 2f * zoom
            drawLine(downColor, Offset(cx - 4f * zoom, cy - 3f * zoom), Offset(cx, cy + 2f * zoom), downWidth)
            drawLine(downColor, Offset(cx, cy + 2f * zoom), Offset(cx + 4f * zoom, cy - 3f * zoom), downWidth)
        }
    }

    /**
     * Draws a question mark inside an unvisited room
     */
    private fun DrawScope.drawQuestionMarkInRoom(x: Float, y: Float, roomSize: Float, zoom: Float, color: Color) {
        val size = roomSize * 0.25f
        // Top arc of ?
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(x - size, y - size * 1.2f),
            size = Size(size * 2, size * 1.5f),
            style = Stroke(width = 2f * zoom)
        )
        // Stem
        drawLine(
            color = color,
            start = Offset(x, y),
            end = Offset(x, y + size * 0.4f),
            strokeWidth = 2f * zoom
        )
        // Dot
        drawCircle(
            color = color,
            radius = 1.5f * zoom,
            center = Offset(x, y + size * 1.1f)
        )
    }

    override fun DrawScope.drawConnection(
        from: RoomDisplayInfo,
        to: RoomDisplayInfo?,
        direction: Direction,
        exit: com.bylins.client.mapper.Exit,
        context: RoomRenderContext
    ) {
        val roomSize = context.roomSize
        val zoom = context.zoom
        val colors = context.colors
        val isExplored = exit.targetRoomId.isNotEmpty()

        // Calculate distance-based alpha for connections
        val dx = abs(from.gridX - context.centerGridX)
        val dy = abs(from.gridY - context.centerGridY)
        val distance = maxOf(dx, dy)
        val distanceAlpha = (1.0f - distance * 0.08f).coerceIn(0.4f, 1.0f)

        // Check if this connection is on the active path
        val isOnPath = from.room.id in context.pathRoomIds || from.room.id == context.currentRoomId
        val targetOnPath = exit.targetRoomId in context.pathRoomIds || exit.targetRoomId == context.pathTargetRoomId
        val isPathConnection = isOnPath && targetOnPath
        val effectiveAlpha = if (isPathConnection) 1.0f else distanceAlpha

        if (to != null) {
            // For cardinal directions (dz == 0), check if we can draw a straight line
            if (direction.dz == 0) {
                // For N/S: rooms must be on same vertical (same gridX)
                // For E/W: rooms must be on same horizontal (same gridY)
                val isVerticalDirection = direction.dy != 0  // N or S
                val isHorizontalDirection = direction.dx != 0  // E or W

                val canDrawStraightLine = when {
                    isVerticalDirection -> from.gridX == to.gridX  // Same column
                    isHorizontalDirection -> from.gridY == to.gridY  // Same row
                    else -> false
                }

                if (!canDrawStraightLine) {
                    // Rooms are diagonal - draw dangling exit instead
                    drawDanglingExitInternal(
                        from = from,
                        direction = direction,
                        context = context,
                        isExplored = true,
                        isOnPath = isPathConnection,
                        effectiveAlpha = effectiveAlpha
                    )
                    return
                }
            }

            // Rooms are aligned (or diagonal exit like NE/SW) - draw connection
            val startX = from.screenX + direction.dx * roomSize / 2
            val startY = from.screenY + direction.dy * roomSize / 2
            val endX = to.screenX - direction.dx * roomSize / 2
            val endY = to.screenY - direction.dy * roomSize / 2

            // Path connections are highlighted in warning color and thicker
            val baseLineColor = if (isPathConnection) colors.warning else colors.border
            val lineColor = baseLineColor.copy(alpha = baseLineColor.alpha * effectiveAlpha)
            val lineWidth = if (isPathConnection) 4f * zoom else 2f * zoom

            drawLine(
                color = lineColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = lineWidth
            )

            // Draw arrow for one-way exits
            if (isOneWayExit(from.room, direction, exit, context.allRooms)) {
                drawArrowInternal(startX, startY, endX, endY, colors.warning.copy(alpha = effectiveAlpha), zoom)
            }
        } else if (direction.dz == 0) {
            // Dangling exit (explored but target not displayed, or unexplored)
            val isDanglingOnPath = isOnPath && targetOnPath
            val danglingAlpha = if (isDanglingOnPath) 1.0f else effectiveAlpha

            drawDanglingExitInternal(
                from = from,
                direction = direction,
                context = context,
                isExplored = isExplored,
                isOnPath = isDanglingOnPath,
                effectiveAlpha = danglingAlpha
            )
        }
    }

    private fun DrawScope.drawArrowInternal(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        color: Color, zoom: Float
    ) {
        val arrowSize = 8f * zoom
        val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
        val arrowAngle = Math.toRadians(25.0)

        val x1 = endX - arrowSize * cos(angle - arrowAngle).toFloat()
        val y1 = endY - arrowSize * sin(angle - arrowAngle).toFloat()
        val x2 = endX - arrowSize * cos(angle + arrowAngle).toFloat()
        val y2 = endY - arrowSize * sin(angle + arrowAngle).toFloat()

        val path = Path().apply {
            moveTo(endX, endY)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }
        drawPath(path, color)
    }

    /**
     * Draws a dangling exit (short line with circle at end).
     * Used for unexplored exits, off-canvas targets, and off-grid targets.
     */
    private fun DrawScope.drawDanglingExitInternal(
        from: RoomDisplayInfo,
        direction: Direction,
        context: RoomRenderContext,
        isExplored: Boolean,
        isOnPath: Boolean,
        effectiveAlpha: Float
    ) {
        val roomSize = context.roomSize
        val zoom = context.zoom
        val colors = context.colors

        val startX = from.screenX + direction.dx * roomSize / 2
        val startY = from.screenY + direction.dy * roomSize / 2
        val exitLength = roomSize * 0.4f
        val endX = startX + direction.dx * exitLength
        val endY = startY + direction.dy * exitLength

        val baseExitColor = when {
            isOnPath -> colors.warning
            isExplored -> colors.onSurfaceVariant
            else -> colors.warning
        }
        val exitColor = baseExitColor.copy(alpha = baseExitColor.alpha * effectiveAlpha)
        val exitWidth = if (isOnPath) 5f * zoom else 3f * zoom

        drawLine(
            color = exitColor,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = exitWidth,
            pathEffect = if (isOnPath) null else PathEffect.dashPathEffect(floatArrayOf(4f * zoom, 2f * zoom), 0f)
        )

        drawCircle(
            color = exitColor,
            radius = if (isOnPath) 6f * zoom else 4f * zoom,
            center = Offset(endX, endY)
        )

        if (isOnPath) {
            drawArrowInternal(startX, startY, endX, endY, exitColor, zoom)
        }
    }
}

/**
 * Simple room renderer - minimal style without terrain colors
 */
object SimpleRoomRenderer : RoomRenderer {
    override fun DrawScope.drawRoom(
        info: RoomDisplayInfo,
        context: RoomRenderContext
    ) {
        val room = info.room
        val isCurrentRoom = room.id == context.currentRoomId
        val roomSize = context.roomSize
        val zoom = context.zoom
        val colors = context.colors

        val roomColor = if (isCurrentRoom) colors.success else colors.primary

        drawRect(
            color = roomColor,
            topLeft = Offset(info.screenX - roomSize / 2, info.screenY - roomSize / 2),
            size = Size(roomSize, roomSize),
            style = Stroke(width = 2f * zoom)
        )

        if (isCurrentRoom) {
            drawRect(
                color = roomColor.copy(alpha = 0.3f),
                topLeft = Offset(info.screenX - roomSize / 2, info.screenY - roomSize / 2),
                size = Size(roomSize, roomSize)
            )
        }
    }

    override fun DrawScope.drawConnection(
        from: RoomDisplayInfo,
        to: RoomDisplayInfo?,
        direction: Direction,
        exit: com.bylins.client.mapper.Exit,
        context: RoomRenderContext
    ) {
        val roomSize = context.roomSize
        val zoom = context.zoom
        val colors = context.colors

        if (to != null) {
            val startX = from.screenX + direction.dx * roomSize / 2
            val startY = from.screenY + direction.dy * roomSize / 2
            val endX = to.screenX - direction.dx * roomSize / 2
            val endY = to.screenY - direction.dy * roomSize / 2

            drawLine(
                color = colors.border,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2f * zoom
            )
        }
    }
}

/**
 * Результат расчёта позиций комнат
 */
data class RoomPositionsResult(
    val displayRooms: Map<String, RoomDisplayInfo>,
    val startRoomVisible: Boolean,
    val startRoomDirection: Pair<Float, Float>?  // Normalized direction to start room if off-canvas
)

/**
 * Вычисляет позиции комнат для отображения используя BFS от стартовой комнаты.
 * Комнаты размещаются на сетке, позиция определяется направлением от родителя.
 * Комнаты за границами канвы пропускаются, но их соседи всё равно обрабатываются.
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
): RoomPositionsResult {
    val result = mutableMapOf<String, RoomDisplayInfo>()

    if (startRoomId == null) return RoomPositionsResult(result, true, null)
    rooms[startRoomId] ?: return RoomPositionsResult(result, true, null)

    // Padding for rooms and dangling exits (room half + exit length + buffer)
    val margin = roomSize * 1.2f

    // Проверяем, видима ли стартовая комната
    val startScreenX = centerX
    val startScreenY = centerY
    val startRoomVisible = startScreenX >= margin && startScreenX <= canvasWidth - margin &&
                           startScreenY >= margin && startScreenY <= canvasHeight - margin

    // Вычисляем направление к стартовой комнате, если она за канвой
    val startRoomDirection = if (!startRoomVisible) {
        val canvasCenterX = canvasWidth / 2
        val canvasCenterY = canvasHeight / 2
        val dx = startScreenX - canvasCenterX
        val dy = startScreenY - canvasCenterY
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len > 0) Pair(dx / len, dy / len) else null
    } else null

    // Занятые позиции на сетке
    val occupiedPositions = mutableSetOf<Pair<Int, Int>>()

    // BFS очередь: roomId, gridX, gridY
    val queue = ArrayDeque<Triple<String, Int, Int>>()
    queue.add(Triple(startRoomId, 0, 0))

    val visited = mutableSetOf<String>()

    while (queue.isNotEmpty()) {
        val (roomId, gridX, gridY) = queue.removeFirst()

        if (roomId in visited) continue
        visited.add(roomId)

        val room = rooms[roomId] ?: continue

        // Проверяем занята ли позиция
        val gridPos = Pair(gridX, gridY)
        val positionOccupied = gridPos in occupiedPositions

        // Вычисляем экранную позицию
        val screenX = centerX + gridX * roomSpacing
        val screenY = centerY + gridY * roomSpacing

        // Проверяем границы канвы
        val isOnCanvas = screenX >= margin && screenX <= canvasWidth - margin &&
                         screenY >= margin && screenY <= canvasHeight - margin

        // Добавляем в результат только если на канве и позиция свободна
        if (isOnCanvas && !positionOccupied) {
            occupiedPositions.add(gridPos)
            result[roomId] = RoomDisplayInfo(room, screenX, screenY, gridX, gridY)
        }

        // ВСЕГДА добавляем соседние комнаты в очередь (даже если текущая за границами)
        // НО только горизонтальные выходы (dz == 0), чтобы не смешивать этажи
        for ((direction, exit) in room.exits) {
            if (direction.dz == 0 && exit.targetRoomId.isNotEmpty() && exit.targetRoomId !in visited) {
                val newGridX = gridX + direction.dx
                val newGridY = gridY + direction.dy
                queue.add(Triple(exit.targetRoomId, newGridX, newGridY))
            }
        }
    }

    return RoomPositionsResult(result, startRoomVisible, startRoomDirection)
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
 * Draws an arrow at the end of a line
 */
private fun DrawScope.drawArrow(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    color: Color,
    zoom: Float
) {
    val arrowSize = 8f * zoom
    val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())

    val arrowAngle = Math.toRadians(25.0)
    val x1 = endX - arrowSize * cos(angle - arrowAngle).toFloat()
    val y1 = endY - arrowSize * sin(angle - arrowAngle).toFloat()
    val x2 = endX - arrowSize * cos(angle + arrowAngle).toFloat()
    val y2 = endY - arrowSize * sin(angle + arrowAngle).toFloat()

    val path = Path().apply {
        moveTo(endX, endY)
        lineTo(x1, y1)
        lineTo(x2, y2)
        close()
    }
    drawPath(path, color)
}

/**
 * Checks if exit is one-way (no return exit)
 */
private fun isOneWayExit(
    room: Room,
    direction: Direction,
    exit: com.bylins.client.mapper.Exit,
    allRooms: Map<String, Room>
): Boolean {
    if (exit.targetRoomId.isEmpty()) return false

    val targetRoom = allRooms[exit.targetRoomId] ?: return false
    val oppositeDir = direction.getOpposite()
    val returnExit = targetRoom.exits[oppositeDir]

    return returnExit == null || returnExit.targetRoomId != room.id
}

/**
 * Рисует карту комнат используя указанный рендерер
 */
fun DrawScope.drawMap(
    displayRooms: Map<String, RoomDisplayInfo>,
    allRooms: Map<String, Room>,
    currentRoomId: String?,
    viewCenterRoomId: String? = null,
    currentZone: String? = null,
    hoveredRoomId: String?,
    roomSize: Float,
    zoom: Float = 1f,
    pathRoomIds: Set<String> = emptySet(),
    pathTargetRoomId: String? = null,
    colors: MapRenderColors,
    renderer: RoomRenderer = DefaultRoomRenderer
) {
    // Find center room's grid coordinates for distance fading
    val effectiveCenterId = viewCenterRoomId ?: currentRoomId
    val centerInfo = effectiveCenterId?.let { displayRooms[it] }
    val centerGridX = centerInfo?.gridX ?: 0
    val centerGridY = centerInfo?.gridY ?: 0

    val context = RoomRenderContext(
        currentRoomId = currentRoomId,
        viewCenterRoomId = viewCenterRoomId,
        currentZone = currentZone,
        hoveredRoomId = hoveredRoomId,
        roomSize = roomSize,
        zoom = zoom,
        allRooms = allRooms,
        colors = colors,
        pathRoomIds = pathRoomIds,
        pathTargetRoomId = pathTargetRoomId,
        centerGridX = centerGridX,
        centerGridY = centerGridY
    )

    // 1. Draw connections between rooms
    displayRooms.values.forEach { info ->
        info.room.exits.forEach { (direction, exit) ->
            val isExplored = exit.targetRoomId.isNotEmpty()
            val targetInfo = if (isExplored) displayRooms[exit.targetRoomId] else null
            with(renderer) {
                drawConnection(info, targetInfo, direction, exit, context)
            }
        }
    }

    // 2. Draw rooms
    displayRooms.values.forEach { info ->
        with(renderer) {
            drawRoom(info, context)
        }
    }
}

/**
 * Gets terrain color or default color for a room
 */
private fun getTerrainOrDefaultColor(room: Room, colors: MapRenderColors): Color {
    return room.terrain?.let { TerrainColors.getColor(it) }
        ?: getDefaultRoomColor(false, room.visited, colors)
}

private fun getDefaultRoomColor(isCurrentRoom: Boolean, visited: Boolean, colors: MapRenderColors): Color {
    return when {
        isCurrentRoom -> colors.success       // Текущая - зелёная (success)
        visited -> colors.primary             // Посещённая - синяя (primary)
        else -> colors.onSurfaceVariant       // Непосещённая - серая
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
    zoneNotes: String = "",
    zoneNames: Map<String, String> = emptyMap(),
    @Suppress("UNUSED_PARAMETER") maxWidth: Int = 300,
    canvasWidth: Float = 0f,
    canvasHeight: Float = 0f
) {
    val colorScheme = LocalAppColorScheme.current
    val hasZoneNotes = zoneNotes.isNotBlank()

    // Умное позиционирование
    val padding = 10f

    // Сначала рендерим в Box чтобы получить реальный размер, затем позиционируем
    var tooltipSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    // Вычисляем позицию на основе реального или оценочного размера
    val estimatedWidth = if (tooltipSize.width > 0) tooltipSize.width else 200f
    val estimatedHeight = if (tooltipSize.height > 0) tooltipSize.height else 100f

    // Определяем позицию - предпочитаем справа-снизу, но адаптируемся к границам
    val tooltipX = when {
        canvasWidth <= 0 -> mouseX + padding
        mouseX + padding + estimatedWidth <= canvasWidth -> mouseX + padding  // Справа влезает
        mouseX - padding - estimatedWidth >= 0 -> mouseX - padding - estimatedWidth  // Слева влезает
        else -> (canvasWidth - estimatedWidth - 5f).coerceAtLeast(5f)  // Центрируем
    }

    val tooltipY = when {
        canvasHeight <= 0 -> mouseY + padding
        mouseY + padding + estimatedHeight <= canvasHeight -> mouseY + padding  // Снизу влезает
        mouseY - padding - estimatedHeight >= 0 -> mouseY - padding - estimatedHeight  // Сверху влезает
        else -> (canvasHeight - estimatedHeight - 5f).coerceAtLeast(5f)  // Центрируем
    }

    Surface(
        modifier = Modifier
            .offset { IntOffset(tooltipX.roundToInt(), tooltipY.roundToInt()) }
            .widthIn(max = 280.dp)  // Max width but can be smaller
            .onGloballyPositioned { coordinates ->
                tooltipSize = androidx.compose.ui.geometry.Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            },
        color = colorScheme.surface.copy(alpha = 0.97f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Название + (ID)
            Text(
                text = "${room.name} (${room.id})",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colorScheme.onSurface
            )

            // Выходы
            if (room.exits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                room.exits.forEach { (dir, exit) ->
                    val isExplored = exit.targetRoomId.isNotEmpty()
                    val targetRoom = if (isExplored) allRooms[exit.targetRoomId] else null
                    val targetName = when {
                        !isExplored -> "???"
                        targetRoom != null -> targetRoom.name
                        else -> "(за пределами)"
                    }
                    val color = when {
                        !isExplored -> colorScheme.warning
                        targetRoom != null -> colorScheme.success
                        else -> colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "${dir.shortName} → $targetName",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = color
                    )
                }
            }

            // ZoneName (ZoneID) | Terrain
            val zoneName = room.zone?.let { zoneNames[it] }
            val hasZoneName = !zoneName.isNullOrEmpty()
            val hasZoneId = !room.zone.isNullOrEmpty()
            val hasTerrain = !room.terrain.isNullOrEmpty()
            if (hasZoneName || hasZoneId || hasTerrain) {
                Spacer(modifier = Modifier.height(2.dp))
                val parts = mutableListOf<String>()
                if (hasZoneName && hasZoneId) parts.add("$zoneName (${room.zone})")
                else if (hasZoneName) parts.add(zoneName!!)
                else if (hasZoneId) parts.add(room.zone!!)
                if (hasTerrain) parts.add(room.terrain!!)

                Text(
                    text = parts.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = colorScheme.secondary
                )
            }

            // Заметка комнаты
            if (room.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = room.notes.take(150) + if (room.notes.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = colorScheme.warning
                )
            }

            // Теги
            if (room.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Теги: ${room.tags.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = Color(0xFFCC88FF)
                )
            }

            // Заметки зоны - в отдельном блоке с разделителем
            if (hasZoneNotes) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colorScheme.divider.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Зона: " + zoneNotes.take(150) + if (zoneNotes.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
