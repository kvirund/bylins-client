package com.bylins.client.config

import com.bylins.client.triggers.Trigger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Конфиг не должен затираться, если его не читали.
 *
 * Клиент упал при старте — и shutdown hook честно сохранил пустое состояние
 * поверх файла с 54 триггерами, 16 хоткеями и 42 контекстными правилами.
 * Здесь проверяется, что такой записи больше не происходит, и что рядом
 * остаётся резервная копия прежней версии.
 */
class ConfigManagerTest {

    private lateinit var home: Path
    private var previousHome: String? = null

    private val configFile: Path get() = home.resolve(".bylins-client").resolve("config.json")

    @BeforeTest
    fun setUp() {
        // ConfigManager пишет в ~/.bylins-client — на время теста подменяем дом,
        // иначе тест испортит конфиг живого клиента
        previousHome = System.getProperty("user.home")
        home = Files.createTempDirectory("bylins-config-test")
        System.setProperty("user.home", home.toString())
    }

    @AfterTest
    fun tearDown() {
        previousHome?.let { System.setProperty("user.home", it) }
        home.toFile().deleteRecursively()
    }

    private fun trigger(name: String) = Trigger(
        id = name,
        name = name,
        pattern = Regex("^$name$"),
        commands = listOf("смотреть")
    )

    private fun save(manager: ConfigManager, triggers: List<Trigger>) = manager.saveConfig(
        triggers = triggers,
        aliases = emptyList(),
        hotkeys = emptyList(),
        variables = emptyMap(),
        tabs = emptyList()
    )

    @Test
    fun `сохранение без загрузки не затирает конфиг`() {
        val first = ConfigManager()
        first.loadConfig()
        save(first, listOf(trigger("боевой"), trigger("лечение")))
        val saved = Files.readString(configFile)

        // Новый запуск: клиент упал раньше, чем прочитал конфиг, но shutdown
        // hook всё равно позвал сохранение
        val crashed = ConfigManager()
        save(crashed, emptyList())

        assertEquals(saved, Files.readString(configFile))
        assertEquals(2, ConfigManager().loadConfig().triggers.size)
    }

    @Test
    fun `после загрузки сохранение работает как обычно`() {
        val manager = ConfigManager()
        manager.loadConfig()
        save(manager, listOf(trigger("боевой")))

        val reloaded = ConfigManager()
        val data = reloaded.loadConfig()
        assertEquals(listOf("боевой"), data.triggers.map { it.name })

        save(reloaded, listOf(trigger("боевой"), trigger("лечение")))
        assertEquals(2, ConfigManager().loadConfig().triggers.size)
    }

    @Test
    fun `прежняя версия конфига остаётся резервной копией`() {
        val first = ConfigManager()
        first.loadConfig()
        save(first, listOf(trigger("боевой"), trigger("лечение")))

        val second = ConfigManager()
        second.loadConfig()
        save(second, emptyList())

        val backup = home.resolve(".bylins-client").resolve("config.json.1")
        assertTrue(Files.exists(backup), "нет резервной копии рядом с конфигом")
        assertTrue(Files.readString(backup).contains("боевой"), "в копии нет прежних триггеров")
    }

    /** Пишет конфиг с одним триггером-меткой, чтобы копии можно было различить. */
    private fun saveMarked(mark: String, backups: Int = 3) {
        val manager = ConfigManager()
        manager.loadConfig()
        manager.saveConfig(
            triggers = listOf(trigger(mark)),
            aliases = emptyList(),
            hotkeys = emptyList(),
            variables = emptyMap(),
            tabs = emptyList(),
            configBackups = backups
        )
    }

    private fun backup(index: Int): Path = home.resolve(".bylins-client").resolve("config.json.$index")

    @Test
    fun `копии сдвигаются по кругу, самая старая вытесняется`() {
        listOf("первый", "второй", "третий", "четвёртый").forEach { saveMarked(it) }

        // .1 — предыдущая версия, .3 — самая старая из хранимых
        assertTrue(Files.readString(backup(1)).contains("третий"), "в .1 не предыдущая версия")
        assertTrue(Files.readString(backup(2)).contains("второй"))
        assertTrue(Files.readString(backup(3)).contains("первый"))
        assertTrue(!Files.exists(backup(4)), "хранится больше копий, чем задано")
    }

    @Test
    fun `глубина цикла настраивается`() {
        listOf("первый", "второй", "третий", "четвёртый").forEach { saveMarked(it, backups = 1) }

        assertTrue(Files.readString(backup(1)).contains("третий"))
        assertTrue(!Files.exists(backup(2)), "при глубине 1 лишних копий быть не должно")
    }

    @Test
    fun `нулевая глубина отключает копии`() {
        saveMarked("первый", backups = 0)
        saveMarked("второй", backups = 0)

        assertTrue(!Files.exists(backup(1)))
    }

    @Test
    fun `уменьшение глубины убирает лишние копии`() {
        listOf("первый", "второй", "третий", "четвёртый").forEach { saveMarked(it) }
        assertTrue(Files.exists(backup(3)))

        saveMarked("пятый", backups = 1)

        assertTrue(!Files.exists(backup(2)), "старые копии остались после уменьшения глубины")
        assertTrue(!Files.exists(backup(3)))
    }

    @Test
    fun `глубина копий переживает перезапуск`() {
        val manager = ConfigManager()
        manager.loadConfig()
        manager.saveConfig(
            triggers = emptyList(), aliases = emptyList(), hotkeys = emptyList(),
            variables = emptyMap(), tabs = emptyList(), configBackups = 7
        )

        assertEquals(7, ConfigManager().loadConfig().configBackups)
    }

    @Test
    fun `временный файл не остаётся после записи`() {
        val manager = ConfigManager()
        manager.loadConfig()
        save(manager, listOf(trigger("боевой")))

        assertTrue(!Files.exists(home.resolve(".bylins-client").resolve("config.json.tmp")))
    }
}
