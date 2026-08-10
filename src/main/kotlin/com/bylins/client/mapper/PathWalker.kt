package com.bylins.client.mapper

import mu.KotlinLogging

private val logger = KotlinLogging.logger("PathWalker")

/**
 * Пошаговый проход по маршруту.
 *
 * Следующее направление уходит на сервер только после того, как клиент увидел
 * смену комнаты. Слепая отправка — всего маршрута разом или по таймеру —
 * разъезжается на первой же заминке: закрытая дверь, бой, «вы слишком устали»,
 * случайное перемещение. Один шаг не прошёл, а остальные всё равно уходят, и
 * дальше игрок получает пачку «туда не пройти», уехав неизвестно куда.
 *
 * Класс намеренно без корутин и таймеров: он только решает, что делать на
 * очередное событие. Сторожевой таймер заводит владелец и дёргает [onTimeout].
 *
 * @param currentRoomId где игрок сейчас — по нему видно, прошёл ли шаг
 * @param send отправка команды движения на сервер
 * @param notify сообщение игроку
 */
class PathWalker(
    private val currentRoomId: () -> String?,
    private val send: (String) -> Unit,
    private val notify: (String) -> Unit
) {
    /** Следующий шаг маршрута; null — шагов больше нет. */
    private var nextDirection: (() -> Direction?)? = null

    /** Дошли ли до цели. Для маршрута к комнате — сравнение с ней. */
    private var arrived: (() -> Boolean)? = null

    /** Комната, из которой сделан последний шаг: по ней видно, что шаг не прошёл. */
    private var steppedFrom: String? = null

    /** Направление последнего шага — для внятного сообщения об остановке. */
    private var lastDirection: Direction? = null

    private var stepsDone = 0

    /** Вызывается, когда ходок остановился (в том числе дойдя до цели). */
    var onStopped: (() -> Unit)? = null

    val isWalking: Boolean get() = nextDirection != null

    /**
     * Начинает движение.
     *
     * @param next следующий шаг; для маршрута к комнате его отдаёт MapManager
     *   и сам пересчитывает, если игрока унесло с пути
     * @param arrived признак прибытия; для списка направлений — просто их конец
     * @return false, если идти оказалось некуда
     */
    fun start(next: () -> Direction?, arrived: () -> Boolean): Boolean {
        this.nextDirection = next
        this.arrived = arrived
        stepsDone = 0
        if (arrived()) {
            stop("Вы уже на месте")
            return false
        }
        return step()
    }

    /** Игрок сменил комнату — шаг подтверждён, можно делать следующий. */
    fun onRoomChanged() {
        if (!isWalking) return
        // Событие «вошли в комнату» приходит и без смены комнаты: MSDP шлёт
        // ROOM на каждый промпт. Без этой проверки шаги уходят пачкой, и ходок
        // вырождается ровно в тот залп, ради ухода от которого он и написан.
        if (currentRoomId() == steppedFrom) return
        stepsDone++
        if (arrived?.invoke() == true) {
            stop("Пришли, шагов: $stepsDone")
            return
        }
        step()
    }

    /**
     * Сторож: комната не сменилась за отведённое время.
     * Идти дальше вслепую нельзя — маршрут уже не соответствует месту.
     */
    fun onTimeout() {
        if (!isWalking) return
        if (currentRoomId() != steppedFrom) return // шаг всё-таки прошёл
        val direction = lastDirection?.russianName ?: "шаг"
        stop("Застряли: «$direction» не прошёл. Сделано шагов: $stepsDone")
    }

    /** Остановка: по приходу, по ошибке или по воле игрока. */
    fun stop(reason: String? = null) {
        if (!isWalking) return
        nextDirection = null
        arrived = null
        steppedFrom = null
        lastDirection = null
        reason?.let { notify("[Маршрут] $it") }
        onStopped?.invoke()
    }

    private fun step(): Boolean {
        val direction = nextDirection?.invoke()
        if (direction == null) {
            // Шаги кончились, а цель не достигнута: обычно игрока унесло с пути
            // и пересчёт не нашёл дороги
            stop("Маршрут оборвался, до цели не дошли. Сделано шагов: $stepsDone")
            return false
        }
        steppedFrom = currentRoomId()
        lastDirection = direction
        logger.debug { "Walk step ${direction.russianShort} from $steppedFrom" }
        send(direction.russianShort)
        return true
    }
}
