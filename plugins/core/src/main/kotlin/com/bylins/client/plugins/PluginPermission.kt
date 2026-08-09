package com.bylins.client.plugins

/**
 * Разрешения плагина, которые пользователь выдаёт ЯВНО в настройках плагинов.
 *
 * Плагин объявляет нужные разрешения в plugin.yml (`permissions:`), но до выдачи
 * пользователем соответствующие вызовы API запрещены и бросают
 * [PluginPermissionDeniedException].
 */
enum class PluginPermission(
    /** Идентификатор в plugin.yml и конфиге */
    val id: String,
    /** Название для UI */
    val title: String,
    /** Пояснение: что плагин сможет делать */
    val description: String
) {
    /**
     * Управление клиентом «как если бы пользователь нажимал кнопки»:
     * профили подключения, триггеры, алиасы, хоткеи, вкладки, настройки.
     */
    CLIENT_CONTROL(
        id = "client-control",
        title = "Управление клиентом",
        description = "Создавать и изменять профили, триггеры, алиасы, хоткеи и вкладки"
    ),

    /**
     * Управление соединением с MUD-сервером (подключение/отключение).
     * Выделено отдельно: это самое «громкое» действие.
     */
    CONNECTION_CONTROL(
        id = "connection-control",
        title = "Управление подключением",
        description = "Подключаться к серверу и отключаться от него"
    ),

    /**
     * Открытие локального сетевого порта (например, для внешних ИИ-агентов).
     */
    NETWORK_SERVER(
        id = "network-server",
        title = "Локальный сетевой порт",
        description = "Открывать локальный порт для подключения внешних программ"
    );

    companion object {
        fun fromId(id: String): PluginPermission? = entries.find { it.id == id }
    }
}

/**
 * Бросается при вызове защищённого API без выданного разрешения.
 */
class PluginPermissionDeniedException(
    val pluginId: String,
    val permission: PluginPermission
) : SecurityException(
    "Плагину '$pluginId' не выдано разрешение '${permission.title}' (${permission.id}). " +
        "Включите его в настройках плагинов."
)
