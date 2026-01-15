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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bylins.client.ClientState
import com.bylins.client.hotkeys.Hotkey
import com.bylins.client.ui.theme.LocalAppColorScheme
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("HotkeysPanel")

@Composable
fun HotkeysPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    // Получаем все хоткеи с источниками
    val hotkeysWithSource = remember { mutableStateOf(clientState.getAllHotkeysWithSource()) }
    val hotkeys by clientState.hotkeys.collectAsState()
    val activeStack by clientState.profileManager.activeStack.collectAsState()
    val profiles by clientState.profileManager.profiles.collectAsState()

    // Обновляем при изменении хоткеев, стека или профилей
    LaunchedEffect(hotkeys, activeStack, profiles) {
        hotkeysWithSource.value = clientState.getAllHotkeysWithSource()
    }

    val ignoreNumLock by clientState.ignoreNumLock.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHotkey by remember { mutableStateOf<Hotkey?>(null) }
    var editingHotkeySource by remember { mutableStateOf<String?>(null) }
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
                text = "Горячие клавиши",
                color = colorScheme.onBackground,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Экспорт JSON
                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
                        fileChooser.selectedFile = File("hotkeys.json")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val hotkeyIds = hotkeys.map { it.id }
                                val json = clientState.exportHotkeys(hotkeyIds)
                                fileChooser.selectedFile.writeText(json)
                                logger.info { "Hotkeys exported to ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Export error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary)
                ) {
                    Text("JSON↓", color = Color.White, fontSize = 11.sp)
                }

                // Импорт JSON
                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val json = fileChooser.selectedFile.readText()
                                val count = clientState.importHotkeys(json, merge = true)
                                logger.info { "Imported $count hotkeys from ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "Import error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.primary)
                ) {
                    Text("JSON↑", color = Color.White, fontSize = 11.sp)
                }

                // Экспорт YAML
                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("YAML files", "yaml", "yml")
                        fileChooser.selectedFile = File("hotkeys.yaml")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val yaml = exportHotkeysToYaml(hotkeys)
                                fileChooser.selectedFile.writeText(yaml)
                                logger.info { "Hotkeys exported to YAML: ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "YAML export error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.warning)
                ) {
                    Text("YAML↓", color = Color.White, fontSize = 11.sp)
                }

                // Импорт YAML
                Button(
                    onClick = {
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("YAML files", "yaml", "yml")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val yamlText = fileChooser.selectedFile.readText()
                                val count = importHotkeysFromYaml(yamlText, clientState)
                                logger.info { "Imported $count hotkeys from YAML: ${fileChooser.selectedFile.absolutePath}" }
                            } catch (e: Exception) {
                                logger.error { "YAML import error: ${e.message}" }
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.warning)
                ) {
                    Text("YAML↑", color = Color.White, fontSize = 11.sp)
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.success)
                ) {
                    Text("+ Добавить", color = Color.White)
                }
            }
        }

        // Настройка NumLock
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = ignoreNumLock,
                onCheckedChange = { clientState.setIgnoreNumLock(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = colorScheme.success,
                    uncheckedColor = colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = "Игнорировать NumLock (NumPad работает независимо от состояния)",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
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

        // Диалог добавления хоткея
        if (showAddDialog) {
            HotkeyDialog(
                onDismiss = { showAddDialog = false },
                onSave = { hotkey ->
                    // Новый хоткей - добавляем в выбранную цель
                    if (targetProfileId == null) {
                        clientState.addHotkey(hotkey)
                    } else {
                        clientState.profileManager.addHotkeyToProfile(targetProfileId!!, hotkey)
                    }
                    showAddDialog = false
                }
            )
        }

        // Диалог редактирования хоткея
        if (editingHotkey != null) {
            HotkeyDialog(
                hotkey = editingHotkey,
                onDismiss = {
                    editingHotkey = null
                    editingHotkeySource = null
                },
                onSave = { hotkey ->
                    // Редактирование - обновляем в исходном месте
                    if (editingHotkeySource == null) {
                        clientState.updateHotkey(hotkey)
                    } else {
                        clientState.profileManager.updateHotkeyInProfile(editingHotkeySource!!, hotkey)
                    }
                    editingHotkey = null
                    editingHotkeySource = null
                }
            )
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Заголовок таблицы
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Комбинация",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(120.dp)
            )
            Text(
                text = "Команды",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            // Место под кнопки (Switch + Edit + Delete)
            Spacer(modifier = Modifier.width(120.dp))
        }

        Divider(color = colorScheme.divider, thickness = 1.dp)

        // Список хоткеев с источниками (отсортированный)
        val sortedHotkeysWithSource = remember(hotkeysWithSource.value) {
            hotkeysWithSource.value.sortedWith(
                compareBy<Pair<Hotkey, String?>> { !it.first.ctrl && !it.first.alt && !it.first.shift } // Сначала с модификаторами
                    .thenBy { it.first.ctrl }
                    .thenBy { it.first.alt }
                    .thenBy { it.first.shift }
                    .thenBy { it.first.getKeyCombo() }
            )
        }

        if (sortedHotkeysWithSource.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет горячих клавиш",
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sortedHotkeysWithSource, key = { it.first.id }) { (hotkey, source) ->
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

                    HotkeyRow(
                        hotkey = hotkey,
                        source = source,
                        sourceName = source?.let { srcId -> profiles.find { it.id == srcId }?.name } ?: "База",
                        availableTargets = availableTargets,
                        onToggle = { id, enabled ->
                            if (source == null) {
                                // Базовый хоткей
                                if (enabled) {
                                    clientState.enableHotkey(id)
                                } else {
                                    clientState.disableHotkey(id)
                                }
                            }
                            // TODO: для профильных хоткеев пока не поддерживается toggle
                        },
                        onEdit = { editHotkey ->
                            editingHotkey = editHotkey
                            editingHotkeySource = source
                        },
                        onDelete = { id ->
                            if (source == null) {
                                clientState.removeHotkey(id)
                            } else {
                                clientState.profileManager.removeHotkeyFromProfile(source, id)
                            }
                        },
                        onMove = { targetProfileId ->
                            // Удаляем из источника
                            if (source == null) {
                                clientState.removeHotkey(hotkey.id)
                            } else {
                                clientState.profileManager.removeHotkeyFromProfile(source, hotkey.id)
                            }
                            // Добавляем в цель
                            if (targetProfileId == null) {
                                clientState.addHotkey(hotkey)
                            } else {
                                clientState.profileManager.addHotkeyToProfile(targetProfileId, hotkey)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HotkeyRow(
    hotkey: Hotkey,
    source: String?,        // null = база, иначе ID профиля
    sourceName: String,     // Отображаемое имя источника
    availableTargets: List<Pair<String?, String>>,  // (id или null для базы, имя)
    onToggle: (String, Boolean) -> Unit,
    onEdit: (Hotkey) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (targetProfileId: String?) -> Unit  // null = переместить в базу
) {
    val colorScheme = LocalAppColorScheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Комбинация клавиш
        Text(
            text = hotkey.getKeyCombo(),
            color = if (hotkey.enabled) colorScheme.success else colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(120.dp)
        )

        // Команды
        Text(
            text = hotkey.commands.joinToString("; "),
            color = if (hotkey.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Бейдж источника с dropdown для перемещения
        var showMoveMenu by remember { mutableStateOf(false) }
        Box(modifier = Modifier.padding(end = 8.dp)) {
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

        // Кнопки управления
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Switch включения/выключения
            Switch(
                checked = hotkey.enabled,
                onCheckedChange = { enabled ->
                    onToggle(hotkey.id, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.success,
                    uncheckedThumbColor = colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(width = 40.dp, height = 24.dp)
            )

            // Кнопка редактирования
            Button(
                onClick = { onEdit(hotkey) },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colorScheme.primary
                ),
                modifier = Modifier.size(28.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "✎",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            // Кнопка удаления
            Button(
                onClick = { onDelete(hotkey.id) },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colorScheme.error
                ),
                modifier = Modifier.size(28.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Экспортирует хоткеи в YAML формат
 */
private fun exportHotkeysToYaml(hotkeys: List<Hotkey>): String {
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
    }
    val yaml = Yaml(options)

    val data = hotkeys.map { hotkey ->
        mapOf(
            "combo" to hotkey.getKeyCombo(),
            "commands" to hotkey.commands,
            "enabled" to hotkey.enabled
        )
    }

    return yaml.dump(data)
}

/**
 * Импортирует хоткеи из YAML формата
 */
@Suppress("UNCHECKED_CAST")
private fun importHotkeysFromYaml(yamlText: String, clientState: ClientState): Int {
    val yaml = Yaml()
    val data = yaml.load<List<Map<String, Any>>>(yamlText)

    var count = 0
    for (item in data) {
        try {
            val combo = item["combo"] as? String ?: continue
            val commands = (item["commands"] as? List<*>)?.mapNotNull { it?.toString() } ?: continue
            val enabled = item["enabled"] as? Boolean ?: true

            // Парсим комбинацию клавиш
            val parts = combo.split("+")
            var ctrl = false
            var alt = false
            var shift = false
            var keyName = parts.last()

            for (part in parts.dropLast(1)) {
                when (part.uppercase()) {
                    "CTRL" -> ctrl = true
                    "ALT" -> alt = true
                    "SHIFT" -> shift = true
                }
            }

            val key = Hotkey.parseKey(keyName) ?: continue

            val hotkey = Hotkey(
                id = "hk-${java.util.UUID.randomUUID().toString().take(8)}",
                key = key,
                ctrl = ctrl,
                alt = alt,
                shift = shift,
                commands = commands,
                enabled = enabled
            )

            clientState.addHotkey(hotkey)
            count++
        } catch (e: Exception) {
            logger.error { "Failed to import hotkey: ${e.message}" }
        }
    }

    return count
}
