package com.bylins.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState

@Composable
fun ConnectionPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val connectionProfiles by clientState.connectionProfiles.collectAsState()
    val currentProfileId by clientState.currentProfileId.collectAsState()
    val isConnected by clientState.isConnected.collectAsState()
    val errorMessage by clientState.errorMessage.collectAsState()

    var profileMenuExpanded by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<com.bylins.client.connection.ConnectionProfile?>(null) }

    val currentProfile = connectionProfiles.find { it.id == currentProfileId }

    Column(modifier = modifier) {
        if (isConnected) {
            // Компактный вид при подключении
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = "Connected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = currentProfile?.name ?: "Подключено",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${currentProfile?.host ?: ""}:${currentProfile?.port ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentProfile?.encoding ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { clientState.disconnect() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = "Disconnect")
                    Spacer(Modifier.width(4.dp))
                    Text("Отключиться")
                }
            }
        } else {
            // Расширенный вид при отключении - одна строка
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dropdown выбора профиля - занимает всё доступное место
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = if (currentProfile != null) {
                            "${currentProfile.name} — ${currentProfile.host}:${currentProfile.port} (${currentProfile.encoding})"
                        } else {
                            "Выберите профиль"
                        },
                        onValueChange = { },
                        label = { Text("Профиль подключения") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                if (profileMenuExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        connectionProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${profile.host}:${profile.port} (${profile.encoding})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    clientState.setCurrentProfile(profile.id)
                                    profileMenuExpanded = false
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { profileMenuExpanded = true }
                    )
                }

                // Кнопка добавления профиля
                IconButton(
                    onClick = {
                        editingProfile = null
                        showProfileDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить профиль")
                }

                // Кнопка редактирования
                IconButton(
                    onClick = {
                        editingProfile = currentProfile
                        showProfileDialog = true
                    },
                    enabled = currentProfile != null
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                }

                // Кнопка удаления
                IconButton(
                    onClick = {
                        currentProfileId?.let { clientState.removeConnectionProfile(it) }
                    },
                    enabled = currentProfileId != null && connectionProfiles.size > 1
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }

                // Кнопка подключения
                Button(
                    onClick = {
                        if (currentProfile != null) {
                            clientState.connect(currentProfile.host, currentProfile.port)
                        }
                    },
                    enabled = currentProfile != null
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Connect")
                    Spacer(Modifier.width(4.dp))
                    Text("Подключиться")
                }
            }
        }

        // Показываем ошибку если есть
        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    // Диалог создания/редактирования профиля
    if (showProfileDialog) {
        ProfileDialog(
            profile = editingProfile,
            onDismiss = {
                showProfileDialog = false
                editingProfile = null
            },
            onSave = { profile ->
                if (editingProfile != null) {
                    clientState.updateConnectionProfile(profile)
                } else {
                    clientState.addConnectionProfile(profile)
                    clientState.setCurrentProfile(profile.id)
                }
                showProfileDialog = false
                editingProfile = null
            }
        )
    }
}
