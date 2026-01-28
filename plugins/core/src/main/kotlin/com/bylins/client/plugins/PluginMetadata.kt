package com.bylins.client.plugins

/**
 * Метаданные плагина (из plugin.yml)
 */
data class PluginMetadata(
    /** Уникальный идентификатор плагина */
    val id: String,

    /** Отображаемое имя плагина */
    val name: String,

    /** Версия плагина (семантическое версионирование) */
    val version: String,

    /** Описание плагина */
    val description: String = "",

    /** Автор плагина */
    val author: String = "",

    /** Сайт/репозиторий плагина */
    val website: String = "",

    /** Полное имя главного класса (реализует Plugin) */
    val mainClass: String,

    /** Обязательные зависимости */
    val dependencies: List<PluginDependency> = emptyList(),

    /** Опциональные зависимости (если загружены - можно использовать) */
    val softDependencies: List<String> = emptyList(),

    /** Минимальная версия API клиента */
    val apiVersion: String = "1.0"
)

/**
 * Зависимость плагина
 */
data class PluginDependency(
    /** Идентификатор плагина-зависимости */
    val id: String,

    /** Минимальная версия (null = любая) */
    val minVersion: String? = null
)

/**
 * Состояние плагина
 */
enum class PluginState {
    /** Загружен, но не включен */
    LOADED,

    /** Включен и работает */
    ENABLED,

    /** Выключен пользователем */
    DISABLED,

    /** Ошибка при загрузке/работе */
    ERROR
}

/**
 * Загруженный плагин с метаданными и состоянием
 */
data class LoadedPlugin(
    val metadata: PluginMetadata,
    val instance: Plugin,
    val state: PluginState,
    val jarFile: java.io.File,
    val errorMessage: String? = null
)
