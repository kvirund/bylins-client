package com.bylins.client.ui.components

import mu.KotlinLogging
import androidx.compose.foundation.background
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
import com.bylins.client.aliases.Alias
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val logger = KotlinLogging.logger("AliasesPanel")
@Composable
fun AliasesPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    // Получаем все алиасы с источниками
    val aliasesWithSource = remember { mutableStateOf(clientState.getAllAliasesWithSource()) }
    val aliases by clientState.aliases.collectAsState()
    val activeStack by clientState.profileManager.activeStack.collectAsState()
    val profiles by clientState.profileManager.profiles.collectAsState()

    // Обновляем при изменении алиасов или стека
    LaunchedEffect(aliases, activeStack) {
        aliasesWithSource.value = clientState.getAllAliasesWithSource()
    }

    var showDialog by remember { mutableStateOf(false) }
    var editingAlias by remember { mutableStateOf<Alias?>(null) }
    var editingAliasSource by remember { mutableStateOf<String?>(null) }
    var targetProfileId by remember { mutableStateOf<String?>(null) }  // null = база
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
                text = "Алиасы",
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
                        // Экспорт алиасов
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
                        fileChooser.selectedFile = File("aliases.json")

                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val aliasIds = aliases.map { it.id }
                                val json = clientState.exportAliases(aliasIds)
                                fileChooser.selectedFile.writeText(json)
                                logger.info { "Aliases exported to ${fileChooser.selectedFile.absolutePath}" }
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
                    Text("Экспорт", color = Color.White)
                }

                Button(
                    onClick = {
                        // Импорт алиасов
                        val fileChooser = JFileChooser()
                        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")

                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            try {
                                val json = fileChooser.selectedFile.readText()
                                val count = clientState.importAliases(json, merge = true)
                                logger.info { "Imported $count aliases from ${fileChooser.selectedFile.absolutePath}" }
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
                    Text("Импорт", color = Color.White)
                }

                Button(
                    onClick = {
                        editingAlias = null
                        editingAliasSource = null
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.success
                    )
                ) {
                    Text("+ Добавить", color = Color.White)
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
                        targetProfileId = null
                        targetExpanded = false
                    }) {
                        Text("База", fontFamily = FontFamily.Monospace)
                    }
                    activeStack.forEach { profileId ->
                        val profile = profiles.find { it.id == profileId }
                        if (profile != null) {
                            DropdownMenuItem(onClick = {
                                targetProfileId = profileId
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

        // Список алиасов (все из базы + профилей)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(aliasesWithSource.value) { (alias, source) ->
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

                AliasItem(
                    alias = alias,
                    source = source,
                    sourceName = source?.let { srcId -> profiles.find { it.id == srcId }?.name } ?: "База",
                    availableTargets = availableTargets,
                    onToggle = { id, enabled ->
                        if (source == null) {
                            // Базовый алиас
                            if (enabled) {
                                clientState.enableAlias(id)
                            } else {
                                clientState.disableAlias(id)
                            }
                        }
                        // TODO: для профильных алиасов пока не поддерживается toggle
                    },
                    onEdit = { aliasToEdit ->
                        editingAlias = aliasToEdit
                        editingAliasSource = source
                        showDialog = true
                    },
                    onDelete = { id ->
                        if (source == null) {
                            clientState.removeAlias(id)
                        } else {
                            clientState.profileManager.removeAliasFromProfile(source, id)
                        }
                    },
                    onMove = { targetProfileId ->
                        // Удаляем из источника
                        if (source == null) {
                            clientState.removeAlias(alias.id)
                        } else {
                            clientState.profileManager.removeAliasFromProfile(source, alias.id)
                        }
                        // Добавляем в цель
                        if (targetProfileId == null) {
                            clientState.addAlias(alias)
                        } else {
                            clientState.profileManager.addAliasToProfile(targetProfileId, alias)
                        }
                    }
                )
            }
        }

        if (aliasesWithSource.value.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет алиасов",
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    // Диалог добавления/редактирования
    if (showDialog) {
        AliasDialog(
            alias = editingAlias,
            onDismiss = {
                showDialog = false
                editingAlias = null
                editingAliasSource = null
            },
            onSave = { alias ->
                if (editingAlias == null) {
                    // Новый алиас - добавляем в выбранную цель
                    if (targetProfileId == null) {
                        clientState.addAlias(alias)
                    } else {
                        clientState.profileManager.addAliasToProfile(targetProfileId!!, alias)
                    }
                } else {
                    // Редактирование - обновляем в исходном месте
                    if (editingAliasSource == null) {
                        clientState.updateAlias(alias)
                    } else {
                        clientState.profileManager.updateAliasInProfile(editingAliasSource!!, alias)
                    }
                }
                showDialog = false
                editingAlias = null
                editingAliasSource = null
            }
        )
    }
}

@Composable
private fun AliasItem(
    alias: Alias,
    source: String?,        // null = база, иначе ID профиля
    sourceName: String,     // Отображаемое имя источника
    availableTargets: List<Pair<String?, String>>,  // (id или null для базы, имя)
    onToggle: (String, Boolean) -> Unit,
    onEdit: (Alias) -> Unit,
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
                            text = alias.name,
                            color = if (alias.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        // Бейдж источника
                        Card(
                            backgroundColor = if (source == null) colorScheme.surfaceVariant else colorScheme.secondary.copy(alpha = 0.3f),
                            elevation = 0.dp
                        ) {
                            Text(
                                text = sourceName,
                                color = colorScheme.onSurface,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "ID: ${alias.id}",
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
                        checked = alias.enabled,
                        onCheckedChange = { enabled ->
                            onToggle(alias.id, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.success,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant
                        )
                    )

                    // Кнопка редактирования
                    Button(
                        onClick = { onEdit(alias) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.primary
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✎",
                            color = colorScheme.onSurface,
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
                            color = colorScheme.onSurface,
                            fontSize = 10.sp
                        )
                    }

                    // Кнопка удаления
                    Button(
                        onClick = { onDelete(alias.id) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorScheme.error
                        ),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
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
                    InfoRow("Pattern:", alias.pattern.pattern)

                    // Commands
                    if (alias.commands.isNotEmpty()) {
                        InfoRow("Commands:", alias.commands.joinToString("; "))
                    }

                    // Priority
                    InfoRow("Priority:", alias.priority.toString())

                    // Переместить в другой профиль
                    if (availableTargets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        var showMoveMenu by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Переместить в:",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Box {
                                Button(
                                    onClick = { showMoveMenu = true },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.warning),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Выбрать...", color = Color.White, fontSize = 11.sp)
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
                                            Text(targetName, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
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
            color = colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
