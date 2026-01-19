package com.bylins.client.ui.components

import mu.KotlinLogging
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.Surface
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
    var lastTargetProfileId by remember { mutableStateOf<String?>(null) }  // Запоминаем последний выбор
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
        // Формируем список доступных профилей для добавления
        val availableProfiles = mutableListOf<Pair<String?, String>>(null to "База")
        activeStack.forEach { profileId ->
            profiles.find { it.id == profileId }?.let { profile ->
                availableProfiles.add(profileId to profile.name)
            }
        }

        TriggerDialog(
            trigger = editingTrigger,
            availableProfiles = if (editingTrigger == null) availableProfiles else emptyList(),
            initialTargetProfileId = lastTargetProfileId,
            onDismiss = {
                showDialog = false
                editingTrigger = null
                editingTriggerSource = null
            },
            onSave = { trigger, targetProfileId ->
                if (editingTrigger == null) {
                    // Новый триггер - добавляем в выбранную цель
                    lastTargetProfileId = targetProfileId  // Запоминаем выбор
                    if (targetProfileId == null) {
                        clientState.addTrigger(trigger)
                    } else {
                        clientState.profileManager.addTriggerToProfile(targetProfileId, trigger)
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
    val colorScheme = LocalAppColorScheme.current
    var showMoveMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        backgroundColor = if (trigger.enabled) colorScheme.surface else colorScheme.surfaceVariant,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Первая строка: бейдж источника, имя триггера, флаги, кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Бейдж источника с dropdown для перемещения (цветной, как в ContextCommands)
                Box {
                    Surface(
                        modifier = Modifier.clickable {
                            if (availableTargets.isNotEmpty()) showMoveMenu = true
                        },
                        color = if (source == null) colorScheme.primary.copy(alpha = 0.2f)
                                else colorScheme.secondary.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sourceName,
                                color = if (source == null) colorScheme.primary else colorScheme.secondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (availableTargets.isNotEmpty()) {
                                Text(
                                    text = " ▼",
                                    color = if (source == null) colorScheme.primary else colorScheme.secondary,
                                    fontSize = 8.sp
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

                // Имя триггера
                Text(
                    text = trigger.name,
                    color = if (trigger.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                // Флаги как карточки
                if (trigger.gag) {
                    Surface(
                        color = colorScheme.warning.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Text("GAG", color = colorScheme.warning, fontSize = 10.sp,
                             modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (trigger.once) {
                    Surface(
                        color = colorScheme.secondary.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Text("1x", color = colorScheme.secondary, fontSize = 10.sp,
                             modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (trigger.colorize != null) {
                    Surface(
                        color = colorScheme.success.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Text("CLR", color = colorScheme.success, fontSize = 10.sp,
                             modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                // Кнопки управления
                Switch(
                    checked = trigger.enabled,
                    onCheckedChange = { enabled -> onToggle(trigger.id, enabled) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.success,
                        uncheckedThumbColor = colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(width = 40.dp, height = 24.dp)
                )

                Button(
                    onClick = { onEdit(trigger) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary),
                    modifier = Modifier.size(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("✎", color = Color.White, fontSize = 12.sp)
                }

                Button(
                    onClick = { onDelete(trigger.id) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.error),
                    modifier = Modifier.size(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("✕", color = Color.White, fontSize = 12.sp)
                }
            }

            // Вторая строка: паттерн (всегда видим)
            Text(
                text = trigger.pattern.pattern,
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Третья строка: команды (если есть)
            if (trigger.commands.isNotEmpty()) {
                Text(
                    text = "→ ${trigger.commands.joinToString("; ")}",
                    color = colorScheme.secondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

