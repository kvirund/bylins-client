package com.bylins.client.plugins

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Плагины и скрипты ищутся относительно jar-а приложения, а не рабочего
 * каталога: у распакованного дистрибутива рабочим может оказаться что угодно,
 * а бандл macOS, запущенный из Finder, получает корень файловой системы —
 * клиент лез в /scripts и не находил ничего.
 *
 * Правило одно на все платформы: jar лежит в `app/` у образа jpackage и в
 * `Contents/app/` внутри бандла, поэтому искомое — уровнем выше.
 */
class AppLayoutTest {

    private val temp = File(System.getProperty("java.io.tmpdir"), "bylins-layout-${System.nanoTime()}")

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** Собирает раскладку образа и возвращает путь к jar приложения. */
    private fun image(appDir: String, vararg dirs: String): File {
        val app = File(temp, appDir).also { it.mkdirs() }
        dirs.forEach { File(app.parentFile, it).mkdirs() }
        return File(app, "bylins-client.jar")
    }

    @Test
    fun `у образа jpackage каталог лежит рядом с приложением`() {
        val jar = image("Bylins Client/app", "plugins", "scripts")

        assertEquals(File(temp, "Bylins Client/plugins"), AppLayout.beside("plugins", jar))
        assertEquals(File(temp, "Bylins Client/scripts"), AppLayout.beside("scripts", jar))
    }

    @Test
    fun `в бандле macOS то же правило приводит внутрь Contents`() {
        val jar = image("Bylins Client.app/Contents/app", "plugins", "scripts")

        assertEquals(File(temp, "Bylins Client.app/Contents/plugins"), AppLayout.beside("plugins", jar))
    }

    @Test
    fun `каталога нет — значит нет`() {
        // Иначе вызывающий не отличит установленный клиент от запуска из
        // исходников и не откатится на каталог от рабочего
        val jar = image("Bylins Client/app")

        assertNull(AppLayout.beside("plugins", jar))
        assertNull(AppLayout.beside("plugins", null))
    }

    @Test
    fun `свойство перебивает поиск`() {
        val explicit = File(temp, "явный").also { it.mkdirs() }
        System.setProperty("bylins.test.dir", explicit.absolutePath)
        try {
            val dir = AppLayout.directory("plugins", "bylins.test.dir", AppLayoutTest::class.java)
            assertEquals(explicit.absolutePath, dir.absolutePath)
        } finally {
            System.clearProperty("bylins.test.dir")
        }
    }

    @Test
    fun `без свойства и без образа — каталог от рабочего`() {
        // Так клиент запускается из исходников: scripts и plugins в корне проекта
        val dir = AppLayout.directory("plugins", "bylins.test.missing", AppLayoutTest::class.java)

        assertTrue(!dir.isAbsolute, "ожидали путь от рабочего каталога, получили $dir")
        assertEquals("plugins", dir.name)
    }
}
