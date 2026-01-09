package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bylins.client.ClientState

@Composable
fun StatusPanel(
    clientState: ClientState,
    modifier: Modifier = Modifier
) {
    val msdpData by clientState.msdpData.collectAsState()

    // Извлекаем данные из MSDP или используем значения по умолчанию
    val health = (msdpData["HEALTH"] as? String)?.toIntOrNull() ?: 100
    val maxHealth = (msdpData["MAX_HEALTH"] as? String)?.toIntOrNull() ?: 100
    val mana = (msdpData["MANA"] as? String)?.toIntOrNull() ?: 100
    val maxMana = (msdpData["MAX_MANA"] as? String)?.toIntOrNull() ?: 100
    val movement = (msdpData["MOVEMENT"] as? String)?.toIntOrNull() ?: 100
    val maxMovement = (msdpData["MAX_MOVEMENT"] as? String)?.toIntOrNull() ?: 100

    val level = (msdpData["LEVEL"] as? String) ?: "1"
    val experience = (msdpData["EXPERIENCE"] as? String) ?: "0"
    val gold = (msdpData["GOLD"] as? String) ?: "0"
    val opponent = (msdpData["OPPONENT_HEALTH"] as? String)

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Статус персонажа",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Divider()

        // Здоровье
        StatusBar(
            label = "HP",
            current = health,
            max = maxHealth,
            color = Color(0xFF4CAF50)
        )

        // Мана
        StatusBar(
            label = "Mana",
            current = mana,
            max = maxMana,
            color = Color(0xFF2196F3)
        )

        // Движение
        StatusBar(
            label = "Move",
            current = movement,
            max = maxMovement,
            color = Color(0xFFFFC107)
        )

        // Противник (если есть)
        opponent?.let { oppHealth ->
            Divider()
            Text(
                text = "Противник",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            StatusBar(
                label = "HP",
                current = oppHealth.toIntOrNull() ?: 0,
                max = 100, // Обычно сервер не присылает max для противника
                color = Color(0xFFFF5252)
            )
        }

        Divider()

        // Дополнительная информация
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow("Уровень", level)
            InfoRow("Опыт", experience)
            InfoRow("Золото", gold)
        }
    }
}

@Composable
private fun StatusBar(
    label: String,
    current: Int,
    max: Int,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "$current / $max",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LinearProgressIndicator(
            progress = current.toFloat() / max.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            color = color
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
