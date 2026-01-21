package com.bylins.client.stats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger("ChartManager")

/**
 * Менеджер динамических графиков.
 * Позволяет скриптам создавать произвольные графики с несколькими сериями данных.
 */
class ChartManager(
    private val maxDataPoints: Int = 300 // ~5 минут при обновлении каждую секунду
) {
    private val _charts = MutableStateFlow<Map<String, Chart>>(emptyMap())
    val charts: StateFlow<Map<String, Chart>> = _charts

    /**
     * Создать график
     * @param id уникальный идентификатор графика
     * @param options настройки: title, height
     */
    fun createChart(id: String, options: Map<String, Any>) {
        val title = options["title"]?.toString() ?: id
        val height = (options["height"] as? Number)?.toInt() ?: 150

        val chart = Chart(
            id = id,
            title = title,
            height = height,
            series = mutableMapOf(),
            events = mutableListOf()
        )

        _charts.value = _charts.value + (id to chart)
        logger.debug { "Created chart: $id ($title)" }
    }

    /**
     * Удалить график
     */
    fun removeChart(id: String) {
        _charts.value = _charts.value - id
        logger.debug { "Removed chart: $id" }
    }

    /**
     * Очистить данные графика (серии и события)
     */
    fun clearChart(id: String) {
        val chart = _charts.value[id] ?: return
        val clearedChart = chart.copy(
            series = chart.series.mapValues { (_, series) ->
                series.copy(dataPoints = mutableListOf())
            }.toMutableMap(),
            events = mutableListOf()
        )
        _charts.value = _charts.value + (id to clearedChart)
        logger.debug { "Cleared chart: $id" }
    }

    /**
     * Добавить серию данных на график
     * @param chartId ID графика
     * @param seriesId ID серии
     * @param options настройки: label, color, lineStyle
     */
    fun addSeries(chartId: String, seriesId: String, options: Map<String, Any>) {
        val chart = _charts.value[chartId] ?: return

        val label = options["label"]?.toString() ?: seriesId
        val color = options["color"]?.toString() ?: "white"
        val lineStyle = options["lineStyle"]?.toString() ?: "solid"
        val chartType = options["chartType"]?.toString() ?: "line"
        val yAxis = options["yAxis"]?.toString() ?: "left"
        val unit = options["unit"]?.toString() ?: "%"
        val minValue = (options["minValue"] as? Number)?.toFloat()
        val maxValue = (options["maxValue"] as? Number)?.toFloat()

        val series = ChartSeries(
            id = seriesId,
            label = label,
            color = color,
            lineStyle = lineStyle,
            chartType = chartType,
            yAxis = yAxis,
            unit = unit,
            minValue = minValue,
            maxValue = maxValue,
            dataPoints = mutableListOf()
        )

        val updatedSeries = chart.series.toMutableMap()
        updatedSeries[seriesId] = series

        _charts.value = _charts.value + (chartId to chart.copy(series = updatedSeries))
        logger.debug { "Added series '$seriesId' to chart '$chartId'" }
    }

    /**
     * Удалить серию с графика
     */
    fun removeSeries(chartId: String, seriesId: String) {
        val chart = _charts.value[chartId] ?: return

        val updatedSeries = chart.series.toMutableMap()
        updatedSeries.remove(seriesId)

        _charts.value = _charts.value + (chartId to chart.copy(series = updatedSeries))
        logger.debug { "Removed series '$seriesId' from chart '$chartId'" }
    }

    /**
     * Добавить точку данных в серию
     * @param displayValue - значение для отображения в хинте (если отличается от value)
     */
    fun addDataPoint(chartId: String, seriesId: String, value: Double, displayValue: Double? = null) {
        val chart = _charts.value[chartId] ?: return
        val series = chart.series[seriesId] ?: return

        val point = DataPoint(
            timestamp = Instant.now(),
            value = value.toFloat(),
            displayValue = displayValue?.toFloat()
        )

        val updatedPoints = series.dataPoints.toMutableList()
        updatedPoints.add(point)

        // Ограничиваем количество точек
        while (updatedPoints.size > maxDataPoints) {
            updatedPoints.removeAt(0)
        }

        val updatedSeries = chart.series.toMutableMap()
        updatedSeries[seriesId] = series.copy(dataPoints = updatedPoints)

        _charts.value = _charts.value + (chartId to chart.copy(series = updatedSeries))
    }

    /**
     * Добавить событие/метку на график
     */
    fun addChartEvent(chartId: String, label: String, color: String?) {
        val chart = _charts.value[chartId] ?: return

        val event = ChartEvent(
            timestamp = Instant.now(),
            label = label,
            color = color ?: "yellow"
        )

        val updatedEvents = chart.events.toMutableList()
        updatedEvents.add(event)

        // Ограничиваем количество событий
        while (updatedEvents.size > maxDataPoints) {
            updatedEvents.removeAt(0)
        }

        _charts.value = _charts.value + (chartId to chart.copy(events = updatedEvents))
        logger.debug { "Added event '$label' to chart '$chartId'" }
    }

    /**
     * Очистить все графики
     */
    fun clearAll() {
        _charts.value = emptyMap()
    }
}

/**
 * График с несколькими сериями данных
 */
data class Chart(
    val id: String,
    val title: String,
    val height: Int = 150,
    val series: MutableMap<String, ChartSeries>,
    val events: MutableList<ChartEvent>
)

/**
 * Серия данных на графике (одна линия)
 */
data class ChartSeries(
    val id: String,
    val label: String,
    val color: String = "white",
    val lineStyle: String = "solid", // solid, dashed, dotted
    val chartType: String = "line", // line or bar
    val yAxis: String = "left", // left or right (for dual-axis charts)
    val unit: String = "%", // unit label for axis
    val minValue: Float? = null, // null = auto-scale
    val maxValue: Float? = null, // null = auto-scale
    val dataPoints: MutableList<DataPoint>
)

/**
 * Точка данных
 * @param value - значение для отрисовки на графике
 * @param displayValue - значение для отображения в хинте (если отличается от value)
 */
data class DataPoint(
    val timestamp: Instant,
    val value: Float,
    val displayValue: Float? = null
)

/**
 * Событие/метка на графике (вертикальная линия)
 */
data class ChartEvent(
    val timestamp: Instant,
    val label: String,
    val color: String = "yellow"
)
