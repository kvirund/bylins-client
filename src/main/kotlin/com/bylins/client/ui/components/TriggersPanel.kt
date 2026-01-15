package com.bylins.client.ui.components

import mu.KotlinLogging
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.triggers.Trigger
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("TriggersPanel")
@Composable
fun TriggersPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    // Получаем все триггеры с источниками
    val triggersWithSource = remember { mutableStateOf(clientState.getAllTriggersWithSource()) }
    val triggers by clientState.triggers.collectAsState()
    val activeStack by clientState.profileManager.activeStack.collectAsState()
    val profiles by clientState.profileManager.profiles.collectAsState()

    // Обновляем при изменении триггеров, стека или профилей
    LaunchedEffect(triggers, activeStack, profiles) {
        triggersWithSource.value = clientState.getAllTriggersWithSource()
    }

    var showDialog by remember { mutableStateOf(false) }
    var editingTrigger by remember { mutableStateOf<Trigger?>(null) }
    var editingTriggerSource by remember { mutableStateOf<String?>(null) }
    val targetProfileId by clientState.panelTargetProfileId.collectAsState()  // null = база
    val colorScheme = LocalAppColorScheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(8.dp)
    ) {
        // Заголовок с кнопками
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Триггеры",
                color = colorScheme.onBackground,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        // Экспорт триггеров
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
                        fileChooser.selectedFile = File("triggers.json")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val triggerIds = triggers.map { it.id }
                                val json = clientState.exportTriggers(triggerIds)
                                fileChooser.selectedFile.writeText(json)
                                logger.info { "Triggers exported to ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Export error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.primary
                    )
                ) {
                    Text("Экспорт", color = colorScheme.onBackground)
                }

                Button(
                    onClick = {
                        // Импорт триггеров
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val json = fileChooser.selectedFile.readText()
                                val count = clientState.importTriggers(json, merge = true)
                                logger.info { "Imported $count triggers from ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Import error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.warning
                    )
                ) {
                    Text("Импорт", color = colorScheme.onBackground)
                }

                Button(
                    onClick = {
                        editingTrigger = null
                        editingTriggerSource = null
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.success
                    )
                ) {
                    Text("+ Добавить", color = colorScheme.onBackground)
                }
            }
        }

        // Селектор цели добавления
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Добавлять в:",
                color = colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            var targetExpanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { targetExpanded = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.surfaceVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = targetProfileId?.let { id -> profiles.find { it.id == id }?.name } ?: "База",
                        color = colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(" ▼", color = colorScheme.onSurface, fontSize = 10.sp)
                }

                DropdownMenu(
                    expanded = targetExpanded,
                    onDismissRequest = { targetExpanded = false }
                ) {
                    DropdownMenuItem(onClick = {
                        clientState.setPanelTargetProfileId(null)
                        targetExpanded = false
                    }) {
                        Text("База", fontFamily = FontFamily.Monospace)
                    }
                    activeStack.forEach { profileId ->
                        val profile = profiles.find { it.id == profileId }
                        if (profile != null) {
                            DropdownMenuItem(onClick = {
                                clientState.setPanelTargetProfileId(profileId)
                                targetExpanded = false
                            }) {
                                Text(profile.name, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Список триггеров (все из базы + профилей)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(triggersWithSource.value) { (trigger, source) ->
                // Формируем список доступных целей для перемещения (исключая текущий источник)
                val availableTargets = mutableListOf<Pair<String?, String>>()
                if (source != null) {
                    availableTargets.add(null to "База")
                }
                activeStack.forEach { profileId ->
                    if (profileId != source) {
                        profiles.find { it.id == profileId }?.let { profile ->
                            availableTargets.add(profileId to profile.name)
                        }
                    }
                }

                TriggerItem(
                    trigger = trigger,
                    source = source,
                    sourceName = source?.let { srcId -> profiles.find { it.id == srcId }?.name } ?: "База",
                    availableTargets = availableTargets,
                    onToggle = { id, enabled ->
                        if (source == null) {
                            // Базовый триггер
                            if (enabled) {
                                clientState.enableTrigger(id)
                            } else {
                                clientState.disableTrigger(id)
                            }
                        }
                        // TODO: для профильных триггеров пока не поддерживается toggle
                    },
                    onEdit = { triggerToEdit ->
                        editingTrigger = triggerToEdit
                        editingTriggerSource = source
                        showDialog = true
                    },
                    onDelete = { id ->
                        if (source == null) {
                            clientState.removeTrigger(id)
                        } else {
                            clientState.profileManager.removeTriggerFromProfile(source, id)
                        }
                    },
                    onMove = { targetProfileId ->
                        // Удаляем из источника
                        if (source == null) {
                            clientState.removeTrigger(trigger.id)
                        } else {
                            clientState.profileManager.removeTriggerFromProfile(source, trigger.id)
                        }
                        // Добавляем в цель
                        if (targetProfileId == null) {
                            clientState.addTrigger(trigger)
                        } else {
                            clientState.profileManager.addTriggerToProfile(targetProfileId, trigger)
                        }
                    }
                )
            }
        }

        if (triggersWithSource.value.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет триггеров",
                    color = colorScheme.divider,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    // Диалог добавления/редактирования
    if (showDialog) {
        TriggerDialog(
            trigger = editingTrigger,
            onDismiss = {
                showDialog = false
                editingTrigger = null
                editingTriggerSource = null
            },
            onSave = { trigger ->
                if (editingTrigger == null) {
                    // Новый триггер - добавляем в выбранную цель
                    if (targetProfileId == null) {
                        clientState.addTrigger(trigger)
                    } else {
                        clientState.profileManager.addTriggerToProfile(targetProfileId!!, trigger)
                    }
                } else {
                    // Редактирование - обновляем в исходном месте
                    if (editingTriggerSource == null) {
                        clientState.updateTrigger(trigger)
                    } else {
                        clientState.profileManager.updateTriggerInProfile(editingTriggerSource!!, trigger)
                    }
                }
                showDialog = false
                editingTrigger = null
                editingTriggerSource = null
            }
        )
    }
}

@Composable
private fun TriggerItem(
    trigger: Trigger,
    source: String?,        // null = база, иначе ID профиля
    sourceName: String,     // Отображаемое имя источника
    availableTargets: List<Pair<String?, String>>,  // (id или null для базы, имя)
    onToggle: (String, Boolean) -> Unit,
    onEdit: (Trigger) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (targetProfileId: String?) -> Unit  // null = переместить в базу
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = LocalAppColorScheme.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = colorScheme.surface,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Основная строка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Название, ID и источник
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = trigger.name,
                            color = if (trigger.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        // Бейдж источника с dropdown для перемещения
                        var showMoveMenu by remember { mutableStateOf(false) }
                        Box {
                            Card(
                                backgroundColor = if (source == null) colorScheme.surfaceVariant else colorScheme.secondary.copy(alpha = 0.3f),
                                elevation = 0.dp,
                                modifier = Modifier.clickable {
                                    if (availableTargets.isNotEmpty()) showMoveMenu = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sourceName,
                                        color = colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (availableTargets.isNotEmpty()) {
                                        Text(
                                            text = " ▼",
                                            color = colorScheme.onSurface,
                                            fontSize = 7.sp
                                        )
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = showMoveMenu,
                                onDismissRequest = { showMoveMenu = false }
                            ) {
                                availableTargets.forEach { (targetId, targetName) ->
                                    DropdownMenuItem(onClick = {
                                        onMove(targetId)
                                        showMoveMenu = false
                                    }) {
                                        Text("→ $targetName", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = "ID: ${trigger.id}",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Кнопки управления
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка включения/выключения
                    Switch(
                        checked = trigger.enabled,
                        onCheckedChange = { enabled ->
                            onToggle(trigger.id, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.success,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant
                        )
                    )

                    // Кнопка редактирования
                    Button(
                        onClick = { onEdit(trigger) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.primary
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✎",
                            color = colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                    }

                    // Кнопка информации
                    Button(
                        onClick = { expanded = !expanded },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (expanded) "▲" else "▼",
                            color = colorScheme.onBackground,
                            fontSize = 10.sp
                        )
                    }

                    // Кнопка удаления
                    Button(
                        onClick = { onDelete(trigger.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.error
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Развернутая информация
            if (expanded) {
                Divider(
                    color = colorScheme.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Pattern
                    InfoRow("Pattern:", trigger.pattern.pattern)

                    // Commands
                    if (trigger.commands.isNotEmpty()) {
                        InfoRow("Commands:", trigger.commands.joinToString("; "))
                    }

                    // Priority
                    InfoRow("Priority:", trigger.priority.toString())

                    // Flags
                    val flags = mutableListOf<String>()
                    if (trigger.gag) flags.add("GAG")
                    if (trigger.once) flags.add("ONCE")
                    if (trigger.colorize != null) flags.add("COLOR")
                    if (flags.isNotEmpty()) {
                        InfoRow("Flags:", flags.joinToString(", "))
                    }

                    // Colorize info
                    if (trigger.colorize != null) {
                        val colorInfo = buildString {
                            append("FG: ${trigger.colorize.foreground ?: "default"}")
                            if (trigger.colorize.background != null) {
                                append(", BG: ${trigger.colorize.background}")
                            }
                            if (trigger.colorize.bold) append(", BOLD")
                        }
                        InfoRow("Color:", colorInfo)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colorScheme = LocalAppColorScheme.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            color = colorScheme.onBackground,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
