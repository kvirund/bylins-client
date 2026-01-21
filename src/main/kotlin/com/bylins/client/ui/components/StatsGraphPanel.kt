package com.bylins.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.stats.Chart
import com.bylins.client.stats.ChartEvent
import com.bylins.client.stats.ChartSeries
import com.bylins.client.stats.DataPoint
import com.bylins.client.stats.StatsHistory
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Панель с графиками статистики (HP, Mana, Movement + динамические графики)
 */
@Composable
fun StatsGraphPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val hpHistory by clientState.hpHistory.collectAsState()
    val manaHistory by clientState.manaHistory.collectAsState()
    val movementHistory by clientState.movementHistory.collectAsState()
    val dynamicCharts by clientState.dynamicCharts.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Графики статистики",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        val hasBuiltinData = hpHistory.isNotEmpty() || manaHistory.isNotEmpty() || movementHistory.isNotEmpty()
        val hasDynamicCharts = dynamicCharts.isNotEmpty()

        if (!hasBuiltinData && !hasDynamicCharts) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет данных для отображения.\nПодключитесь к серверу, чтобы видеть графики.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            // Встроенные графики (HP, Mana, Movement)
            if (hasBuiltinData) {
                // График HP
                SimpleGraphCard(
                    title = "Health Points (HP)",
                    dataPoints = hpHistory,
                    color = Color(0xFFFF4444),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // График Mana
                SimpleGraphCard(
                    title = "Mana Points",
                    dataPoints = manaHistory,
                    color = Color(0xFF4444FF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // График Movement
                SimpleGraphCard(
                    title = "Movement Points",
                    dataPoints = movementHistory,
                    color = Color(0xFF44FF44),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }

            // Динамические графики (созданные скриптами)
            if (hasDynamicCharts) {
                if (hasBuiltinData) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Графики скриптов",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                dynamicCharts.values.forEach { chart ->
                    DynamicChartCard(
                        chart = chart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chart.height.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Простая карточка графика (для встроенных HP/Mana/Movement)
 */
@Composable
private fun SimpleGraphCard(
    title: String,
    dataPoints: List<StatsHistory.DataPoint>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF2D2D2D))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Color.White
            )

            if (dataPoints.isNotEmpty()) {
                val currentValue = dataPoints.last().value
                Text(
                    text = "${currentValue.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E1E1E))
        ) {
            if (dataPoints.isEmpty()) return@Canvas
            drawSimpleGraph(dataPoints.map { DataPoint(it.timestamp, it.value) }, color)
        }
    }
}

/**
 * Карточка динамического графика (с несколькими сериями и интерактивностью)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DynamicChartCard(
    chart: Chart,
    modifier: Modifier = Modifier
) {
    var mousePosition by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Параметры отступов
    val leftPadding = 45f
    val rightPadding = 45f
    val topPadding = 20f
    val bottomPadding = 25f

    // Определяем временной диапазон
    val allTimestamps = chart.series.values
        .flatMap { it.dataPoints.map { dp -> dp.timestamp } } +
        chart.events.map { it.timestamp }

    val minTime = allTimestamps.minOrNull()
    val maxTime = allTimestamps.maxOrNull()
    val timeRange = if (minTime != null && maxTime != null) {
        (maxTime.epochSecond - minTime.epochSecond).coerceAtLeast(1)
    } else 1L

    // Серии для левой и правой осей
    val leftSeries = chart.series.values.filter { it.yAxis == "left" }
    val rightSeries = chart.series.values.filter { it.yAxis == "right" }

    // Определяем диапазоны значений из настроек серий или авто-масштабирование
    val leftValues = leftSeries.flatMap { it.dataPoints.map { dp -> dp.value } }
    val rightValues = rightSeries.flatMap { it.dataPoints.map { dp -> dp.value } }

    // Левая ось - берём из настроек серии или авто
    val leftSeriesConfig = leftSeries.firstOrNull()
    val leftMin = leftSeriesConfig?.minValue ?: 0f
    val leftMax = leftSeriesConfig?.maxValue ?: run {
        val maxVal = leftValues.maxOrNull() ?: 100f
        maxVal.coerceAtLeast(leftMin + 10f)
    }

    // Правая ось - берём из настроек серии или авто
    val rightSeriesConfig = rightSeries.firstOrNull()
    val rightMin = rightSeriesConfig?.minValue ?: 0f
    val rightMax = rightSeriesConfig?.maxValue ?: run {
        if (rightValues.isEmpty()) 100f else {
            val maxVal = (rightValues.maxOrNull() ?: 100f).coerceAtLeast(10f)
            // Округляем до красивого числа сверху (простая версия)
            val step = when {
                maxVal <= 100 -> 10f
                maxVal <= 1000 -> 100f
                maxVal <= 10000 -> 1000f
                else -> 10000f
            }
            (kotlin.math.ceil((maxVal / step).toDouble()).toFloat() * step).coerceAtLeast(10f)
        }
    }


    // Вычисляем значения под курсором
    val hoverData = remember(mousePosition, canvasSize, chart, minTime, maxTime) {
        if (mousePosition == null || minTime == null || maxTime == null || canvasSize.width == 0f) {
            null
        } else {
            val chartWidth = canvasSize.width - leftPadding - rightPadding
            val relativeX = (mousePosition!!.x - leftPadding) / chartWidth
            if (relativeX < 0 || relativeX > 1) {
                null
            } else {
                val hoverTimestamp = Instant.ofEpochSecond(
                    minTime.epochSecond + (timeRange * relativeX).toLong()
                )

                // Находим ближайшие значения для каждой серии
                // Используем displayValue для хинта если есть, иначе value
                val seriesValues = chart.series.values.mapNotNull { series ->
                    val closest = series.dataPoints.minByOrNull {
                        kotlin.math.abs(it.timestamp.epochSecond - hoverTimestamp.epochSecond)
                    }
                    if (closest != null) {
                        val hasDisplayValue = closest.displayValue != null
                        val value = closest.displayValue ?: closest.value
                        Triple(series, value, hasDisplayValue)
                    } else null
                }

                // Находим события около курсора
                val nearbyEvents = chart.events.filter {
                    kotlin.math.abs(it.timestamp.epochSecond - hoverTimestamp.epochSecond) <= timeRange / 20
                }

                if (seriesValues.isNotEmpty() || nearbyEvents.isNotEmpty()) {
                    HoverData(hoverTimestamp, seriesValues, nearbyEvents)
                } else null
            }
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFF2D2D2D))
            .padding(8.dp)
    ) {
        // Заголовок и легенда
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chart.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Color.White
            )

            // Легенда серий
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chart.series.values.forEach { series ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(parseColor(series.color))
                        )
                        Text(
                            text = series.label,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        if (series.dataPoints.isNotEmpty()) {
                            val lastPoint = series.dataPoints.last()
                            // Если есть displayValue, показываем его (абсолютное значение)
                            // Иначе показываем value с unit
                            val displayText = if (lastPoint.displayValue != null) {
                                "${lastPoint.displayValue.toInt()}"
                            } else {
                                "${lastPoint.value.toInt()}${series.unit}"
                            }
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = parseColor(series.color)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .onPointerEvent(PointerEventType.Move) { event ->
                        mousePosition = event.changes.firstOrNull()?.position
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        mousePosition = null
                    }
            ) {
                canvasSize = size
                drawDynamicChartWithAxes(
                    chart = chart,
                    leftPadding = leftPadding,
                    rightPadding = rightPadding,
                    topPadding = topPadding,
                    bottomPadding = bottomPadding,
                    leftMin = leftMin,
                    leftMax = leftMax,
                    rightMin = rightMin,
                    rightMax = rightMax,
                    hasRightAxis = rightSeries.isNotEmpty(),
                    mouseX = mousePosition?.x
                )
            }

            // Tooltip
            hoverData?.let { data ->
                val tooltipX = mousePosition?.x ?: 0f
                val tooltipOffset = if (tooltipX > canvasSize.width / 2) (-180).dp else 10.dp

                Surface(
                    modifier = Modifier
                        .offset(
                            x = with(androidx.compose.ui.platform.LocalDensity.current) { tooltipX.toDp() } + tooltipOffset,
                            y = 10.dp
                        ),
                    color = Color(0xEE333333),
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                            .withZone(ZoneId.systemDefault())
                        Text(
                            text = timeFormatter.format(data.timestamp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        data.seriesValues.forEach { (series, value, isDisplayValue) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(parseColor(series.color))
                                )
                                // Если это displayValue, не добавляем unit (это абсолютное значение)
                                val displayText = if (isDisplayValue) {
                                    "${series.label}: ${value.toInt()}"
                                } else {
                                    "${series.label}: ${value.toInt()}${series.unit}"
                                }
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = parseColor(series.color)
                                )
                            }
                        }
                        data.nearbyEvents.forEach { event ->
                            Text(
                                text = "▶ ${event.label}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = parseColor(event.color)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Данные для tooltip при наведении
 * @param seriesValues - список троек (серия, значение, isDisplayValue)
 */
private data class HoverData(
    val timestamp: Instant,
    val seriesValues: List<Triple<ChartSeries, Float, Boolean>>,
    val nearbyEvents: List<ChartEvent>
)

/**
 * Рисует простой график с одной линией
 */
private fun DrawScope.drawSimpleGraph(dataPoints: List<DataPoint>, color: Color) {
    val width = size.width
    val height = size.height
    val padding = 20f

    // Рисуем сетку
    drawGrid(width, height, padding)

    if (dataPoints.size < 2) return

    // Рисуем линию графика
    val pointsToShow = dataPoints.takeLast(300)
    val step = (width - 2 * padding) / (pointsToShow.size - 1).coerceAtLeast(1)

    val points = pointsToShow.mapIndexed { index, point ->
        val x = padding + index * step
        val y = padding + (height - 2 * padding) * (1 - point.value / 100f)
        Offset(x, y)
    }

    for (i in 0 until points.size - 1) {
        drawLine(
            color = color,
            start = points[i],
            end = points[i + 1],
            strokeWidth = 2f
        )
    }

    // Выделяем последнюю точку
    if (points.isNotEmpty()) {
        drawCircle(
            color = color,
            radius = 4f,
            center = points.last()
        )
    }
}

/**
 * Рисует динамический график с осями, подписями и поддержкой двух Y-осей
 */
private fun DrawScope.drawDynamicChartWithAxes(
    chart: Chart,
    leftPadding: Float,
    rightPadding: Float,
    topPadding: Float,
    bottomPadding: Float,
    leftMin: Float,
    leftMax: Float,
    rightMin: Float,
    rightMax: Float,
    hasRightAxis: Boolean,
    mouseX: Float?
) {
    val width = size.width
    val height = size.height
    val chartWidth = width - leftPadding - rightPadding
    val chartHeight = height - topPadding - bottomPadding

    val gridColor = Color.White.copy(alpha = 0.1f)
    val textPaint = Paint().apply {
        color = org.jetbrains.skia.Color.makeARGB(180, 255, 255, 255)
    }
    val font = Font(null, 10f)

    // Определяем временной диапазон для всех серий
    val allTimestamps = chart.series.values
        .flatMap { it.dataPoints.map { dp -> dp.timestamp } } +
        chart.events.map { it.timestamp }

    if (allTimestamps.isEmpty()) return

    val minTime = allTimestamps.minOrNull() ?: return
    val maxTime = allTimestamps.maxOrNull() ?: return
    val timeRange = (maxTime.epochSecond - minTime.epochSecond).coerceAtLeast(1)

    // Функция для преобразования времени в x-координату
    fun timestampToX(timestamp: Instant): Float {
        val progress = (timestamp.epochSecond - minTime.epochSecond).toFloat() / timeRange
        return leftPadding + chartWidth * progress
    }

    // Рисуем горизонтальные линии сетки и подписи левой оси Y
    val leftUnit = chart.series.values.firstOrNull { it.yAxis == "left" }?.unit ?: "%"
    val rightUnit = chart.series.values.firstOrNull { it.yAxis == "right" }?.unit ?: ""

    for (i in 0..4) {
        val y = topPadding + chartHeight * i / 4
        drawLine(
            color = gridColor,
            start = Offset(leftPadding, y),
            end = Offset(width - rightPadding, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
        )

        // Подпись левой оси (проценты или другие значения)
        val leftValue = leftMax - (leftMax - leftMin) * i / 4
        val leftText = "${leftValue.toInt()}$leftUnit"
        val leftTextLine = TextLine.Companion.make(leftText, font)
        drawContext.canvas.nativeCanvas.drawTextLine(
            leftTextLine,
            leftPadding - leftTextLine.width - 4,
            y + 4,
            textPaint
        )

        // Подпись правой оси (если есть)
        if (hasRightAxis) {
            val rightValue = rightMax - (rightMax - rightMin) * i / 4
            val rightText = "${rightValue.toInt()}$rightUnit"
            val rightTextLine = TextLine.Companion.make(rightText, font)
            drawContext.canvas.nativeCanvas.drawTextLine(
                rightTextLine,
                width - rightPadding + 4,
                y + 4,
                textPaint
            )
        }
    }

    // Рисуем временные метки на оси X
    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
    for (i in 0..4) {
        val x = leftPadding + chartWidth * i / 4
        val timestamp = Instant.ofEpochSecond(minTime.epochSecond + timeRange * i / 4)
        val timeText = timeFormatter.format(timestamp)
        val timeTextLine = TextLine.Companion.make(timeText, font)
        drawContext.canvas.nativeCanvas.drawTextLine(
            timeTextLine,
            x - timeTextLine.width / 2,
            height - 5,
            textPaint
        )
    }

    // Рисуем события (вертикальные линии)
    chart.events.forEach { event ->
        val x = timestampToX(event.timestamp)
        drawLine(
            color = parseColor(event.color).copy(alpha = 0.5f),
            start = Offset(x, topPadding),
            end = Offset(x, height - bottomPadding),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
        )
    }

    // Рисуем серии
    chart.series.values.forEach seriesLoop@{ series ->
        if (series.dataPoints.isEmpty()) return@seriesLoop

        val color = parseColor(series.color)
        val isLeftAxis = series.yAxis == "left"
        val yMin = if (isLeftAxis) leftMin else rightMin
        val yMax = if (isLeftAxis) leftMax else rightMax
        val yRange = yMax - yMin

        // Базовая линия (y=0 или yMin)
        val baseY = topPadding + chartHeight * (1 - (0f - yMin) / yRange)
            .coerceIn(0f, 1f)

        if (series.chartType == "bar") {
            // Столбчатый график
            val barWidth = (chartWidth / series.dataPoints.size.coerceAtLeast(1) * 0.8f).coerceIn(2f, 20f)

            series.dataPoints.forEach { dp ->
                // Пропускаем нулевые значения
                if (dp.value <= 0.01f) return@forEach

                val x = timestampToX(dp.timestamp)
                val normalizedValue = (dp.value - yMin) / yRange
                val y = topPadding + chartHeight * (1 - normalizedValue)
                val clampedY = y.coerceIn(topPadding, height - bottomPadding)

                // Рисуем столбик от базовой линии до значения
                val top = minOf(clampedY, baseY.coerceIn(topPadding, height - bottomPadding))
                val bottom = maxOf(clampedY, baseY.coerceIn(topPadding, height - bottomPadding))
                val barHeight = bottom - top

                if (barHeight > 0) {
                    drawRect(
                        color = color.copy(alpha = 0.7f),
                        topLeft = Offset(x - barWidth / 2, top),
                        size = Size(barWidth, barHeight)
                    )
                }
            }
        } else {
            // Линейный график
            if (series.dataPoints.size < 2) return@seriesLoop

            val points = series.dataPoints.map { dp ->
                val x = timestampToX(dp.timestamp)
                val normalizedValue = (dp.value - yMin) / yRange
                val y = topPadding + chartHeight * (1 - normalizedValue)
                Offset(x, y.coerceIn(topPadding, height - bottomPadding))
            }

            // Рисуем линию
            val pathEffect = when (series.lineStyle) {
                "dashed" -> PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                "dotted" -> PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
                else -> null
            }

            for (i in 0 until points.size - 1) {
                drawLine(
                    color = color,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2f,
                    pathEffect = pathEffect
                )
            }

            // Рисуем точки на всех значениях
            points.forEach { point ->
                drawCircle(
                    color = color,
                    radius = 3f,
                    center = point
                )
            }
        }
    }

    // Вертикальная линия под курсором
    mouseX?.let { mx ->
        if (mx >= leftPadding && mx <= width - rightPadding) {
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(mx, topPadding),
                end = Offset(mx, height - bottomPadding),
                strokeWidth = 1f
            )
        }
    }
}

/**
 * Рисует динамический график с несколькими сериями и событиями (deprecated, use drawDynamicChartWithAxes)
 */
private fun DrawScope.drawDynamicChart(chart: Chart) {
    val width = size.width
    val height = size.height
    val padding = 20f

    // Рисуем сетку
    drawGrid(width, height, padding)

    // Определяем временной диапазон для всех серий
    val allTimestamps = chart.series.values
        .flatMap { it.dataPoints.map { dp -> dp.timestamp } } +
        chart.events.map { it.timestamp }

    if (allTimestamps.isEmpty()) return

    val minTime = allTimestamps.minOrNull() ?: return
    val maxTime = allTimestamps.maxOrNull() ?: return
    val timeRange = (maxTime.epochSecond - minTime.epochSecond).coerceAtLeast(1)

    // Функция для преобразования времени в x-координату
    fun timestampToX(timestamp: Instant): Float {
        val progress = (timestamp.epochSecond - minTime.epochSecond).toFloat() / timeRange
        return padding + (width - 2 * padding) * progress
    }

    // Рисуем события (вертикальные линии)
    chart.events.forEach { event ->
        val x = timestampToX(event.timestamp)
        drawLine(
            color = parseColor(event.color).copy(alpha = 0.5f),
            start = Offset(x, padding),
            end = Offset(x, height - padding),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
        )
    }

    // Рисуем серии
    chart.series.values.forEach { series ->
        if (series.dataPoints.size < 2) return@forEach

        val color = parseColor(series.color)
        val points = series.dataPoints.map { dp ->
            val x = timestampToX(dp.timestamp)
            val y = padding + (height - 2 * padding) * (1 - dp.value / 100f)
            Offset(x, y)
        }

        // Рисуем линию
        val pathEffect = when (series.lineStyle) {
            "dashed" -> PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
            "dotted" -> PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
            else -> null
        }

        for (i in 0 until points.size - 1) {
            drawLine(
                color = color,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 2f,
                pathEffect = pathEffect
            )
        }

        // Выделяем последнюю точку
        if (points.isNotEmpty()) {
            drawCircle(
                color = color,
                radius = 4f,
                center = points.last()
            )
        }
    }
}

/**
 * Рисует сетку графика
 */
private fun DrawScope.drawGrid(width: Float, height: Float, padding: Float) {
    val gridColor = Color.White.copy(alpha = 0.1f)
    for (i in 0..4) {
        val y = padding + (height - 2 * padding) * i / 4
        drawLine(
            color = gridColor,
            start = Offset(padding, y),
            end = Offset(width - padding, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
        )
    }
}

/**
 * Парсит цвет из строки
 */
private fun parseColor(colorStr: String): Color {
    return when (colorStr.lowercase()) {
        "red" -> Color(0xFFFF4444)
        "green" -> Color(0xFF44FF44)
        "blue" -> Color(0xFF4444FF)
        "yellow" -> Color(0xFFFFFF44)
        "orange" -> Color(0xFFFF8844)
        "purple" -> Color(0xFF8844FF)
        "cyan" -> Color(0xFF44FFFF)
        "magenta" -> Color(0xFFFF44FF)
        "white" -> Color.White
        "gray", "grey" -> Color.Gray
        else -> {
            // Попробуем распарсить как hex
            try {
                val hex = colorStr.removePrefix("#")
                val longColor = hex.toLong(16)
                if (hex.length == 6) {
                    Color(0xFF000000 or longColor)
                } else {
                    Color(longColor)
                }
            } catch (e: Exception) {
                Color.White
            }
        }
    }
}
