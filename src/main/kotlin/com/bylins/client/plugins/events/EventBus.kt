package com.bylins.client.plugins.events

import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Шина событий для плагинов.
 *
 * Управляет подписками и рассылкой событий.
 * Потокобезопасная реализация.
 */
private val logger = KotlinLogging.logger("EventBus")
class EventBus {

    private data class Subscription(
        val id: String,
        val eventClass: Class<out PluginEvent>,
        val priority: EventPriority,
        val handler: (PluginEvent) -> Unit,
        val pluginId: String
    )

    private val subscriptions = CopyOnWriteArrayList<Subscription>()
    private val subscriptionIdCounter = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * Подписывается на событие.
     *
     * @param eventClass Класс события
     * @param priority Приоритет обработки
     * @param pluginId ID плагина-владельца подписки
     * @param handler Обработчик события
     * @return ID подписки для отписки
     */
    fun <T : PluginEvent> subscribe(
        eventClass: Class<T>,
        priority: EventPriority,
        pluginId: String,
        handler: (T) -> Unit
    ): String {
        val id = "sub_${subscriptionIdCounter.incrementAndGet()}"

        @Suppress("UNCHECKED_CAST")
        val subscription = Subscription(
            id = id,
            eventClass = eventClass,
            priority = priority,
            handler = { event -> handler(event as T) },
            pluginId = pluginId
        )

        subscriptions.add(subscription)

        // Пересортируем по приоритету
        sortSubscriptions()

        return id
    }

    /**
     * Отписывается от события.
     *
     * @param subscriptionId ID подписки
     */
    fun unsubscribe(subscriptionId: String) {
        subscriptions.removeIf { it.id == subscriptionId }
    }

    /**
     * Отписывает все подписки плагина.
     *
     * @param pluginId ID плагина
     */
    fun unsubscribeAll(pluginId: String) {
        subscriptions.removeIf { it.pluginId == pluginId }
    }

    /**
     * Рассылает событие всем подписчикам.
     *
     * @param event Событие для рассылки
     */
    fun post(event: PluginEvent) {
        val eventClass = event::class.java

        for (subscription in subscriptions) {
            // Проверяем, подходит ли подписка для этого события
            if (!subscription.eventClass.isAssignableFrom(eventClass)) {
                continue
            }

            // MONITOR не может отменять события
            if (subscription.priority == EventPriority.MONITOR) {
                try {
                    subscription.handler(event)
                } catch (e: Exception) {
                    logger.error { "Error in MONITOR handler: ${e.message}" }
                    e.printStackTrace()
                }
                continue
            }

            // Проверяем, отменено ли событие
            if (event is CancellableEvent && event.isCancelled) {
                continue
            }

            try {
                subscription.handler(event)
            } catch (e: Exception) {
                logger.error { "Error in event handler: ${e.message}" }
                e.printStackTrace()
            }
        }
    }

    /**
     * Рассылает событие и возвращает результат (для CancellableEvent).
     *
     * @param event Событие для рассылки
     * @return true если событие было отменено
     */
    fun <T : PluginEvent> postAndCheck(event: T): T {
        post(event)
        return event
    }

    /**
     * Получает количество подписок.
     */
    fun getSubscriptionCount(): Int = subscriptions.size

    /**
     * Получает количество подписок плагина.
     */
    fun getSubscriptionCount(pluginId: String): Int =
        subscriptions.count { it.pluginId == pluginId }

    /**
     * Очищает все подписки.
     */
    fun clear() {
        subscriptions.clear()
    }

    private fun sortSubscriptions() {
        // Сортируем: LOWEST -> LOW -> NORMAL -> HIGH -> HIGHEST -> MONITOR
        val sorted = subscriptions.sortedBy { it.priority.ordinal }
        subscriptions.clear()
        subscriptions.addAll(sorted)
    }
}
