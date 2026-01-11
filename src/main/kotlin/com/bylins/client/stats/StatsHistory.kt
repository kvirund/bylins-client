package com.bylins.client.stats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * Хранит историю изменения статистики для построения графиков
 */
class StatsHistory(
    private val maxDataPoints: Int = 300 // ~5 минут при обновлении каждую секунду
) {
    private val _hpHistory = MutableStateFlow<List<DataPoint>>(emptyList())
    val hpHistory: StateFlow<List<DataPoint>> = _hpHistory

    private val _manaHistory = MutableStateFlow<List<DataPoint>>(emptyList())
    val manaHistory: StateFlow<List<DataPoint>> = _manaHistory

    private val _movementHistory = MutableStateFlow<List<DataPoint>>(emptyList())
    val movementHistory: StateFlow<List<DataPoint>> = _movementHistory

    /**
     * Добавляет точку данных для HP
     */
    fun addHpData(value: Int, maxValue: Int) {
        val percentage = if (maxValue > 0) (value.toFloat() / maxValue * 100) else 0f
        addDataPoint(_hpHistory, percentage)
    }

    /**
     * Добавляет точку данных для Mana
     */
    fun addManaData(value: Int, maxValue: Int) {
        val percentage = if (maxValue > 0) (value.toFloat() / maxValue * 100) else 0f
        addDataPoint(_manaHistory, percentage)
    }

    /**
     * Добавляет точку данных для Movement
     */
    fun addMovementData(value: Int, maxValue: Int) {
        val percentage = if (maxValue > 0) (value.toFloat() / maxValue * 100) else 0f
        addDataPoint(_movementHistory, percentage)
    }

    /**
     * Добавляет точку данных в указанный поток
     */
    private fun addDataPoint(flow: MutableStateFlow<List<DataPoint>>, value: Float) {
        val currentList = flow.value.toMutableList()
        currentList.add(DataPoint(Instant.now(), value))

        // Удаляем старые точки если превышен лимит
        while (currentList.size > maxDataPoints) {
            currentList.removeAt(0)
        }

        flow.value = currentList
    }

    /**
     * Очищает всю историю
     */
    fun clear() {
        _hpHistory.value = emptyList()
        _manaHistory.value = emptyList()
        _movementHistory.value = emptyList()
    }

    /**
     * Точка данных для графика
     */
    data class DataPoint(
        val timestamp: Instant,
        val value: Float // Процент (0-100)
    )
}
