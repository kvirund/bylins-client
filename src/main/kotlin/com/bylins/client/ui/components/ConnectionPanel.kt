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

    var host by remember { mutableStateOf("bylins.su") }
    var port by remember { mutableStateOf("4000") }
    var encoding by remember { mutableStateOf(clientState.encoding) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var encodingMenuExpanded by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<com.bylins.client.connection.ConnectionProfile?>(null) }

    val availableEncodings = listOf("UTF-8", "windows-1251", "KOI8-R", "ISO-8859-1")

    // Синхронизация с выбранным профилем
    LaunchedEffect(currentProfileId) {
        val profile = connectionProfiles.find { it.id == currentProfileId }
        if (profile != null) {
            host = profile.host
            port = profile.port.toString()
            encoding = profile.encoding
            clientState.setEncoding(profile.encoding)
        }
    }

    Column(modifier = modifier) {
        // Первая строка: выбор профиля + управление
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dropdown выбора профиля
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = connectionProfiles.find { it.id == currentProfileId }?.name ?: "Выберите профиль",
                    onValueChange = { },
                    label = { Text("Профиль") },
                    enabled = !isConnected,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = profileMenuExpanded && !isConnected,
                    onDismissRequest = { profileMenuExpanded = false }
                ) {
                    connectionProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.getDisplayName()) },
                            onClick = {
                                clientState.setCurrentProfile(profile.id)
                                profileMenuExpanded = false
                            }
                        )
                    }
                }
                if (!isConnected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { profileMenuExpanded = true }
                    )
                }
            }

            // Кнопка добавления профиля
            IconButton(
                onClick = {
                    editingProfile = null
                    showProfileDialog = true
                },
                enabled = !isConnected
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить профиль")
            }

            // Кнопка редактирования текущего профиля
            IconButton(
                onClick = {
                    editingProfile = connectionProfiles.find { it.id == currentProfileId }
                    showProfileDialog = true
                },
                enabled = !isConnected && currentProfileId != null
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать профиль")
            }

            // Кнопка удаления текущего профиля
            IconButton(
                onClick = {
                    currentProfileId?.let { clientState.removeConnectionProfile(it) }
                },
                enabled = !isConnected && currentProfileId != null && connectionProfiles.size > 1
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить профиль")
            }
        }

        // Вторая строка: host, port, encoding, connect
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                enabled = !isConnected,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                enabled = !isConnected,
                modifier = Modifier.width(100.dp)
            )

            // Dropdown для выбора кодировки
            Box(modifier = Modifier.width(150.dp)) {
                OutlinedTextField(
                    value = encoding,
                    onValueChange = { },
                    label = { Text("Кодировка") },
                    enabled = !isConnected,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = encodingMenuExpanded && !isConnected,
                    onDismissRequest = { encodingMenuExpanded = false }
                ) {
                    availableEncodings.forEach { enc ->
                        DropdownMenuItem(
                            text = { Text(enc) },
                            onClick = {
                                encoding = enc
                                clientState.setEncoding(enc)
                                encodingMenuExpanded = false
                            }
                        )
                    }
                }
                // Кликабельная область для открытия меню
                if (!isConnected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { encodingMenuExpanded = true }
                    )
                }
            }

            Button(
                onClick = {
                    if (isConnected) {
                        clientState.disconnect()
                    } else {
                        val portInt = port.toIntOrNull() ?: 4000
                        clientState.connect(host, portInt)
                    }
                },
                colors = if (isConnected) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                    contentDescription = if (isConnected) "Disconnect" else "Connect"
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isConnected) "Отключиться" else "Подключиться")
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
