package com.bylins.client.connection

import kotlinx.serialization.Serializable

/**
 * Профиль подключения к MUD серверу
 *
 * @param id Уникальный идентификатор профиля
 * @param name Название профиля (отображается в UI)
 * @param host Адрес сервера
 * @param port Порт сервера
 * @param encoding Кодировка (UTF-8, windows-1251, KOI8-R, ISO-8859-1)
 * @param mapFile Имя файла карты (относительно ~/.bylins-client/maps/)
 */
@Serializable
data class ConnectionProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val encoding: String = "UTF-8",
    val mapFile: String = "maps.db"
) {
    companion object {
        /**
         * Создает профиль по умолчанию для Былин
         */
        fun createDefault(): ConnectionProfile {
            return ConnectionProfile(
                name = "Былины (основной)",
                host = "bylins.su",
                port = 4000,
                encoding = "UTF-8"
            )
        }

        /**
         * Создает список стандартных профилей
         */
        fun createDefaultProfiles(): List<ConnectionProfile> {
            return listOf(
                ConnectionProfile(
                    name = "Былины (основной)",
                    host = "bylins.su",
                    port = 4000,
                    encoding = "UTF-8"
                ),
                ConnectionProfile(
                    name = "Былины (резервный)",
                    host = "mud.bylins.su",
                    port = 4000,
                    encoding = "UTF-8"
                )
            )
        }
    }

    /**
     * Возвращает строковое представление для отображения
     */
    fun getDisplayName(): String {
        return "$name ($host:$port)"
    }
}
