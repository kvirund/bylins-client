package com.bylins.client.scripting.engines

import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptAPI
import com.bylins.client.scripting.ScriptEngine
import mu.KotlinLogging
import org.python.core.Py
import org.python.core.PyDictionary
import org.python.core.PyFunction
import org.python.core.PyInteger
import org.python.core.PyObject
import org.python.core.PyString
import org.python.core.PyUnicode
import org.python.util.PythonInterpreter
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger("Python")

/**
 * Python движок (использует Jython)
 * Поддерживает Python 2.7 синтаксис
 */
class PythonEngine : ScriptEngine {
    override val name: String = "python"
    override val fileExtensions: List<String> = listOf(".py")

    private var interpreter: PythonInterpreter? = null
    private lateinit var api: ScriptAPI

    // Реестр callback'ов
    private val triggerCallbacks = ConcurrentHashMap<String, PyObject>()
    private val timerCallbacks = ConcurrentHashMap<String, PyObject>()

    override fun isAvailable(): Boolean {
        return try {
            Class.forName("org.python.util.PythonInterpreter")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override fun initialize(api: ScriptAPI) {
        this.api = api

        try {
            // Настраиваем UTF-8 для Jython
            val props = Properties()
            props.setProperty("python.console.encoding", "UTF-8")
            props.setProperty("python.io.encoding", "UTF-8")
            PythonInterpreter.initialize(System.getProperties(), props, arrayOf<String>())

            interpreter = PythonInterpreter()

            // Добавляем API в контекст
            interpreter?.set("api", api)

            // Добавляем хелпер для регистрации callback'ов
            interpreter?.set("_trigger_helper", TriggerHelper())
            interpreter?.set("_timer_helper", TimerHelper())

            // Устанавливаем UTF-8 кодировку в интерпретаторе
            interpreter?.exec("""
import sys
reload(sys)
sys.setdefaultencoding('UTF-8')
""")

            // Загружаем вспомогательные функции из ресурсов
            val helperCode = javaClass.getResourceAsStream("/python_helper.py")
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                ?: throw IllegalStateException("python_helper.py not found in resources")
            interpreter?.exec(helperCode)
        } catch (e: Exception) {
            logger.error { "Error initializing Jython: ${e.message}" }
            e.printStackTrace()
        }
    }

    /**
     * Извлекает UTF-8 строку из PyObject (PyUnicode или PyString)
     */
    private fun pyObjectToUtf8String(obj: PyObject): String {
        return when (obj) {
            is PyUnicode -> {
                // PyUnicode хранит строку как Java String (UTF-16)
                obj.string
            }
            is PyString -> {
                // PyString в Jython хранит байты как Latin-1 encoded string
                // Получаем байты и декодируем как UTF-8
                val str = obj.string
                // Если строка содержит Latin-1 байты от UTF-8, конвертируем
                try {
                    String(str.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                } catch (e: Exception) {
                    str
                }
            }
            else -> {
                obj.toString()
            }
        }
    }

    /**
     * Хелпер для регистрации триггеров с Python callback'ами
     */
    inner class TriggerHelper {
        fun register(patternObj: PyObject, callback: PyObject): String {
            // DEBUG: тип Python объекта
            logger.debug { "TriggerHelper.register patternObj type=${patternObj.javaClass.simpleName}" }

            // Извлекаем UTF-8 строку из Python объекта
            val pattern = pyObjectToUtf8String(patternObj)

            // DEBUG: проверяем что пришло из Python
            val patternBytes = pattern.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }
            logger.debug { "TriggerHelper.register pattern='$pattern' bytes=[$patternBytes]" }

            val kotlinCallback: (String, Map<Int, String>) -> Unit = { line, groups ->
                try {
                    // Конвертируем groups в Python dict с UTF-8
                    val pyDict = PyDictionary()
                    groups.forEach { (index, value) ->
                        pyDict.__setitem__(PyInteger(index), PyUnicode(value))
                    }

                    // Вызываем Python callback с Unicode строками
                    callback.__call__(arrayOf(PyUnicode(line), pyDict))
                } catch (e: Exception) {
                    logger.error { "Error in trigger callback: ${e.message}" }
                }
            }

            val triggerId = api.addTrigger(pattern, kotlinCallback)
            triggerCallbacks[triggerId] = callback
            return triggerId
        }
    }

    /**
     * Хелпер для регистрации таймеров с Python callback'ами
     */
    inner class TimerHelper {
        fun registerTimeout(callback: PyObject, delay: Long): String {
            val kotlinCallback: () -> Unit = {
                try {
                    callback.__call__()
                } catch (e: Exception) {
                    logger.error { "Error in timeout callback: ${e.message}" }
                }
            }

            val timerId = api.setTimeout(delay, kotlinCallback)
            timerCallbacks[timerId] = callback
            return timerId
        }

        fun registerInterval(callback: PyObject, interval: Long): String {
            val kotlinCallback: () -> Unit = {
                try {
                    callback.__call__()
                } catch (e: Exception) {
                    logger.error { "Error in interval callback: ${e.message}" }
                }
            }

            val timerId = api.setInterval(interval, kotlinCallback)
            timerCallbacks[timerId] = callback
            return timerId
        }
    }

    override fun loadScript(scriptPath: String): Script? {
        val file = File(scriptPath)
        if (!file.exists()) {
            logger.warn { "Script not found: $scriptPath" }
            return null
        }

        return try {
            // Используем execfile для правильной обработки encoding declaration
            interpreter?.execfile(scriptPath)

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
            interpreter?.exec(code)
        } catch (e: Exception) {
            logger.error { "Error executing code: ${e.message}" }
            e.printStackTrace()
        }
    }

    override fun callFunction(functionName: String, vararg args: Any?): Any? {
        return try {
            val func = interpreter?.get(functionName)
            if (func is PyFunction) {
                // Конвертируем аргументы в Python объекты
                val pyArgs = args.map { interpreter?.eval(it?.toString() ?: "None") as org.python.core.PyObject }.toTypedArray()
                // __call__ принимает Array<PyObject>, а не vararg
                func.__call__(pyArgs)
            } else {
                null
            }
        } catch (e: Exception) {
            // Функция не найдена или ошибка вызова - это нормально
            null
        }
    }

    override fun shutdown() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            logger.debug { "Error closing interpreter: ${e.message}" }
        }
        interpreter = null
    }
}
