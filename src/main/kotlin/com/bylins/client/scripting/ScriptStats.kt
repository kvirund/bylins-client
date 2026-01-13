package com.bylins.client.scripting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger("ScriptStats")

/**
 * Статистика выполнения скриптов и триггеров
 */
object ScriptStats {

    // Статистика триггеров
    private val triggerStats = ConcurrentHashMap<String, TriggerStat>()

    // Статистика скриптов
    private val scriptStats = ConcurrentHashMap<String, ScriptStat>()

    // Общая статистика
    private val totalTriggerTime = AtomicLong(0)
    private val totalTriggerCount = AtomicLong(0)
    private val totalScriptTime = AtomicLong(0)
    private val totalScriptCount = AtomicLong(0)

    // StateFlow для UI
    private val _stats = MutableStateFlow(StatsSnapshot())
    val stats: StateFlow<StatsSnapshot> = _stats

    /**
     * Записывает время выполнения триггера
     */
    fun recordTrigger(triggerId: String, pattern: String, timeNanos: Long, matched: Boolean) {
        val stat = triggerStats.getOrPut(triggerId) { TriggerStat(triggerId, pattern) }
        stat.record(timeNanos, matched)

        totalTriggerTime.addAndGet(timeNanos)
        totalTriggerCount.incrementAndGet()

        updateSnapshot()

        // Предупреждаем о медленных триггерах (>10ms)
        if (timeNanos > 10_000_000) {
            logger.warn { "Slow trigger '$pattern': ${timeNanos / 1_000_000}ms" }
        }
    }

    /**
     * Записывает время выполнения скрипта
     */
    fun recordScript(scriptId: String, scriptName: String, operation: String, timeNanos: Long) {
        val stat = scriptStats.getOrPut(scriptId) { ScriptStat(scriptId, scriptName) }
        stat.record(operation, timeNanos)

        totalScriptTime.addAndGet(timeNanos)
        totalScriptCount.incrementAndGet()

        updateSnapshot()

        // Предупреждаем о медленных операциях (>100ms)
        if (timeNanos > 100_000_000) {
            logger.warn { "Slow script operation '$scriptName.$operation': ${timeNanos / 1_000_000}ms" }
        }
    }

    /**
     * Записывает время IPC вызова
     */
    fun recordIpcCall(engineName: String, method: String, timeNanos: Long) {
        // Предупреждаем о медленных IPC вызовах (>50ms)
        if (timeNanos > 50_000_000) {
            logger.warn { "Slow IPC call '$engineName.$method': ${timeNanos / 1_000_000}ms" }
        }
    }

    private fun updateSnapshot() {
        val triggerList = triggerStats.values.map { stat ->
            TriggerStatSnapshot(
                id = stat.id,
                pattern = stat.pattern,
                totalTimeMs = stat.totalTime.get() / 1_000_000.0,
                callCount = stat.callCount.get(),
                matchCount = stat.matchCount.get(),
                avgTimeMs = if (stat.callCount.get() > 0)
                    stat.totalTime.get() / stat.callCount.get() / 1_000_000.0
                else 0.0
            )
        }.sortedByDescending { it.totalTimeMs }

        val scriptList = scriptStats.values.map { stat ->
            ScriptStatSnapshot(
                id = stat.id,
                name = stat.name,
                totalTimeMs = stat.totalTime.get() / 1_000_000.0,
                callCount = stat.callCount.get(),
                avgTimeMs = if (stat.callCount.get() > 0)
                    stat.totalTime.get() / stat.callCount.get() / 1_000_000.0
                else 0.0,
                operations = stat.operations.mapValues { (_, v) -> v.get() / 1_000_000.0 }
            )
        }.sortedByDescending { it.totalTimeMs }

        _stats.value = StatsSnapshot(
            triggers = triggerList,
            scripts = scriptList,
            totalTriggerTimeMs = totalTriggerTime.get() / 1_000_000.0,
            totalTriggerCount = totalTriggerCount.get(),
            totalScriptTimeMs = totalScriptTime.get() / 1_000_000.0,
            totalScriptCount = totalScriptCount.get()
        )
    }

    /**
     * Сбрасывает статистику
     */
    fun reset() {
        triggerStats.clear()
        scriptStats.clear()
        totalTriggerTime.set(0)
        totalTriggerCount.set(0)
        totalScriptTime.set(0)
        totalScriptCount.set(0)
        _stats.value = StatsSnapshot()
    }

    /**
     * Возвращает форматированную статистику для отображения
     */
    fun getFormattedStats(): String {
        val snapshot = _stats.value
        val sb = StringBuilder()

        sb.appendLine("=== Script Statistics ===")
        sb.appendLine()
        sb.appendLine("Total trigger checks: ${snapshot.totalTriggerCount}, time: %.2fms".format(snapshot.totalTriggerTimeMs))
        sb.appendLine("Total script calls: ${snapshot.totalScriptCount}, time: %.2fms".format(snapshot.totalScriptTimeMs))
        sb.appendLine()

        if (snapshot.triggers.isNotEmpty()) {
            sb.appendLine("--- Triggers (by total time) ---")
            snapshot.triggers.take(10).forEach { t ->
                sb.appendLine("  ${t.pattern.take(30).padEnd(30)} | calls: ${t.callCount} | matches: ${t.matchCount} | total: %.2fms | avg: %.3fms"
                    .format(t.totalTimeMs, t.avgTimeMs))
            }
            sb.appendLine()
        }

        if (snapshot.scripts.isNotEmpty()) {
            sb.appendLine("--- Scripts (by total time) ---")
            snapshot.scripts.take(10).forEach { s ->
                sb.appendLine("  ${s.name.padEnd(20)} | calls: ${s.callCount} | total: %.2fms | avg: %.3fms"
                    .format(s.totalTimeMs, s.avgTimeMs))
            }
        }

        return sb.toString()
    }
}

private class TriggerStat(val id: String, val pattern: String) {
    val totalTime = AtomicLong(0)
    val callCount = AtomicLong(0)
    val matchCount = AtomicLong(0)

    fun record(timeNanos: Long, matched: Boolean) {
        totalTime.addAndGet(timeNanos)
        callCount.incrementAndGet()
        if (matched) matchCount.incrementAndGet()
    }
}

private class ScriptStat(val id: String, val name: String) {
    val totalTime = AtomicLong(0)
    val callCount = AtomicLong(0)
    val operations = ConcurrentHashMap<String, AtomicLong>()

    fun record(operation: String, timeNanos: Long) {
        totalTime.addAndGet(timeNanos)
        callCount.incrementAndGet()
        operations.getOrPut(operation) { AtomicLong(0) }.addAndGet(timeNanos)
    }
}

data class TriggerStatSnapshot(
    val id: String,
    val pattern: String,
    val totalTimeMs: Double,
    val callCount: Long,
    val matchCount: Long,
    val avgTimeMs: Double
)

data class ScriptStatSnapshot(
    val id: String,
    val name: String,
    val totalTimeMs: Double,
    val callCount: Long,
    val avgTimeMs: Double,
    val operations: Map<String, Double>
)

data class StatsSnapshot(
    val triggers: List<TriggerStatSnapshot> = emptyList(),
    val scripts: List<ScriptStatSnapshot> = emptyList(),
    val totalTriggerTimeMs: Double = 0.0,
    val totalTriggerCount: Long = 0,
    val totalScriptTimeMs: Double = 0.0,
    val totalScriptCount: Long = 0
)
