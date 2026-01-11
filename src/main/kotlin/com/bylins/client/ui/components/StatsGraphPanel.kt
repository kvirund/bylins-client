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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.stats.StatsHistory

/**
 * Панель с графиками статистики (HP, Mana, Movement)
 */
@Composable
fun StatsGraphPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val hpHistory by clientState.hpHistory.collectAsState()
    val manaHistory by clientState.manaHistory.collectAsState()
    val movementHistory by clientState.movementHistory.collectAsState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Графики статистики",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (hpHistory.isEmpty() && manaHistory.isEmpty() && movementHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет данных для отображения.\nПодключитесь к серверу, чтобы видеть графики.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            // График HP
            GraphCard(
                title = "Health Points (HP)",
                dataPoints = hpHistory,
                color = Color(0xFFFF4444),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // График Mana
            GraphCard(
                title = "Mana Points",
                dataPoints = manaHistory,
                color = Color(0xFF4444FF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // График Movement
            GraphCard(
                title = "Movement Points",
                dataPoints = movementHistory,
                color = Color(0xFF44FF44),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
        }
    }
}

@Composable
private fun GraphCard(
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

        // График
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E1E1E))
        ) {
            if (dataPoints.isEmpty()) return@Canvas

            val width = size.width
            val height = size.height
            val padding = 20f

            // Рисуем сетку
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

            // Рисуем линии процентов
            val labelColor = Color.White.copy(alpha = 0.3f)
            for (i in 0..4) {
                val percentage = 100 - i * 25
                val y = padding + (height - 2 * padding) * i / 4
                // Можно добавить текстовые метки, но это требует TextMeasurer
            }

            // Рисуем график
            if (dataPoints.size < 2) return@Canvas

            val pointsToShow = dataPoints.takeLast(300) // Последние 5 минут
            val step = (width - 2 * padding) / (pointsToShow.size - 1).coerceAtLeast(1)

            val points = pointsToShow.mapIndexed { index, point ->
                val x = padding + index * step
                val y = padding + (height - 2 * padding) * (1 - point.value / 100f)
                Offset(x, y)
            }

            // Рисуем линию графика
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = color,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2f
                )
            }

            // Рисуем точки
            points.forEach { point ->
                drawCircle(
                    color = color,
                    radius = 2f,
                    center = point
                )
            }

            // Рисуем текущее значение (последняя точка выделена)
            if (points.isNotEmpty()) {
                drawCircle(
                    color = color,
                    radius = 4f,
                    center = points.last()
                )
            }
        }
    }
}
