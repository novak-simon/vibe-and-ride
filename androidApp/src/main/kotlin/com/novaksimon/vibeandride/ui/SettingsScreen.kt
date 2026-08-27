package com.novaksimon.vibeandride.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val settingsItems = listOf(
        "Profile",
        "Workout",
        "Free Ride",
        "Bluetooth",
        "About"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(settingsItems) { item ->
            SettingsRow(item)
            HorizontalDivider()
        }
    }
}

@Composable
fun SettingsRow(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Stub */ }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
