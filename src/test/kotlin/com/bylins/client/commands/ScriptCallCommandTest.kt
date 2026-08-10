package com.bylins.client.commands

import com.bylins.client.audio.SoundManager
import com.bylins.client.contextcommands.ContextCommandManager
import com.bylins.client.mapper.Direction
import com.bylins.client.mapper.MapManager
import com.bylins.client.scripting.engines.JavaScriptEngine
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `#script call` — единственный способ повесить скриптовую логику на клавишу.
 *
 * Хоткей умеет только слать строки команд, а скрипт до сих пор мог реагировать
 * лишь на вывод сервера. Ключевое здесь — команда должна гаситься локально:
 * иначе на каждое нажатие сервер отвечает «Чаво?».
 */
class ScriptCallCommandTest {

    private val sentToServer = mutableListOf<String>()
    private val localOutput = mutableListOf<String>()
    private val tempFiles = mutableListOf<File>()

    /** ScriptAPI большой; скрипту в тесте нужен только echo — остальное заглушено. */
    private val echoed = mutableListOf<String>()
    private val api: ScriptAPI = Proxy.newProxyInstance(
        ScriptAPI::class.java.classLoader,
        arrayOf(ScriptAPI::class.java)
    ) { _, method, args ->
        when (method.name) {
            "echo", "print" -> { echoed.add(args?.getOrNull(0)?.toString() ?: ""); null }
            else -> null
        }
    } as ScriptAPI

    private val context = object : CommandContext {
        override fun addLocalOutput(text: String) { localOutput.add(text) }
        override fun sendRaw(command: String) { sentToServer.add(command) }
        override fun startWalk(targetRoomId: String) = false
        override fun walkDirections(directions: List<Direction>, label: String) = false
        override fun stopWalk() {}
        override fun getAllZones(): List<String> = emptyList()
        override fun getZoneStatistics(): Map<String, Int> = emptyMap()
        override fun detectAndAssignZones() {}
        override fun clearAllZones() {}
    }

    private fun processor(scriptManager: ScriptManager?): CommandProcessor {
        val mapDb = File.createTempFile("bylins-cmd-test", ".db").also { tempFiles.add(it) }
        val mapManager = MapManager(dbFileName = mapDb.absolutePath)
        return CommandProcessor(
            scope = CoroutineScope(Dispatchers.Unconfined),
            context = context,
            mapManager = mapManager,
            soundManager = SoundManager(),
            contextCommandManager = ContextCommandManager(onCommand = {}, getCurrentRoom = { null }),
            getScriptManager = { scriptManager },
            getPluginManager = { null }
        )
    }

    /** Скрипт с функцией, которая отмечается через api.echo. */
    private fun scriptManagerWith(js: String, name: String = "first-attack-cycle.js"): ScriptManager {
        val manager = ScriptManager(api)
        manager.registerEngine(JavaScriptEngine())
        val dir = File(System.getProperty("java.io.tmpdir"), "bylins-scripts-${System.nanoTime()}")
        dir.mkdirs()
        tempFiles.add(dir)
        val file = File(dir, name).also { it.writeText(js) }
        tempFiles.add(file)
        manager.loadScript(file)
        return manager
    }

    @AfterTest
    fun cleanUp() {
        tempFiles.forEach { it.deleteRecursively() }
        tempFiles.clear()
    }

    @Test
    fun `вызывает функцию скрипта и не пускает команду на сервер`() {
        val manager = scriptManagerWith(
            """
            function cycle() { api.echo("cycle-called"); }
            """.trimIndent()
        )

        val handled = processor(manager).processNavigationCommand("#script call first-attack-cycle cycle")

        assertTrue(handled, "команда должна обрабатываться локально")
        assertTrue(echoed.contains("cycle-called"), "функция скрипта не вызвана: $echoed")
        assertTrue(sentToServer.isEmpty(), "команда ушла на сервер: $sentToServer")
    }

    @Test
    fun `передаёт аргументы функции`() {
        val manager = scriptManagerWith(
            """
            function setMode(mode) { api.echo("mode:" + mode); }
            """.trimIndent()
        )

        processor(manager).processNavigationCommand("#script call first-attack-cycle setMode пнуть")

        assertTrue(echoed.contains("mode:пнуть"), "аргумент не дошёл: $echoed")
    }

    @Test
    fun `имя скрипта принимается и с расширением`() {
        val manager = scriptManagerWith("function cycle() { api.echo(\"ok\"); }")

        processor(manager).processNavigationCommand("#script call first-attack-cycle.js cycle")

        assertTrue(echoed.contains("ok"), "скрипт не найден по имени с расширением")
    }

    @Test
    fun `про отсутствующую функцию сообщает, а не молчит`() {
        val manager = scriptManagerWith("function cycle() {}")

        val handled = processor(manager).processNavigationCommand("#script call first-attack-cycle нетТакой")

        assertTrue(handled)
        assertTrue(localOutput.any { it.contains("нет функции") }, localOutput.toString())
    }

    @Test
    fun `про неизвестный скрипт сообщает, а не молчит`() {
        val manager = scriptManagerWith("function cycle() {}")

        val handled = processor(manager).processNavigationCommand("#script call нет-такого cycle")

        assertTrue(handled)
        assertTrue(localOutput.any { it.contains("не найден") }, localOutput.toString())
        assertTrue(sentToServer.isEmpty())
    }

    @Test
    fun `ошибка внутри функции не роняет клиент и попадает в вывод`() {
        val manager = scriptManagerWith("function boom() { throw new Error('внутри плохо'); }")

        val handled = processor(manager).processNavigationCommand("#script call first-attack-cycle boom")

        assertTrue(handled)
        assertTrue(localOutput.any { it.contains("Ошибка вызова") }, localOutput.toString())
    }

    @Test
    fun `без имени функции показывает подсказку`() {
        val manager = scriptManagerWith("function cycle() {}")

        processor(manager).processNavigationCommand("#script call first-attack-cycle")

        assertTrue(localOutput.any { it.contains("Использование") }, localOutput.toString())
        assertTrue(echoed.isEmpty())
    }

    @Test
    fun `команды с похожим началом не перехватываются`() {
        // «#scripter» — не «#script»: раньше startsWith ловил и такое
        val handled = processor(null).processNavigationCommand("#scripter call x y")

        assertFalse(handled, "чужая команда обработана как #script")
        assertEquals(emptyList(), localOutput)
    }
}
