package com.bylins.client.contextcommands

import com.bylins.client.mapper.Room

/**
 * Проверка области действия — одна на все сущности, которые её используют
 * (контекстные команды, триггеры, хоткеи).
 *
 * @param room текущая комната игрока; null — местоположение неизвестно
 *   (нет карты или ещё не определились), тогда работают только глобальные правила.
 * @return true, если правило действует здесь и сейчас
 */
fun matchesRoom(scope: ContextScope?, room: Room?): Boolean = when (scope) {
    // Область не задана — правило глобальное (обычное поведение триггеров)
    null, is ContextScope.World -> true

    is ContextScope.Zone -> {
        val zone = room?.zone
        zone != null && scope.zones.contains(zone)
    }

    is ContextScope.Room -> {
        room != null && scope.matches(room.id, room.properties)
    }
}

/** Краткое человекочитаемое описание области — для UI и логов. */
fun describeScope(scope: ContextScope?): String = when (scope) {
    null, is ContextScope.World -> "везде"
    is ContextScope.Zone -> "зоны: " + scope.zones.joinToString(", ")
    is ContextScope.Room -> buildList {
        if (scope.roomIds.isNotEmpty()) add("комнаты: " + scope.roomIds.joinToString(", "))
        if (scope.roomPropertyKeys.isNotEmpty()) add("свойства: " + scope.roomPropertyKeys.joinToString(", "))
    }.joinToString("; ").ifEmpty { "комнаты не заданы" }
}
