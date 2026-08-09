package com.bylins.client.aicontrol

import com.bylins.client.plugins.PluginBase
import com.bylins.client.plugins.PluginPermission
import com.bylins.client.plugins.events.EventPriority
import com.bylins.client.plugins.events.LineReceivedEvent
import com.bylins.client.plugins.ui.PluginUINode
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Конфигурация плагина (plugins/ai-control/config.json).
 */
@Serializable
data class AiControlConfig(
    val port: Int = 4747,
    /** Мастер-токен: им внешний агент открывает себе сессию. */
    val masterToken: String = "",
    /** Поднимать сервер при включении плагина (если выдано разрешение на порт). */
    val autoStart: Boolean = true,
    /** Сколько строк вывода держать для «догоняющего» чтения. */
    val journalCapacity: Int = 5000,
    /** Через сколько минут простоя закрывать сессию агента. */
    val idleTimeoutMinutes: Int = 5,
)

/**
 * Плагин управления сессиями ИИ.
 *
 * Даёт внешним агентам собственные контексты: чтение вывода «с прошлого раза»,
 * отправку команд с ожиданием ответа и (с разрешения пользователя) управление
 * самим клиентом. Игрок видит происходящее во вкладке-логе и командой #ai.
 */
class AiControlPlugin : PluginBase() {

    private lateinit var journal: OutputJournal
    private lateinit var sessions: SessionManager
    private var server: AiHttpServer? = null
    private var config = AiControlConfig()

    // Ждём ли ещё автозапуска (снимается после старта или ручного #ai stop:
    // если игрок остановил сервер сам, поднимать его обратно нельзя)
    private var autoStartPending = true
    private var lastPanelFingerprint: String? = null
    private var autoStartTimer: com.bylins.client.plugins.TimerHandle? = null

    private val logTabId = "ai_control_log"

    override fun onEnable() {
        config = loadConfig<AiControlConfig>() ?: AiControlConfig()
        // Мастер-токен генерируем один раз и сохраняем — агенту его даёт игрок
        if (config.masterToken.isBlank()) {
            config = config.copy(masterToken = UUID.randomUUID().toString().replace("-", ""))
            saveConfig(config)
        }

        journal = OutputJournal(config.journalCapacity)
        sessions = SessionManager(api, journal, config.idleTimeoutMinutes * 60_000L)

        api.createOutputTab(logTabId, "ИИ")
        audit("Плагин управления ИИ включён")

        // Весь вывод сервера идёт в журнал: это источник для «что произошло
        // с моей прошлой попытки». Низкий приоритет — не мешаем геймплейным
        // подписчикам и не влияем на gag.
        api.subscribe(LineReceivedEvent::class.java, EventPriority.MONITOR) { event ->
            journal.append(event.line, event.timestamp)
        }

        // Периодическая уборка «мёртвых» контекстов
        api.setInterval(60_000) {
            sessions.evictIdle().forEach { id -> audit("Сессия $id закрыта по таймауту") }
        }

        // Правая панель: состояние сервера и список подключённых ИИ.
        // Сессии появляются извне (по HTTP), поэтому опрашиваем состояние —
        // но перерисовываем ТОЛЬКО при изменениях: иначе кнопки пересоздаются
        // каждый тик вместе с обработчиками, и клик теряется.
        api.setInterval(3000) { refreshStatusPanel() }
        refreshStatusPanel(force = true)

        registerCommands()

        // Автозапуск. Плагины включаются РАНЬШЕ, чем клиент загружает конфиг с
        // выданными разрешениями, поэтому одной попытки на onEnable мало: ждём
        // появления права и стартуем, как только оно есть. Тот же механизм
        // поднимает сервер, если пользователь выдал право уже во время работы.
        if (config.autoStart) {
            startServer(quiet = true)
            if (server == null) scheduleAutoStart()
        }
    }

    /** Повторяет попытку автозапуска, пока не появится разрешение на порт. */
    private fun scheduleAutoStart() {
        autoStartTimer = api.setInterval(3000) {
            if (!autoStartPending || server != null) return@setInterval
            if (api.hasPermission(PluginPermission.NETWORK_SERVER)) {
                startServer(quiet = true)
                cancelAutoStart()
            }
        }
    }

    private fun cancelAutoStart() {
        autoStartPending = false
        autoStartTimer?.let { runCatching { api.cancelTimer(it) } }
        autoStartTimer = null
    }

    override fun onDisable() {
        cancelAutoStart()
        server?.stop()
        server = null
        if (::sessions.isInitialized) sessions.closeAll()
        audit("Плагин управления ИИ выключен")
    }

    // --- CLI игрока ---

    private fun registerCommands() {
        api.createAlias(Regex("^#ai\\s*(.*)$")) { _, groups ->
            val args = (groups.getOrNull(1) ?: "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            handleCommand(args)
            true // команда наша — на сервер не отправляем
        }
    }

    private fun handleCommand(args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            null, "status" -> showStatus()
            "list" -> showSessions()
            "start" -> startServer()
            "stop" -> stopServer()
            "token" -> api.echo("[ИИ] Мастер-токен: ${config.masterToken}")
            "kick" -> withSession(args) { s ->
                sessions.close(s.id)
                api.echo("[ИИ] Сессия ${s.name} (${s.id}) закрыта")
            }
            "mute" -> withSession(args) { s ->
                s.muted = !s.muted
                api.echo("[ИИ] Сессия ${s.name}: ${if (s.muted) "заглушена" else "снова может слать команды"}")
            }
            "take" -> withSession(args) { s ->
                sessions.grantWriteLease(s.id)
                api.echo("[ИИ] Право отправлять команды передано сессии ${s.name}")
            }
            else -> showHelp()
        }
    }

    private fun withSession(args: List<String>, action: (AiSession) -> Unit) {
        val id = args.getOrNull(1)
        if (id == null) {
            api.echo("[ИИ] Укажите id сессии (см. #ai list)")
            return
        }
        val session = sessions.get(id)
        if (session == null) {
            api.echo("[ИИ] Сессия '$id' не найдена")
            return
        }
        action(session)
    }

    private fun showStatus() {
        val running = server?.isRunning == true
        api.echo("[ИИ] Сервер: ${if (running) "работает на 127.0.0.1:${config.port}" else "остановлен"}")
        api.echo("[ИИ] Разрешения: " + listOf(
            PluginPermission.NETWORK_SERVER to "порт",
            PluginPermission.CLIENT_CONTROL to "клиент",
            PluginPermission.CONNECTION_CONTROL to "подключение"
        ).joinToString(", ") { (perm, label) ->
            "$label=${if (api.hasPermission(perm)) "да" else "НЕТ"}"
        })
        api.echo("[ИИ] Журнал вывода: ${journal.size} строк (курсор ${journal.headSeq})")
        showSessions()
    }

    private fun showSessions() {
        val all = sessions.all()
        if (all.isEmpty()) {
            api.echo("[ИИ] Подключённых агентов нет")
            return
        }
        api.echo("[ИИ] Контексты (${all.size}):")
        all.forEach { s ->
            val flags = buildList {
                if (s.hasWriteLease) add("пишет")
                if (s.muted) add("заглушен")
            }.joinToString(",").ifEmpty { "только чтение" }
            api.echo("  ${s.id}  ${s.name}  [$flags]  команд: ${s.stats.commandsSent}, триггеров: ${s.stats.triggers}")
        }
    }

    private fun showHelp() {
        api.echo("[ИИ] Команды:")
        api.echo("  #ai status        - состояние сервера и сессий")
        api.echo("  #ai list          - список подключённых агентов")
        api.echo("  #ai start | stop  - запустить/остановить локальный сервер")
        api.echo("  #ai token         - показать мастер-токен для подключения")
        api.echo("  #ai kick <id>     - закрыть контекст (снимает его триггеры)")
        api.echo("  #ai mute <id>     - запретить/разрешить отправку команд")
        api.echo("  #ai take <id>     - передать право отправки команд")
    }

    // --- Сервер ---

    private fun startServer(quiet: Boolean = false) {
        if (server?.isRunning == true) {
            if (!quiet) api.echo("[ИИ] Сервер уже запущен")
            return
        }
        if (!api.hasPermission(PluginPermission.NETWORK_SERVER)) {
            if (!quiet) {
                api.echo("[ИИ] Нет разрешения «Локальный сетевой порт» — выдайте его в настройках плагинов")
            }
            audit("Автозапуск пропущен: нет разрешения на локальный порт")
            return
        }
        try {
            val srv = AiHttpServer(
                api = api,
                sessions = sessions,
                journal = journal,
                masterToken = config.masterToken,
                port = config.port,
                audit = ::audit
            )
            srv.start()
            server = srv
            cancelAutoStart()
            api.echo("[ИИ] Сервер запущен: 127.0.0.1:${config.port} (мастер-токен: #ai token)")
            audit("Сервер запущен на порту ${config.port}")
        } catch (e: Exception) {
            api.echo("[ИИ] Не удалось запустить сервер: ${e.message}")
            logger.error("Failed to start AI server: ${e.message}")
        }
    }

    private fun stopServer() {
        cancelAutoStart()
        server?.stop()
        server = null
        sessions.closeAll()
        api.echo("[ИИ] Сервер остановлен, контексты закрыты")
        audit("Сервер остановлен")
    }

    /**
     * Перерисовывает блок в правой панели: сервер, порт и список контекстов
     * с кнопками «Стоп» (закрыть) и «Дать перо» (передать право записи).
     */
    private fun refreshStatusPanel(force: Boolean = false) {
        // Отпечаток видимого состояния: пока он не менялся, панель не трогаем
        val fingerprint = buildString {
            append(server?.isRunning == true).append('|')
            sessions.all().forEach { s ->
                append(s.id).append(s.muted).append(s.hasWriteLease)
                    .append(s.stats.commandsSent).append(s.stats.triggers).append(';')
            }
        }
        if (!force && fingerprint == lastPanelFingerprint) return
        lastPanelFingerprint = fingerprint

        val nodes = mutableListOf<PluginUINode>()
        val running = server?.isRunning == true
        val all = sessions.all()

        nodes += PluginUINode.Text(
                text = if (running) "Сервер: 127.0.0.1:${config.port}" else "Сервер остановлен",
                style = PluginUINode.TextStyle.SUBTITLE
            )
            nodes += PluginUINode.Button(
                text = if (running) "Остановить сервер" else "Запустить сервер",
                onClick = { if (running) stopServer() else startServer() }
            )

            if (all.isEmpty()) {
                nodes += PluginUINode.Text(text = "Агентов нет", style = PluginUINode.TextStyle.CAPTION)
            } else {
                all.forEach { s ->
                    val mark = when {
                        s.muted -> "заглушен"
                        s.hasWriteLease -> "пишет"
                        else -> "читает"
                    }
                    nodes += PluginUINode.Divider()
                    nodes += PluginUINode.Text(text = "${s.name} [$mark]", style = PluginUINode.TextStyle.SUBTITLE)
                    nodes += PluginUINode.Text(
                        text = "команд: ${s.stats.commandsSent}, триггеров: ${s.stats.triggers}",
                        style = PluginUINode.TextStyle.CAPTION
                    )
                    nodes += PluginUINode.Row(
                        children = listOf(
                            PluginUINode.Button(
                                text = "Отключить",
                                onClick = {
                                    sessions.close(s.id)
                                    audit("[${s.name}] отключён игроком из панели")
                                    refreshStatusPanel(force = true)
                                }
                            ),
                            PluginUINode.Button(
                                text = if (s.muted) "Разрешить" else "Заглушить",
                                onClick = {
                                    s.muted = !s.muted
                                    audit("[${s.name}] ${if (s.muted) "заглушен" else "снова может слать команды"}")
                                    refreshStatusPanel(force = true)
                                }
                            ),
                            PluginUINode.Button(
                                text = "Управление",
                                enabled = !s.hasWriteLease,
                                onClick = {
                                    sessions.grantWriteLease(s.id)
                                    audit("[${s.name}] получил право отправлять команды")
                                    refreshStatusPanel(force = true)
                                }
                            )
                        )
                    )
            }
        }

        api.addStatusPanel(
            id = "sessions",
            label = "ИИ-агенты",        // заголовок и сворачивание рисует статус-панель
            content = PluginUINode.Column(children = nodes),
            order = Int.MAX_VALUE       // всегда внизу правой панели
        )
    }

    /** Пишет строку в лог-вкладку: игрок видит, что делают агенты. */
    private fun audit(message: String) {
        val stamp = java.time.LocalTime.now().withNano(0).toString()
        api.appendToOutputTab(logTabId, "[$stamp] $message")
    }
}
