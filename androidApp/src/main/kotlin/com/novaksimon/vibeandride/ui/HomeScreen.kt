package com.novaksimon.vibeandride.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.novaksimon.vibeandride.home.HomeViewModel
import com.novaksimon.vibeandride.home.WorkoutPlaceholder
import com.novaksimon.vibeandride.model.Song

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val likedSongs by viewModel.likedSongs.collectAsState()
    val isLoadingLikedSongs by viewModel.isLoadingLikedSongs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Section(title = "Recent workouts") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(viewModel.recentWorkouts) { workout ->
                    WorkoutCard(workout)
                }
            }
        }

        Section(title = "Suggested workouts") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(viewModel.suggestedWorkouts) { workout ->
                    WorkoutCard(workout)
                }
            }
        }

        Section(title = "Liked songs") {
            if (isLoadingLikedSongs) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (likedSongs.isEmpty()) {
                Text(
                    "No liked songs yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(likedSongs) { song ->
                        SongTile(song)
                    }
                }
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
fun WorkoutCard(workout: WorkoutPlaceholder) {
    Card(
        modifier = Modifier.width(160.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(workout.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(workout.duration, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text(workout.rating, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SongTile(song: Song) {
    Column(modifier = Modifier.width(120.dp)) {
        AsyncImage(
            model = song.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(MaterialTheme.shapes.medium)
        )
        Text(
            song.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            maxLines = 1
        )
    }
}
