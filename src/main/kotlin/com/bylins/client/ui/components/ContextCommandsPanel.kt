package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bylins.client.ClientState
import com.bylins.client.contextcommands.ContextCommandRule
import com.bylins.client.contextcommands.ContextCommandTTL
import com.bylins.client.contextcommands.ContextScope
import com.bylins.client.contextcommands.ContextTriggerType
import com.bylins.client.ui.theme.LocalAppColorScheme
import java.util.UUID

@Composable
fun ContextCommandsPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    // Получаем все правила с источниками
    val rulesWithSource = remember { mutableStateOf(clientState.getAllContextRulesWithSource()) }
    val baseRules by clientState.contextCommandManager.rules.collectAsState()
    val activeStack by clientState.profileManager.activeStack.collectAsState()
    val profiles by clientState.profileManager.profiles.collectAsState()

    // Обновляем при изменении правил, стека или профилей
    LaunchedEffect(baseRules, activeStack, profiles) {
        rulesWithSource.value = clientState.getAllContextRulesWithSource()
    }

    val queue by clientState.contextCommandManager.commandQueue.collectAsState()
    val maxQueueSize by clientState.contextCommandManager.maxQueueSize.collectAsState()
    val colorScheme = LocalAppColorScheme.current
    var lastTargetProfileId by remember { mutableStateOf<String?>(null) }  // Запоминаем последний выбор

    var showDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ContextCommandRule?>(null) }
    var editingRuleSource by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(8.dp)
    ) {
        // Header with queue info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Context Commands",
                    color = colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Queue: ${queue.size}/$maxQueueSize commands",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear queue button
                Button(
                    onClick = { clientState.contextCommandManager.clearQueue() },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.warning,
                        disabledBackgroundColor = colorScheme.surfaceVariant
                    ),
                    enabled = queue.isNotEmpty()
                ) {
                    Text(
                        "Clear Queue",
                        color = if (queue.isNotEmpty()) Color.White else colorScheme.onSurfaceVariant
                    )
                }

                // Add rule button
                Button(
                    onClick = {
                        editingRule = null
                        editingRuleSource = null
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorScheme.success
                    )
                ) {
                    Text("+ Add Rule", color = Color.White)
                }
            }
        }

        Divider(color = colorScheme.divider)

        // Rules list
        if (rulesWithSource.value.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No context command rules defined.\nClick \"+ Add Rule\" to create one.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rulesWithSource.value, key = { it.first.id }) { (rule, source) ->
                    // Формируем список доступных целей для перемещения
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

                    ContextRuleItem(
                        rule = rule,
                        source = source,
                        sourceName = source?.let { srcId -> profiles.find { it.id == srcId }?.name } ?: "База",
                        availableTargets = availableTargets,
                        colorScheme = colorScheme,
                        onEdit = {
                            editingRule = rule
                            editingRuleSource = source
                            showDialog = true
                        },
                        onDelete = {
                            if (source == null) {
                                clientState.contextCommandManager.removeRule(rule.id)
                                clientState.saveConfig()
                            } else {
                                clientState.profileManager.removeContextRuleFromProfile(source, rule.id)
                            }
                            rulesWithSource.value = clientState.getAllContextRulesWithSource()
                        },
                        onToggle = { enabled ->
                            if (source == null) {
                                clientState.contextCommandManager.updateRule(rule.id) {
                                    it.copy(enabled = enabled)
                                }
                                clientState.saveConfig()
                            } else {
                                clientState.profileManager.updateContextRuleInProfile(source, rule.copy(enabled = enabled))
                            }
                            rulesWithSource.value = clientState.getAllContextRulesWithSource()
                        },
                        onMove = { targetProfileId ->
                            // Удаляем из источника
                            if (source == null) {
                                clientState.contextCommandManager.removeRule(rule.id)
                                clientState.saveConfig()
                            } else {
                                clientState.profileManager.removeContextRuleFromProfile(source, rule.id)
                            }
                            // Добавляем в цель
                            if (targetProfileId == null) {
                                clientState.contextCommandManager.addRule(rule)
                                clientState.saveConfig()
                            } else {
                                clientState.profileManager.addContextRuleToProfile(targetProfileId, rule)
                            }
                            rulesWithSource.value = clientState.getAllContextRulesWithSource()
                        }
                    )
                }
            }
        }
    }

    // Edit/Create dialog
    if (showDialog) {
        // Формируем список доступных профилей для добавления
        val availableProfiles = mutableListOf<Pair<String?, String>>(null to "База")
        activeStack.forEach { profileId ->
            profiles.find { it.id == profileId }?.let { profile ->
                availableProfiles.add(profileId to profile.name)
            }
        }

        ContextRuleDialog(
            rule = editingRule,
            clientState = clientState,
            availableProfiles = if (editingRule == null) availableProfiles else emptyList(),
            initialTargetProfileId = lastTargetProfileId,
            onDismiss = { showDialog = false },
            onSave = { rule, targetProfileId ->
                if (editingRule != null) {
                    // Editing existing rule
                    if (editingRuleSource == null) {
                        clientState.contextCommandManager.updateRule(rule.id) { rule }
                        clientState.saveConfig()
                    } else {
                        clientState.profileManager.updateContextRuleInProfile(editingRuleSource!!, rule)
                    }
                } else {
                    // Creating new rule
                    lastTargetProfileId = targetProfileId  // Запоминаем выбор
                    if (targetProfileId == null) {
                        clientState.contextCommandManager.addRule(rule)
                        clientState.saveConfig()
                    } else {
                        clientState.profileManager.addContextRuleToProfile(targetProfileId, rule)
                    }
                }
                rulesWithSource.value = clientState.getAllContextRulesWithSource()
                showDialog = false
            }
        )
    }
}

@Composable
private fun ContextRuleItem(
    rule: ContextCommandRule,
    source: String?,
    sourceName: String,
    availableTargets: List<Pair<String?, String>>,
    colorScheme: com.bylins.client.ui.theme.ColorScheme,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMove: (targetProfileId: String?) -> Unit
) {
    var showMoveMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        backgroundColor = if (rule.enabled) colorScheme.surface else colorScheme.surfaceVariant,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Source badge with move dropdown
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
                                    fontSize = 10.sp
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
                    Text(
                        text = rule.command,
                        color = if (rule.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    RuleTypeBadge(rule.triggerType, rule.scope, colorScheme)
                    // TTL только для Pattern правил
                    if (rule.triggerType is ContextTriggerType.Pattern) {
                        TtlBadge(rule.ttl, colorScheme)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Показываем паттерн или информацию о scope
                val triggerType = rule.triggerType
                val scope = rule.scope

                if (triggerType is ContextTriggerType.Pattern) {
                    Text(
                        text = "Pattern: ${triggerType.regex.pattern}",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Показываем scope если не World - теги как цветные карточки
                when (scope) {
                    is ContextScope.Room -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (scope.roomIds.isNotEmpty()) {
                                Text(
                                    text = "IDs: ${scope.roomIds.take(2).joinToString()}${if (scope.roomIds.size > 2) "..." else ""}",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            // Теги как цветные карточки
                            scope.roomTags.take(3).forEach { tag ->
                                Surface(
                                    color = colorScheme.warning.copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = colorScheme.warning,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (scope.roomTags.size > 3) {
                                Text(
                                    text = "+${scope.roomTags.size - 3}",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    is ContextScope.Zone -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Zones:",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            scope.zones.take(3).forEach { zone ->
                                Surface(
                                    color = colorScheme.secondary.copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = zone,
                                        color = colorScheme.secondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (scope.zones.size > 3) {
                                Text(
                                    text = "+${scope.zones.size - 3}",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    is ContextScope.World -> { /* Nothing to show */ }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.success,
                        uncheckedThumbColor = colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(width = 40.dp, height = 24.dp)
                )

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(backgroundColor = colorScheme.error),
                    modifier = Modifier.size(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("✕", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RuleTypeBadge(
    triggerType: ContextTriggerType,
    scope: ContextScope,
    colorScheme: com.bylins.client.ui.theme.ColorScheme
) {
    // Используем Row со spacedBy для консистентного спейсинга
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Показываем тип триггера
        val (triggerText, triggerColor) = when (triggerType) {
            is ContextTriggerType.Pattern -> "Pattern" to colorScheme.primary
            is ContextTriggerType.Permanent -> "Permanent" to colorScheme.success
        }

        Surface(
            color = triggerColor.copy(alpha = 0.2f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
        ) {
            Text(
                text = triggerText,
                color = triggerColor,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Показываем scope если не World
        if (scope !is ContextScope.World) {
            val (scopeText, scopeColor) = when (scope) {
                is ContextScope.Room -> "Room" to colorScheme.secondary
                is ContextScope.Zone -> "Zone" to colorScheme.warning
                is ContextScope.World -> "" to colorScheme.onSurfaceVariant // never reached
            }

            Surface(
                color = scopeColor.copy(alpha = 0.2f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = scopeText,
                    color = scopeColor,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TtlBadge(ttl: ContextCommandTTL, colorScheme: com.bylins.client.ui.theme.ColorScheme) {
    val text = when (ttl) {
        is ContextCommandTTL.UntilRoomChange -> "Room"
        is ContextCommandTTL.UntilZoneChange -> "Zone"
        is ContextCommandTTL.FixedTime -> "${ttl.minutes}m"
        is ContextCommandTTL.Permanent -> "Perm"
        is ContextCommandTTL.OneTime -> "1x"
    }

    Surface(
        color = colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "TTL: $text",
            color = colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ContextRuleDialog(
    rule: ContextCommandRule?,
    clientState: ClientState,
    availableProfiles: List<Pair<String?, String>> = emptyList(),
    initialTargetProfileId: String? = null,
    onDismiss: () -> Unit,
    onSave: (ContextCommandRule, String?) -> Unit
) {
    val isNew = rule == null
    var selectedTargetProfileId by remember { mutableStateOf(initialTargetProfileId) }
    val colorScheme = LocalAppColorScheme.current

    // Получаем данные из маппера для выбора зон и комнат
    val mapRooms by clientState.mapRooms.collectAsState()
    val zoneNames by clientState.zoneNames.collectAsState()
    // Собираем уникальные пары (zoneId, zoneName) для отображения
    val availableZonesWithNames = remember(mapRooms, zoneNames) {
        mapRooms.values
            .filter { !it.zone.isNullOrEmpty() }
            .map { it.zone!! to (zoneNames[it.zone] ?: it.zone) }
            .distinct()
            .sortedBy { it.second }
    }
    val visitedRooms = remember(mapRooms) {
        mapRooms.values.filter { it.visited }.sortedBy { it.name }
    }

    var command by remember { mutableStateOf(rule?.command ?: "") }
    var enabled by remember { mutableStateOf(rule?.enabled ?: true) }
    var priority by remember { mutableStateOf(rule?.priority?.toString() ?: "0") }

    // Trigger type: Pattern vs Permanent
    var selectedTriggerType by remember {
        mutableStateOf(
            when (rule?.triggerType) {
                is ContextTriggerType.Pattern -> "pattern"
                is ContextTriggerType.Permanent -> "permanent"
                null -> "pattern"
            }
        )
    }

    // Scope: Room / Zone / World
    var selectedScope by remember {
        mutableStateOf(
            when (rule?.scope) {
                is ContextScope.Room -> "room"
                is ContextScope.Zone -> "zone"
                is ContextScope.World -> "world"
                null -> "world"
            }
        )
    }

    // Pattern (for Pattern trigger type)
    var pattern by remember {
        mutableStateOf(
            (rule?.triggerType as? ContextTriggerType.Pattern)?.regex?.pattern ?: ""
        )
    }

    // Room IDs (for Room scope)
    var roomIds by remember {
        mutableStateOf(
            (rule?.scope as? ContextScope.Room)?.roomIds?.joinToString("\n") ?: ""
        )
    }

    // Room Tags (for Room scope)
    var roomTags by remember {
        mutableStateOf(
            (rule?.scope as? ContextScope.Room)?.roomTags?.joinToString(", ") ?: ""
        )
    }

    // Zones (for Zone scope)
    var zones by remember {
        mutableStateOf(
            (rule?.scope as? ContextScope.Zone)?.zones?.joinToString("\n") ?: ""
        )
    }

    // TTL
    var selectedTtl by remember {
        mutableStateOf(
            when (rule?.ttl) {
                is ContextCommandTTL.UntilRoomChange -> "room_change"
                is ContextCommandTTL.UntilZoneChange -> "zone_change"
                is ContextCommandTTL.FixedTime -> "fixed_time"
                is ContextCommandTTL.OneTime -> "one_time"
                is ContextCommandTTL.Permanent -> "permanent"
                null -> "room_change"
            }
        )
    }
    var ttlMinutes by remember {
        mutableStateOf(
            (rule?.ttl as? ContextCommandTTL.FixedTime)?.minutes?.toString() ?: "5"
        )
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 700.dp),
            backgroundColor = Color(0xFF2D2D2D),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = if (isNew) "New Context Rule" else "Edit Context Rule",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Profile selector (only for new)
                if (isNew && availableProfiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Добавить в:",
                            color = Color(0xFFBBBBBB),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        var targetExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { targetExpanded = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    backgroundColor = Color(0xFF3D3D3D),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = availableProfiles.find { it.first == selectedTargetProfileId }?.second ?: "База",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(" ▼", fontSize = 10.sp)
                            }
                            DropdownMenu(
                                expanded = targetExpanded,
                                onDismissRequest = { targetExpanded = false }
                            ) {
                                availableProfiles.forEach { (profileId, profileName) ->
                                    DropdownMenuItem(onClick = {
                                        selectedTargetProfileId = profileId
                                        targetExpanded = false
                                    }) {
                                        Text(profileName, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }

                // Error message - вынесен наверх, чтобы всегда был виден
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        color = Color(0xFFF44336).copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFF44336),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Command
                    FormField(
                        label = "Command" + if (selectedTriggerType == "pattern") " (use \$1, \$2 for pattern groups)" else "",
                        value = command,
                        onValueChange = { command = it },
                        placeholder = if (selectedTriggerType == "pattern") "look \$1" else "look mob"
                    )

                    // Trigger type selector: Pattern vs Permanent
                    Text(
                        text = "Trigger Type",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("pattern" to "Pattern", "permanent" to "Permanent").forEach { (value, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedTriggerType == value,
                                    onClick = { selectedTriggerType = value },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF4CAF50)
                                    )
                                )
                                Text(label, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    // Pattern field (only for Pattern trigger type)
                    if (selectedTriggerType == "pattern") {
                        FormField(
                            label = "Regex Pattern",
                            value = pattern,
                            onValueChange = { pattern = it },
                            placeholder = "You see (.+) here"
                        )
                    }

                    // Scope selector: Room / Zone / World
                    Text(
                        text = "Scope",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("world" to "World", "zone" to "Zone", "room" to "Room").forEach { (value, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedScope == value,
                                    onClick = { selectedScope = value },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF4CAF50)
                                    )
                                )
                                Text(label, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    // Scope-specific fields
                    when (selectedScope) {
                        "room" -> {
                            var showRoomPicker by remember { mutableStateOf(false) }
                            var roomSearchQuery by remember { mutableStateOf("") }

                            // Парсим выбранные room IDs
                            val selectedRoomIds = remember(roomIds) {
                                roomIds.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Комнаты",
                                    color = Color(0xFFBBBBBB),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Button(
                                    onClick = {
                                        roomSearchQuery = ""
                                        showRoomPicker = true
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3D3D3D)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("+ Добавить", color = Color.White, fontSize = 11.sp)
                                }
                            }

                            // Список выбранных комнат как chips
                            if (selectedRoomIds.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF1E1E1E),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        selectedRoomIds.forEach { roomId ->
                                            val room = mapRooms[roomId]
                                            // Формат: "Room (Vnum) • ZoneName"
                                            val displayName = if (room != null) {
                                                val zoneName = room.zone?.let { zoneNames[it] }
                                                val locationPart = if (!zoneName.isNullOrEmpty()) " • $zoneName" else ""
                                                "${room.name} (${room.id})$locationPart"
                                            } else {
                                                roomId
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    displayName,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        roomIds = selectedRoomIds.filter { it != roomId }.joinToString("\n")
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Text("✕", color = Color.Gray, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "Нет выбранных комнат",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Теги комнат
                            Text(
                                text = "Теги комнат (через запятую)",
                                color = Color(0xFFBBBBBB),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            OutlinedTextField(
                                value = roomTags,
                                onValueChange = { roomTags = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("shop, blacksmith, quest_npc", color = Color.Gray, fontSize = 12.sp) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    backgroundColor = Color(0xFF1E1E1E),
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                singleLine = true
                            )
                            Text(
                                text = "Команда активируется в комнатах с любым из указанных тегов",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            // Room picker dialog
                            if (showRoomPicker) {
                                Dialog(onDismissRequest = { showRoomPicker = false }) {
                                    Card(
                                        modifier = Modifier.width(450.dp).heightIn(max = 500.dp),
                                        backgroundColor = Color(0xFF2D2D2D),
                                        elevation = 8.dp
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                "Выбор комнаты",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )

                                            // Проверяем, похож ли ввод на VNUM (числовой ID)
                                            val isVnumInput = roomSearchQuery.trim().all { it.isDigit() } && roomSearchQuery.isNotBlank()
                                            val vnumAlreadySelected = isVnumInput && roomIds.lines().any { it.trim() == roomSearchQuery.trim() }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = roomSearchQuery,
                                                    onValueChange = { roomSearchQuery = it },
                                                    modifier = Modifier.weight(1f),
                                                    placeholder = { Text("Поиск или ввод VNUM...", color = Color.Gray, fontSize = 12.sp) },
                                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                                        textColor = Color.White,
                                                        backgroundColor = Color(0xFF1E1E1E),
                                                        cursorColor = Color.White,
                                                        focusedBorderColor = colorScheme.primary,
                                                        unfocusedBorderColor = Color.Gray
                                                    ),
                                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                                    singleLine = true,
                                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                                        onDone = {
                                                            if (isVnumInput && !vnumAlreadySelected) {
                                                                val vnum = roomSearchQuery.trim()
                                                                roomIds = if (roomIds.isBlank()) vnum else roomIds + "\n" + vnum
                                                                showRoomPicker = false
                                                            }
                                                        }
                                                    )
                                                )
                                                Button(
                                                    onClick = {
                                                        val vnum = roomSearchQuery.trim()
                                                        roomIds = if (roomIds.isBlank()) vnum else roomIds + "\n" + vnum
                                                        showRoomPicker = false
                                                    },
                                                    enabled = isVnumInput && !vnumAlreadySelected,
                                                    colors = ButtonDefaults.buttonColors(
                                                        backgroundColor = colorScheme.primary,
                                                        disabledBackgroundColor = colorScheme.surfaceVariant
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        "Добавить",
                                                        color = if (isVnumInput && !vnumAlreadySelected) Color.White else colorScheme.onSurfaceVariant,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            val filteredRooms = remember(roomSearchQuery, visitedRooms) {
                                                val query = roomSearchQuery.lowercase()
                                                if (query.isBlank()) visitedRooms.take(50)
                                                else visitedRooms.filter {
                                                    it.name.lowercase().contains(query) ||
                                                    it.id.lowercase().contains(query) ||
                                                    it.zone?.lowercase()?.contains(query) == true
                                                }.take(50)
                                            }

                                            androidx.compose.foundation.lazy.LazyColumn(
                                                modifier = Modifier.weight(1f).fillMaxWidth()
                                            ) {
                                                items(filteredRooms.size) { index ->
                                                    val room = filteredRooms[index]
                                                    val alreadySelected = roomIds.lines().any { it.trim() == room.id }
                                                    // Формируем строку: "Room • ZoneName" или просто "Room"
                                                    val zoneName = room.zone?.let { zoneNames[it] }
                                                    val roomTitle = room.name + (if (!zoneName.isNullOrEmpty()) " • $zoneName" else "")
                                                    // Формируем вторую строку: "ID: vnum"
                                                    val roomSubtitle = "ID: ${room.id}"
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (!alreadySelected) {
                                                                    roomIds = if (roomIds.isBlank()) room.id
                                                                              else roomIds + "\n" + room.id
                                                                }
                                                                showRoomPicker = false
                                                            }
                                                            .padding(vertical = 2.dp),
                                                        color = if (alreadySelected) Color(0xFF3D3D3D) else Color.Transparent
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp)) {
                                                            Text(
                                                                roomTitle,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 12.sp,
                                                                color = if (alreadySelected) Color.Gray else Color.White
                                                            )
                                                            Text(
                                                                roomSubtitle,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 10.sp,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(onClick = { showRoomPicker = false }) {
                                                    Text("Закрыть", color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "zone" -> {
                            var showZonePicker by remember { mutableStateOf(false) }
                            var zoneSearchQuery by remember { mutableStateOf("") }

                            // Парсим выбранные zone IDs
                            val selectedZoneIds = remember(zones) {
                                zones.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            }

                            // Создаём map zone ID -> zone name для быстрого поиска
                            val zoneIdToName = remember(availableZonesWithNames) {
                                availableZonesWithNames.toMap()
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Зоны",
                                    color = Color(0xFFBBBBBB),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Button(
                                    onClick = {
                                        zoneSearchQuery = ""
                                        showZonePicker = true
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3D3D3D)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("+ Добавить", color = Color.White, fontSize = 11.sp)
                                }
                            }

                            // Список выбранных зон
                            if (selectedZoneIds.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF1E1E1E),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        selectedZoneIds.forEach { zoneId ->
                                            val savedZoneName = zoneIdToName[zoneId]
                                            val displayName = if (savedZoneName != null && savedZoneName != zoneId) {
                                                "$savedZoneName ($zoneId)"
                                            } else {
                                                zoneId
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    displayName,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        zones = selectedZoneIds.filter { it != zoneId }.joinToString("\n")
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Text("✕", color = Color.Gray, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "Нет выбранных зон",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Zone picker dialog
                            if (showZonePicker) {
                                Dialog(onDismissRequest = { showZonePicker = false }) {
                                    Card(
                                        modifier = Modifier.width(400.dp).heightIn(max = 400.dp),
                                        backgroundColor = Color(0xFF2D2D2D),
                                        elevation = 8.dp
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                "Выбор зоны",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            OutlinedTextField(
                                                value = zoneSearchQuery,
                                                onValueChange = { zoneSearchQuery = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = { Text("Поиск зоны...", color = Color.Gray, fontSize = 12.sp) },
                                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                                    textColor = Color.White,
                                                    backgroundColor = Color(0xFF1E1E1E),
                                                    cursorColor = Color.White,
                                                    focusedBorderColor = Color(0xFF4CAF50),
                                                    unfocusedBorderColor = Color.Gray
                                                ),
                                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                                singleLine = true
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            val filteredZones = remember(zoneSearchQuery, availableZonesWithNames) {
                                                val query = zoneSearchQuery.lowercase()
                                                if (query.isBlank()) availableZonesWithNames
                                                else availableZonesWithNames.filter { (zoneId, name) ->
                                                    zoneId.lowercase().contains(query) ||
                                                    name.lowercase().contains(query)
                                                }
                                            }

                                            androidx.compose.foundation.lazy.LazyColumn(
                                                modifier = Modifier.weight(1f).fillMaxWidth()
                                            ) {
                                                items(filteredZones.size) { index ->
                                                    val (zoneId, name) = filteredZones[index]
                                                    val alreadySelected = zones.lines().any { it.trim() == zoneId }
                                                    // Показываем "ZoneName (ZoneID)" формат
                                                    val displayText = if (name != zoneId) "$name ($zoneId)" else zoneId
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (!alreadySelected) {
                                                                    zones = if (zones.isBlank()) zoneId
                                                                            else zones + "\n" + zoneId
                                                                }
                                                                showZonePicker = false
                                                            }
                                                            .padding(vertical = 2.dp),
                                                        color = if (alreadySelected) Color(0xFF3D3D3D) else Color.Transparent
                                                    ) {
                                                        Text(
                                                            displayText,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 12.sp,
                                                            color = if (alreadySelected) Color.Gray else Color.White,
                                                            modifier = Modifier.padding(8.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(onClick = { showZonePicker = false }) {
                                                    Text("Закрыть", color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TTL selector (only for Pattern trigger type)
                    if (selectedTriggerType == "pattern") {
                        Text(
                            text = "Time To Live (TTL)",
                            color = Color(0xFFBBBBBB),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "room_change" to "Room",
                                "zone_change" to "Zone",
                                "fixed_time" to "Time",
                                "permanent" to "Perm",
                                "one_time" to "1x"
                            ).forEach { (value, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedTtl == value,
                                        onClick = { selectedTtl = value },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF4CAF50)
                                        )
                                    )
                                    Text(label, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        if (selectedTtl == "fixed_time") {
                            FormField(
                                label = "TTL Minutes",
                                value = ttlMinutes,
                                onValueChange = { ttlMinutes = it.filter { c -> c.isDigit() } },
                                placeholder = "5"
                            )
                        }
                    }

                    // Priority and enabled - fixed layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.width(100.dp)) {
                            Text(
                                text = "Priority",
                                color = Color(0xFFBBBBBB),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            OutlinedTextField(
                                value = priority,
                                onValueChange = { priority = it.filter { c -> c.isDigit() || c == '-' } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    backgroundColor = Color(0xFF1E1E1E),
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                singleLine = true
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Checkbox(
                                checked = enabled,
                                onCheckedChange = { enabled = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF4CAF50)
                                )
                            )
                            Text("Enabled", color = Color.White, fontSize = 12.sp)
                        }
                    }

                }

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Validate
                            if (command.isBlank()) {
                                errorMessage = "Command is required"
                                return@Button
                            }

                            // Build triggerType
                            val actualTriggerType: ContextTriggerType = try {
                                when (selectedTriggerType) {
                                    "pattern" -> {
                                        if (pattern.isBlank()) {
                                            errorMessage = "Pattern is required"
                                            return@Button
                                        }
                                        ContextTriggerType.Pattern(pattern.toRegex())
                                    }
                                    "permanent" -> ContextTriggerType.Permanent
                                    else -> {
                                        errorMessage = "Invalid trigger type"
                                        return@Button
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "Invalid pattern: ${e.message}"
                                return@Button
                            }

                            // Build scope
                            val actualScope: ContextScope = when (selectedScope) {
                                "room" -> {
                                    val ids = roomIds.lines().filter { it.isNotBlank() }.toSet()
                                    val tags = roomTags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                                    if (ids.isEmpty() && tags.isEmpty()) {
                                        errorMessage = "At least one room ID or tag is required"
                                        return@Button
                                    }
                                    ContextScope.Room(roomIds = ids, roomTags = tags)
                                }
                                "zone" -> {
                                    val zoneList = zones.lines().filter { it.isNotBlank() }.toSet()
                                    if (zoneList.isEmpty()) {
                                        errorMessage = "At least one zone is required"
                                        return@Button
                                    }
                                    ContextScope.Zone(zoneList)
                                }
                                "world" -> ContextScope.World
                                else -> ContextScope.World
                            }

                            // TTL only for Pattern rules
                            val ttl = if (selectedTriggerType == "pattern") {
                                when (selectedTtl) {
                                    "room_change" -> ContextCommandTTL.UntilRoomChange
                                    "zone_change" -> ContextCommandTTL.UntilZoneChange
                                    "fixed_time" -> ContextCommandTTL.FixedTime(ttlMinutes.toIntOrNull() ?: 5)
                                    "permanent" -> ContextCommandTTL.Permanent
                                    "one_time" -> ContextCommandTTL.OneTime
                                    else -> ContextCommandTTL.UntilRoomChange
                                }
                            } else {
                                // For Permanent rules, TTL is determined by scope
                                when (actualScope) {
                                    is ContextScope.Room -> ContextCommandTTL.UntilRoomChange
                                    is ContextScope.Zone -> ContextCommandTTL.UntilZoneChange
                                    is ContextScope.World -> ContextCommandTTL.Permanent
                                }
                            }

                            val newRule = ContextCommandRule(
                                id = rule?.id ?: UUID.randomUUID().toString(),
                                enabled = enabled,
                                triggerType = actualTriggerType,
                                scope = actualScope,
                                command = command,
                                ttl = ttl,
                                priority = priority.toIntOrNull() ?: 0
                            )

                            onSave(newRule, selectedTargetProfileId)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text(if (isNew) "Create" else "Save", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                backgroundColor = Color(0xFF1E1E1E),
                cursorColor = Color.White,
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color.Gray,
                disabledTextColor = Color.Gray,
                disabledBorderColor = Color.DarkGray
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            singleLine = true
        )
    }
}
