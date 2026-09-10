package com.bylins.client.plugins

import java.io.File

/**
 * Где искать то, что лежит рядом с установленным приложением: плагины, скрипты.
 *
 * Ищем относительно jar-а, а не рабочего каталога: у распакованного
 * дистрибутива рабочим каталогом может оказаться что угодно — ярлык, консоль,
 * а у бандла macOS, запущенного из Finder, это вообще корень файловой системы.
 *
 * Правило одно на все платформы. jar приложения лежит в `app/` у образа
 * jpackage и в `Contents/app/` внутри бандла macOS, поэтому уровень выше — это
 * `<образ>/` и `Contents/` соответственно; туда сборка и кладёт эти каталоги.
 */
object AppLayout {

    /** jar (или каталог классов), из которого загружен [anchor]. */
    fun jarOf(anchor: Class<*>): File? = runCatching {
        File(anchor.protectionDomain.codeSource.location.toURI())
    }.getOrNull()

    /**
     * Каталог [name] рядом с приложением.
     *
     * @return null, если каталога нет — тогда вызывающий откатывается на
     *   поведение для разработки (каталог от рабочего)
     */
    fun beside(name: String, jar: File?): File? =
        jar?.parentFile?.parentFile?.resolve(name)?.takeIf { it.isDirectory }

    /**
     * Каталог: сначала явно заданный свойством, затем рядом с приложением,
     * затем от рабочего каталога — как при запуске из исходников.
     */
    fun directory(name: String, property: String, anchor: Class<*>): File {
        System.getProperty(property)?.let { return File(it) }
        return beside(name, jarOf(anchor)) ?: File(name)
    }
}
