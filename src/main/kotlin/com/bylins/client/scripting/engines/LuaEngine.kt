package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine
import mu.KotlinLogging
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger("Lua")

/**
 * Lua движок (использует LuaJ)
 * Поддерживает Lua 5.2 синтаксис
 */
class LuaEngine : ScriptEngine {
    override val name: String = "lua"
    override val fileExtensions: List<String> = listOf(".lua")

    private var globals: Globals? = null
    private lateinit var api: ScriptAPI

    // Реестр callback'ов для триггеров и таймеров
    private val triggerCallbacks = ConcurrentHashMap<String, LuaValue>()
    private val timerCallbacks = ConcurrentHashMap<String, LuaValue>()

    override fun isAvailable(): Boolean {
        return try {
            Class.forName("org.luaj.vm2.Globals")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override fun initialize(api: ScriptAPI) {
        this.api = api

        try {
            // Принудительно устанавливаем UTF-8 как кодировку по умолчанию
            // Это влияет на String.getBytes() внутри LuaJ
            System.setProperty("file.encoding", "UTF-8")
            System.setProperty("sun.jnu.encoding", "UTF-8")

            // Создаем стандартное окружение Lua
            globals = JsePlatform.standardGlobals()

            // Добавляем API в глобальный контекст
            globals?.set("api", CoerceJavaToLua.coerce(api))

            // Регистрируем нативные функции с обёртками для callback'ов
            registerNativeFunctions()

            // Загружаем вспомогательные функции из ресурсов
            javaClass.getResourceAsStream("/lua_helper.lua")?.use { stream ->
                globals?.load(stream, "@lua_helper", "bt", globals)?.call()
            } ?: throw IllegalStateException("lua_helper.lua not found in resources")
        } catch (e: Exception) {
            logger.error { "Error initializing LuaJ: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Конвертирует LuaValue в UTF-8 строку
     * Нужно для правильной обработки кириллицы
     */
    private fun luaValueToUtf8String(value: LuaValue): String {
        return if (value is LuaString) {
            String(value.m_bytes, value.m_offset, value.m_length, Charsets.UTF_8)
        } else {
            value.tojstring()
        }
    }

    /**
     * Регистрирует нативные функции которые требуют обёртки callback'ов
     */
    private fun registerNativeFunctions() {
        val g = globals ?: return

        // add_trigger(pattern, callback) - регистрация триггера с Lua callback
        g.set("add_trigger", object : TwoArgFunction() {
            override fun call(pattern: LuaValue, callback: LuaValue): LuaValue {
                // Правильно декодируем UTF-8 паттерн из Lua
                val patternStr = luaValueToUtf8String(pattern)

                // DEBUG: показываем байты паттерна
                val patternBytes = if (pattern is LuaString) {
                    pattern.m_bytes.slice(pattern.m_offset until pattern.m_offset + pattern.m_length)
                        .joinToString(" ") { String.format("%02X", it) }
                } else "N/A"
                logger.debug { "add_trigger pattern='$patternStr' bytes=[$patternBytes]" }

                // Создаём Kotlin lambda-обёртку для Lua callback
                val kotlinCallback: (String, Map<Int, String>) -> Unit = { line, groups ->
                    try {
                        // DEBUG: показываем входящие данные
                        val lineBytes = line.toByteArray(Charsets.UTF_8).take(50).joinToString(" ") { String.format("%02X", it) }
                        logger.debug { "trigger callback: line='$line' bytes=[$lineBytes]" }

                        // Конвертируем groups в Lua таблицу (явно используем UTF-8)
                        val luaGroups = LuaTable()
                        groups.forEach { (index, value) ->
                            luaGroups.set(index, LuaString.valueUsing(value.toByteArray(Charsets.UTF_8)))
                        }

                        // Вызываем Lua callback (явно используем UTF-8 для кириллицы)
                        val luaLine = LuaString.valueUsing(line.toByteArray(Charsets.UTF_8))
                        logger.debug { "Calling Lua callback..." }
                        val result = callback.call(luaLine, luaGroups)
                        logger.debug { "Lua callback returned: $result" }
                    } catch (e: Exception) {
                        logger.error { "Error in trigger callback: ${e.message}" }
                        e.printStackTrace()
                    }
                }

                val triggerId = api.addTrigger(patternStr, kotlinCallback)
                triggerCallbacks[triggerId] = callback

                return LuaValue.valueOf(triggerId)
            }
        })

        // set_timeout(callback, delay) - отложенный вызов
        g.set("set_timeout", object : TwoArgFunction() {
            override fun call(callback: LuaValue, delay: LuaValue): LuaValue {
                val delayMs = delay.tolong()

                val kotlinCallback: () -> Unit = {
                    try {
                        callback.call()
                    } catch (e: Exception) {
                        logger.error { "Error in timeout callback: ${e.message}" }
                    }
                }

                val timerId = api.setTimeout(delayMs, kotlinCallback)
                timerCallbacks[timerId] = callback

                return LuaValue.valueOf(timerId)
            }
        })

        // set_interval(callback, interval) - периодический вызов
        g.set("set_interval", object : TwoArgFunction() {
            override fun call(callback: LuaValue, interval: LuaValue): LuaValue {
                val intervalMs = interval.tolong()

                val kotlinCallback: () -> Unit = {
                    try {
                        callback.call()
                    } catch (e: Exception) {
                        logger.error { "Error in interval callback: ${e.message}" }
                    }
                }

                val timerId = api.setInterval(intervalMs, kotlinCallback)
                timerCallbacks[timerId] = callback

                return LuaValue.valueOf(timerId)
            }
        })

        // handle_movement(direction, roomName, exitsTable, roomId) - обработка перемещения для маппера
        g.set("handle_movement", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val dir = luaValueToUtf8String(args.checkvalue(1))
                val name = luaValueToUtf8String(args.checkvalue(2))
                val exitsTable = args.checktable(3)
                val roomId = if (args.narg() >= 4 && !args.isnil(4)) luaValueToUtf8String(args.checkvalue(4)) else null

                // Конвертируем Lua таблицу в Java List
                val exits = mutableListOf<String>()
                var i = 1
                while (true) {
                    val v = exitsTable.get(i)
                    if (v.isnil()) break
                    exits.add(luaValueToUtf8String(v))
                    i++
                }
                logger.debug { "handle_movement: dir='$dir' name='$name' exits=$exits roomId=$roomId" }

                return try {
                    val result = api.handleMovement(dir, name, exits, roomId)
                    if (result != null) {
                        CoerceJavaToLua.coerce(result)
                    } else {
                        LuaValue.NIL
                    }
                } catch (e: Exception) {
                    logger.error { "Error in handle_movement: ${e.message}" }
                    LuaValue.NIL
                }
            }
        })

        // create_room(id, name) - создание комнаты
        g.set("create_room", object : TwoArgFunction() {
            override fun call(idArg: LuaValue, nameArg: LuaValue): LuaValue {
                val id = luaValueToUtf8String(idArg)
                val name = luaValueToUtf8String(nameArg)

                return try {
                    val result = api.createRoom(id, name)
                    LuaValue.valueOf(result)
                } catch (e: Exception) {
                    logger.error { "Error in create_room: ${e.message}" }
                    LuaValue.FALSE
                }
            }
        })

        // search_rooms(query) - поиск комнат
        g.set("search_rooms", object : TwoArgFunction() {
            override fun call(query: LuaValue, unused: LuaValue): LuaValue {
                val q = luaValueToUtf8String(query)
                return try {
                    val rooms = api.searchRooms(q)
                    // Конвертируем List в Lua table
                    val table = LuaTable()
                    rooms.forEachIndexed { index, room ->
                        table.set(index + 1, CoerceJavaToLua.coerce(room))
                    }
                    table
                } catch (e: Exception) {
                    logger.error { "Error in search_rooms: ${e.message}" }
                    LuaTable()
                }
            }
        })

        // add_unexplored_exits(roomId, exitsTable) - добавление неизведанных выходов
        g.set("add_unexplored_exits", object : TwoArgFunction() {
            override fun call(roomIdValue: LuaValue, exitsTable: LuaValue): LuaValue {
                val roomId = luaValueToUtf8String(roomIdValue)
                val table = exitsTable.checktable()

                val exits = mutableListOf<String>()
                var i = 1
                while (true) {
                    val v = table.get(i)
                    if (v.isnil()) break
                    exits.add(luaValueToUtf8String(v))
                    i++
                }

                return try {
                    api.addUnexploredExits(roomId, exits)
                    LuaValue.TRUE
                } catch (e: Exception) {
                    logger.error { "Error in add_unexplored_exits: ${e.message}" }
                    LuaValue.FALSE
                }
            }
        })

        // get_current_room() - получение текущей комнаты
        g.set("get_current_room", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return try {
                    val room = api.getCurrentRoom()
                    if (room != null) {
                        CoerceJavaToLua.coerce(room)
                    } else {
                        LuaValue.NIL
                    }
                } catch (e: Exception) {
                    logger.error { "Error in get_current_room: ${e.message}" }
                    LuaValue.NIL
                }
            }
        })

        // set_current_room(roomId) - установка текущей комнаты
        g.set("set_current_room", object : TwoArgFunction() {
            override fun call(roomIdValue: LuaValue, unused: LuaValue): LuaValue {
                val roomId = luaValueToUtf8String(roomIdValue)
                return try {
                    api.setCurrentRoom(roomId)
                    LuaValue.TRUE
                } catch (e: Exception) {
                    logger.error { "Error in set_current_room: ${e.message}" }
                    LuaValue.FALSE
                }
            }
        })
    }

    override fun loadScript(scriptPath: String): Script? {
        val file = File(scriptPath)
        if (!file.exists()) {
            logger.warn { "Script not found: $scriptPath" }
            return null
        }

        return try {
            // Загружаем скрипт как InputStream - LuaJ напрямую получит UTF-8 байты
            // Это важно для правильной обработки кириллицы в строковых литералах
            file.inputStream().use { stream ->
                globals?.load(stream, "@$scriptPath", "bt", globals)?.call()
            }

            // Вызываем on_load если есть
            callFunction("on_load", api)

            Script(
                id = UUID.randomUUID().toString(),
                name = file.nameWithoutExtension,
                path = scriptPath,
                engine = this,
                enabled = true
            )
        } catch (e: Exception) {
            logger.error { "Error loading script: ${e.message}" }
            e.printStackTrace()
            null
        }
    }

    override fun execute(code: String) {
        try {
            // Конвертируем в UTF-8 байты и загружаем
            code.byteInputStream(Charsets.UTF_8).use { stream ->
                globals?.load(stream, "@inline", "bt", globals)?.call()
            }
        } catch (e: Exception) {
            logger.error { "Error executing code: ${e.message}" }
            e.printStackTrace()
        }
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        return try {
            val func = globals?.get(functionName)
            if (func != null && func.isfunction()) {
                // Конвертируем аргументы в Lua значения
                val luaArgs = args.map {
                    when (it) {
                        null -> LuaValue.NIL
                        is String -> LuaValue.valueOf(it)
                        is Number -> LuaValue.valueOf(it.toDouble())
                        is Boolean -> LuaValue.valueOf(it)
                        else -> CoerceJavaToLua.coerce(it)
                    }
                }.toTypedArray()

                func.invoke(luaArgs)?.tojstring()
            } else {
                null
            }
        } catch (e: Exception) {
            // Функция не найдена или ошибка вызова - это нормально
            null
        }
    }

    override fun shutdown() {
        globals = null
    }
}
