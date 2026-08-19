package com.novaksimon.vibeandride.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.util.formatSeconds
import com.novaksimon.vibeandride.workout.WorkoutState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import coil3.compose.AsyncImage
import com.novaksimon.vibeandride.R
import com.novaksimon.vibeandride.music.MusicUiState
import com.novaksimon.vibeandride.workout.WorkoutUiState

@Composable
fun WorkoutScreenContent(
    workoutUiState: WorkoutUiState,
    musicUiState: MusicUiState,
    availableWorkouts: List<Workout>,

    onLoadWorkouts: () -> Unit,
    onSelectWorkout: (Int) -> Unit,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onResync: (Duration) -> Unit,
    onUploadWorkout: () -> Unit,
    onPickerVisibilityChange: (Boolean) -> Unit,
) {
    var showResyncDialog by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadWorkouts()
    }

    val status = workoutUiState.workoutState?.status ?: WorkoutStatus.NOT_STARTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(onClick = { onPickerVisibilityChange(true) }) {
                Text("Select workout...", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = { showDebugDialog = true }) {
                Text("Debug", style = MaterialTheme.typography.labelLarge)
            }
        }

        val imageModel = if (LocalInspectionMode.current) {
            R.drawable.placeholder
        } else {
            musicUiState.currentSong?.image
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Artwork",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (workoutUiState.isLoading) "Loading workout..."
                    else workoutUiState.workoutState?.let { "Workout loaded" } ?: "No workout loaded",
                    style = MaterialTheme.typography.titleSmall
                )

                workoutUiState.workoutState?.let { state ->
                    Text(
                        text = if (state.intervalIndex == null) "Interval: finished"
                        else "Int ${state.intervalIndex!! + 1}: ${state.currentInterval?.type ?: ""} (${formatSeconds(state.currentInterval?.duration)})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = buildString {
                            state.currentInterval?.power?.let { append("Pwr: $it") }
                            state.currentInterval?.cadence?.let { append(" · Cad: $it") }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Song: ${musicUiState.currentSong?.title ?: "none"}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )

                Text(
                    text = if (!musicUiState.isMusicAvailable) "Music: disabled"
                    else if (status == WorkoutStatus.RUNNING) "Music: playing"
                    else "Music: paused",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall
                )

                workoutUiState.workoutState?.let { state ->
                    Text(
                        text = "Int: ${formatSeconds(state.elapsedInInterval.inWholeSeconds.toDouble())} / ${formatSeconds(state.intervalLength.inWholeSeconds.toDouble())}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Total: ${formatSeconds(state.totalElapsed.inWholeSeconds.toDouble())} / ${formatSeconds((state.totalElapsed + state.totalRemaining).inWholeSeconds.toDouble())}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Status: ${status.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    color = if (status == WorkoutStatus.RUNNING) Color.Green else Color.Red,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onStart() },
                enabled = status == WorkoutStatus.NOT_STARTED,
                modifier = Modifier.weight(1f),
            ) { Text("Start") }
            Button(
                onClick = { onPauseResume() },
                enabled = status == WorkoutStatus.RUNNING || status == WorkoutStatus.PAUSED,
                modifier = Modifier.weight(1f),
            ) { Text(if (status == WorkoutStatus.PAUSED) "Resume" else "Pause") }

            Button(
                onClick = { onSkip() },
                enabled = status == WorkoutStatus.RUNNING || status == WorkoutStatus.PAUSED,
                modifier = Modifier.weight(1f),
            ) { Text("Skip") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onReset() },
                enabled = status == WorkoutStatus.RUNNING || status == WorkoutStatus.PAUSED || status == WorkoutStatus.FINISHED,
                modifier = Modifier.weight(1f),
            ) { Text("Reset") }

            Button(
                onClick = { showResyncDialog = true },
                enabled = status == WorkoutStatus.RUNNING || status == WorkoutStatus.PAUSED,
                modifier = Modifier.weight(1f),
            ) { Text("Resync") }
        }
    }

    if (workoutUiState.isPickerVisible) {
        WorkoutPickerDialog(
            workouts = availableWorkouts,
            onSelect = { workoutId ->
                onSelectWorkout(workoutId)
                onPickerVisibilityChange(false)
            },
            onUpload = onUploadWorkout,
            onDismiss = { onPickerVisibilityChange(false) },
        )
    }

    if (showResyncDialog) {
        ResyncDialog(
            onConfirm = { seconds ->
                onResync((Duration.parse("${seconds}s")))
                showResyncDialog = false
            },
            onDismiss = { showResyncDialog = false },
        )
    }

    if (showDebugDialog) {
        DebugDialog(
            info = musicUiState.debugInfo,
            onDismiss = { showDebugDialog = false },
        )
    }
}


@Composable
fun WorkoutPickerDialog(
    workouts: List<Workout>,
    onSelect: (Int) -> Unit,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredWorkouts = remember(workouts, searchQuery) {
        workouts.filter { it.name?.contains(searchQuery, ignoreCase = true) == true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search workouts...") },
                    singleLine = true
                )

                OutlinedButton(
                    onClick = onUpload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload Workout (.zwo)")
                }

                if (filteredWorkouts.isEmpty()) {
                    Text("No workouts found.", modifier = Modifier.padding(top = 8.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                        items(filteredWorkouts) { workout ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = workout.id != null) {
                                        workout.id?.let(onSelect)
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = workout.name ?: "<unnamed>",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatSeconds(workout.totalDuration.toDouble()),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    workout.author?.let {
                                        Text(
                                            text = "by $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ResyncDialog(onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resynchronize workout") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Workout position (seconds)") },
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toDoubleOrNull()?.let(onConfirm) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DebugDialog(info: List<SongRecommendation>?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recommendation Debug") },
        text = {
            if (info.isNullOrEmpty()) {
                Text("No debug info available for current interval yet.")
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(info) { rec ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text("${rec.song?.title ?: "-"} by ${rec.song?.artist ?: "-"}", style = MaterialTheme.typography.titleSmall)
                            Text("BPM: ${rec.song?.bpm ?: "-"}, confidence: ${rec.song?.bpmConfidence ?: "-"}")
                            Text("Preference score: ${rec.preferenceRecommendation?.score ?: "-"}")
                            Row(modifier = Modifier.padding(start = 4.dp)) {
                                Text("G: ${rec.preferenceRecommendation?.numberOfMatchingGenres}," +
                                        "R: ${rec.preferenceRecommendation?.repetitionCoefficient}," +
                                        "L: ${rec.preferenceRecommendation?.likeCoefficient}," +
                                        "S: ${rec.preferenceRecommendation?.skipCoefficient}")
                            }
                            Text("Interval score: ${rec.intervalRecommendation?.score ?: "-"}")
                            Row(modifier = Modifier.padding(start = 4.dp)) {
                                Text("Cd: ${rec.intervalRecommendation?.cadenceScore}," +
                                        "Cf: ${rec.intervalRecommendation?.confScore}," +
                                        "S: ${rec.intervalRecommendation?.songScore}," +
                                        "Cm: ${rec.intervalRecommendation?.cadenceCompatibleBonus}")
                            }
                            Text("FINAL SCORE: ${rec.score}")
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Preview(
    showBackground = true,
    widthDp = 3000,
    heightDp = 3000
)
@Composable
private fun WorkoutScreenPreview() {
    MaterialTheme {
        WorkoutScreenContent(
            workoutUiState = previewWorkoutState(),
            musicUiState = previewMusicState(),
            availableWorkouts = previewWorkouts(),

            onLoadWorkouts = {},
            onSelectWorkout = {},
            onStart = {},
            onPauseResume = {},
            onSkip = {},
            onReset = {},
            onResync = {},
            onUploadWorkout = {},
            onPickerVisibilityChange = {},
        )
    }
}
private fun previewWorkouts(): List<Workout> {
    return listOf(
        Workout(
            id = 1,
            name = "FTP Intervals",
            author = "Zwift",
            sportType = true,
            tags = listOf("FTP", "Intervals"),
            description = "Example workout",
            intervals = emptyList()
        ),
        Workout(
            id = 2,
            name = "Recovery Ride",
            author = "Example",
            sportType = true,
            tags = listOf("Recovery"),
            description = null,
            intervals = emptyList()
        )
    )
}
private fun previewWorkoutState(): WorkoutUiState {
    return WorkoutUiState(
        workoutState = WorkoutState(
            workout = previewWorkouts().first(),
            status = WorkoutStatus.RUNNING,
            intervalIndex = 3,
            currentInterval = Interval(type = "SteadyState", power = 0.8f, cadence = 90.toDouble()),
            elapsedInInterval = Duration.ZERO,
            remainingInInterval = Interval().duration.toDuration(DurationUnit.SECONDS),
            intervalLength = Interval().duration.toDuration(DurationUnit.SECONDS),
            totalElapsed = 180.toDuration(DurationUnit.SECONDS),
            totalRemaining = 240.toDuration(DurationUnit.SECONDS),
        )
    )
}
private fun previewMusicState(): MusicUiState {
    return MusicUiState(
        currentSong = Song(
            title = "Test song",
            artist = "test artist",
            image = "https://usercontent.jamendo.com?type=album&id=4117&width=300&trackid=33324"
        ),
        isMusicAvailable = true
    )
}