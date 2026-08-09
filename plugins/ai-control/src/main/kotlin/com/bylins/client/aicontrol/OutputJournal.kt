package com.bylins.client.aicontrol

/**
 * Строка вывода с абсолютным номером.
 *
 * @param seq монотонный номер, НЕ сбрасывается при вытеснении из буфера —
 *   по нему агент запрашивает «всё, что появилось с прошлого раза».
 */
data class JournalLine(
    val seq: Long,
    val text: String,
    val timestamp: Long
)

/**
 * Кольцевой журнал вывода MUD для ИИ-агентов.
 *
 * Хранит последние [capacity] строк. Номера строк монотонны, поэтому курсор
 * агента остаётся валидным даже после вытеснения: если запрошенный seq уже
 * вытеснен, отдаём то, что есть, и сообщаем о пропуске.
 *
 * Потокобезопасен: пишет сетевой поток клиента, читают потоки HTTP-сервера.
 */
class OutputJournal(private val capacity: Int = 5000) {

    private val lines = ArrayDeque<JournalLine>()
    private val lock = Any()

    /** Номер, который получит следующая добавленная строка. */
    private var nextSeq: Long = 1

    /** Сколько строк вытеснено из начала (для диагностики пропусков). */
    private var evicted: Long = 0

    /** Номер, который будет присвоен следующей строке. */
    val headSeq: Long get() = synchronized(lock) { nextSeq }

    /** Номер самой старой доступной строки (или headSeq, если журнал пуст). */
    val tailSeq: Long get() = synchronized(lock) { lines.firstOrNull()?.seq ?: nextSeq }

    val size: Int get() = synchronized(lock) { lines.size }

    /** Добавляет строку вывода, возвращает присвоенный ей seq. */
    fun append(text: String, timestamp: Long): Long = synchronized(lock) {
        val seq = nextSeq++
        lines.addLast(JournalLine(seq, text, timestamp))
        while (lines.size > capacity) {
            lines.removeFirst()
            evicted++
        }
        seq
    }

    /**
     * Строки, начиная с [since] (включительно), максимум [limit] штук.
     *
     * @return срез журнала; [ReadResult.missed] > 0, если часть запрошенного
     *   уже вытеснена — агент так понимает, что видит не всё.
     */
    fun read(since: Long, limit: Int = 500): ReadResult = synchronized(lock) {
        val oldest = lines.firstOrNull()?.seq ?: nextSeq
        val effectiveSince = maxOf(since, oldest)
        val missed = maxOf(0L, effectiveSince - since)

        val selected = lines.asSequence()
            .filter { it.seq >= effectiveSince }
            .take(limit)
            .toList()

        ReadResult(
            lines = selected,
            missed = missed,
            nextSeq = selected.lastOrNull()?.let { it.seq + 1 } ?: maxOf(effectiveSince, nextSeq)
        )
    }

    fun clear() = synchronized(lock) {
        evicted += lines.size
        lines.clear()
    }

    data class ReadResult(
        val lines: List<JournalLine>,
        /** Сколько строк было потеряно из-за вытеснения (0 — ничего не пропущено). */
        val missed: Long,
        /** Курсор для следующего запроса. */
        val nextSeq: Long
    )
}
