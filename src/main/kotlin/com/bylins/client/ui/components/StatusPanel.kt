package com.bylins.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusPanel(modifier: Modifier = Modifier) {
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
            current = 100,
            max = 100,
            color = Color(0xFF4CAF50)
        )

        // Мана
        StatusBar(
            label = "Mana",
            current = 80,
            max = 100,
            color = Color(0xFF2196F3)
        )

        // Движение
        StatusBar(
            label = "Move",
            current = 120,
            max = 150,
            color = Color(0xFFFFC107)
        )

        Divider()

        // Дополнительная информация
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow("Уровень", "1")
            InfoRow("Опыт", "0")
            InfoRow("Золото", "0")
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
