package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.bylins.client.ClientState
import com.bylins.client.contextcommands.ContextCommand
import com.bylins.client.contextcommands.ContextCommandSource
import com.bylins.client.ui.theme.LocalAppColorScheme

/**
 * Context command bar displayed above the input field.
 * Shows active context commands that can be executed with Alt+1-0.
 */
@Composable
fun ContextCommandBar(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val commands by clientState.contextCommandManager.commandQueue.collectAsState()
    var showPopup by remember { mutableStateOf(false) }
    val colorScheme = LocalAppColorScheme.current

    // Don't show bar if no commands
    if (commands.isEmpty()) return

    // Reverse to show freshest first
    val displayCommands = commands.asReversed()

    // Horizontal row of chips (floating, no background)
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .clickable { showPopup = !showPopup },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show first 10 commands (or less)
        displayCommands.take(10).forEachIndexed { index, command ->
            ContextCommandChip(
                command = command,
                index = index,
                onClick = {
                    clientState.contextCommandManager.executeCommand(index)
                }
            )
        }

        // Show indicator if there are more commands
        if (displayCommands.size > 10) {
            Text(
                text = "+${displayCommands.size - 10}",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }

    // Popup with full list
    if (showPopup) {
        ContextCommandPopup(
            commands = displayCommands,
            onDismiss = { showPopup = false },
            onExecute = { index ->
                clientState.contextCommandManager.executeCommand(index)
                showPopup = false
            },
            onRemove = { id ->
                clientState.contextCommandManager.removeCommand(id)
            }
        )
    }
}

@Composable
private fun ContextCommandChip(
    command: ContextCommand,
    index: Int,
    onClick: () -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    val hotkeyText = if (index < 10) {
        val key = if (index == 9) 0 else index + 1
        "[Alt+$key]"
    } else {
        "[${index + 1}]"
    }

    // Chip styled like OutlinedTextField label
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hotkeyText,
            color = colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = command.command,
            color = colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ContextCommandPopup(
    commands: List<ContextCommand>,
    onDismiss: () -> Unit,
    onExecute: (Int) -> Unit,
    onRemove: (String) -> Unit
) {
    val colorScheme = LocalAppColorScheme.current

    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 600.dp)
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Context Commands (${commands.size})",
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                Divider()

                // Command list
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    commands.forEachIndexed { index, command ->
                        ContextCommandPopupItem(
                            command = command,
                            index = index,
                            onExecute = { onExecute(index) },
                            onRemove = { onRemove(command.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextCommandPopupItem(
    command: ContextCommand,
    index: Int,
    onExecute: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = LocalAppColorScheme.current
    val hotkeyText = if (index < 10) {
        val key = if (index == 9) 0 else index + 1
        "Alt+$key"
    } else {
        "${index + 1}"
    }

    val sourceText = when (val source = command.source) {
        is ContextCommandSource.Pattern -> "pattern: ${source.matchedText.take(30)}"
        is ContextCommandSource.RoomBased -> "room: ${source.roomId}"
        is ContextCommandSource.ZoneBased -> "zone: ${source.zone}"
        is ContextCommandSource.Manual -> if (source.description.isNotEmpty()) source.description else "manual"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colorScheme.background)
            .clickable(onClick = onExecute)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[$hotkeyText]",
                    color = colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = command.command,
                    color = colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = sourceText,
                color = colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Text(
                text = "×",
                color = colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
        }
    }
}
