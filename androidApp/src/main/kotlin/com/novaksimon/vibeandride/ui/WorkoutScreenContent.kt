package com.novaksimon.vibeandride.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novaksimon.vibeandride.model.Interval
import com.novaksimon.vibeandride.model.Song
import com.novaksimon.vibeandride.model.Workout
import com.novaksimon.vibeandride.recommendation.SongRecommendation
import com.novaksimon.vibeandride.util.formatSeconds
import com.novaksimon.vibeandride.workout.StructuredState
import com.novaksimon.vibeandride.workout.WorkoutMode
import com.novaksimon.vibeandride.workout.WorkoutSessionState
import com.novaksimon.vibeandride.workout.WorkoutStatus
import kotlin.time.Duration
import coil3.compose.AsyncImage
import com.novaksimon.vibeandride.R
import com.novaksimon.vibeandride.music.MusicUiState
import com.novaksimon.vibeandride.workout.WorkoutUiState
import kotlin.time.Duration.Companion.seconds

@Composable
fun WorkoutScreenContent(
    workoutUiState: WorkoutUiState,
    musicUiState: MusicUiState,
    availableWorkouts: List<Workout>,
    onLoadWorkouts: () -> Unit,
    onSelectWorkout: (Int) -> Unit,
    onStart: () -> Unit,
    onPauseResumeBoth: () -> Unit,
    onMusicPlay: () -> Unit,
    onMusicPause: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onAdjustTime: (Duration) -> Unit,
    onLikeSong: (Boolean) -> Unit,
    onUploadWorkout: () -> Unit,
    onPickerVisibilityChange: (Boolean) -> Unit,
    onStartFreeRide: () -> Unit,
    onToggleBle: () -> Unit,
) {
    var showResyncDialog by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadWorkouts()
    }

    val state = workoutUiState.workoutState
    val status = state?.status ?: WorkoutStatus.NOT_STARTED
    val workout = (state?.mode as? WorkoutMode.Structured)?.workout
    val structured = state?.structuredState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Workout Name Header
        Text(
            text = workout?.name?.uppercase() ?: "NO WORKOUT SELECTED",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 6. Live RPM/POWER readout row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ValueReadout(
                label = "POWER",
                value = state?.currentPower?.let { "${it.toInt()}W" } ?: "---"
            )
            ValueReadout(
                label = "CADENCE",
                value = state?.currentCadence?.let { "${it.toInt()} RPM" } ?: "---"
            )
            
            val bleColor = when (workoutUiState.bleStatus) {
                com.novaksimon.vibeandride.workout.CadenceSourceStatus.CONNECTED -> Color.Green
                com.novaksimon.vibeandride.workout.CadenceSourceStatus.SCANNING,
                com.novaksimon.vibeandride.workout.CadenceSourceStatus.CONNECTING -> Color.Yellow
                com.novaksimon.vibeandride.workout.CadenceSourceStatus.ERROR -> Color.Red
                else -> Color.Gray
            }
            IconButton(onClick = onToggleBle, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = "Bluetooth",
                    tint = bleColor
                )
            }
        }

        // 2. Three-box row: prev / current / next
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val prevInterval = structured?.let { workout?.intervals?.getOrNull(it.intervalIndex - 1) }
            val nextInterval = structured?.let { workout?.intervals?.getOrNull(it.intervalIndex + 1) }

            IntervalBox(
                interval = prevInterval,
                modifier = Modifier.weight(1f),
                isCurrent = false
            )
            IntervalBox(
                interval = structured?.currentInterval,
                elapsed = structured?.elapsedInInterval,
                modifier = Modifier.weight(1.5f),
                isCurrent = true
            )
            IntervalBox(
                interval = nextInterval,
                modifier = Modifier.weight(1f),
                isCurrent = false
            )
        }

        // 3. Line Chart
        if (workout != null) {
            WorkoutProfileChart(
                workout = workout,
                currentElapsed = state?.totalElapsed ?: 0.seconds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
        }

        // 4. Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onPickerVisibilityChange(true) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("SELECT")
            }

            Button(
                onClick = {
                    if (status == WorkoutStatus.READY) onStart() else onPauseResumeBoth()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                enabled = status != WorkoutStatus.FINISHED && status != WorkoutStatus.NOT_STARTED,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    when (status) {
                        WorkoutStatus.READY -> "START"
                        WorkoutStatus.RUNNING -> "PAUSE"
                        WorkoutStatus.PAUSED -> "RESUME"
                        else -> "START"
                    }
                )
            }

            IconButton(
                onClick = { showResyncDialog = true },
                enabled = status == WorkoutStatus.RUNNING || status == WorkoutStatus.PAUSED
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Resync", tint = MaterialTheme.colorScheme.primary)
            }
            
            IconButton(onClick = { showDebugDialog = true }) {
                Icon(Icons.Default.Refresh, contentDescription = "Debug", tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 5. Music mini-player
        MusicMiniPlayer(
            musicUiState = musicUiState,
            onMusicPlay = onMusicPlay,
            onMusicPause = onMusicPause,
            onSkip = onSkip,
            onLikeSong = onLikeSong
        )
    }

    if (workoutUiState.isPickerVisible) {
        WorkoutPickerDialog(
            workouts = availableWorkouts,
            onSelect = { workoutId ->
                onSelectWorkout(workoutId)
                onPickerVisibilityChange(false)
            },
            onUpload = onUploadWorkout,
            onStartFreeRide = {
                onStartFreeRide()
                onPickerVisibilityChange(false)
            },
            onDismiss = { onPickerVisibilityChange(false) },
        )
    }

    if (showResyncDialog) {
        ResyncDialog(
            currentTotalElapsed = workoutUiState.workoutState?.totalElapsed ?: Duration.ZERO,
            onAdjust = onAdjustTime,
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
fun ValueReadout(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IntervalBox(
    interval: Interval?,
    modifier: Modifier = Modifier,
    elapsed: Duration? = null,
    isCurrent: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        if (interval == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("---", color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${(interval.power * 100).toInt()}%",
                    style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${interval.cadence.toInt()} RPM",
                    style = if (isCurrent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isCurrent && elapsed != null) {
                    Text(
                        text = "${formatSeconds(elapsed.inWholeSeconds.toDouble())} / ${formatSeconds(interval.duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = formatSeconds(interval.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutProfileChart(
    workout: Workout,
    currentElapsed: Duration,
    modifier: Modifier = Modifier
) {
    val intervals = workout.intervals
    if (intervals.isEmpty()) return

    val totalDuration = workout.totalDuration.toFloat()
    val maxPower = intervals.maxOfOrNull { it.power } ?: 1f
    val accentColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        var currentX = 0f
        
        intervals.forEach { interval ->
            val intervalWidth = (interval.duration.toFloat() / totalDuration) * width
            val intervalHeight = (interval.power / maxPower) * height
            
            drawRect(
                color = mutedColor,
                topLeft = Offset(currentX, height - intervalHeight),
                size = Size(intervalWidth, intervalHeight)
            )
            currentX += intervalWidth
        }
        
        // Position Marker
        val markerX = (currentElapsed.inWholeSeconds.toFloat() / totalDuration) * width
        drawLine(
            color = accentColor,
            start = Offset(markerX, 0f),
            end = Offset(markerX, height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun MusicMiniPlayer(
    musicUiState: MusicUiState,
    onMusicPlay: () -> Unit,
    onMusicPause: () -> Unit,
    onSkip: () -> Unit,
    onLikeSong: (Boolean) -> Unit
) {
    val imageModel = if (LocalInspectionMode.current) {
        R.drawable.placeholder
    } else {
        musicUiState.currentSong?.image
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Song Artwork",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = musicUiState.currentSong?.title ?: "Not Playing",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = musicUiState.currentSong?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            IconButton(onClick = { onLikeSong(!musicUiState.isLiked) }, enabled = musicUiState.currentSong != null) {
                Icon(
                    imageVector = if (musicUiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (musicUiState.isLiked) Color.Red else Color.Gray
                )
            }

            IconButton(
                onClick = { if (musicUiState.isPlaying) onMusicPause() else onMusicPlay() },
                enabled = musicUiState.isMusicAvailable
            ) {
                Icon(
                    imageVector = if (musicUiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }

            IconButton(onClick = { onSkip() }, enabled = musicUiState.isMusicAvailable) {
                Icon(Icons.Default.SkipNext, contentDescription = "Skip")
            }
        }
    }
}

@Composable
fun WorkoutPickerDialog(
    workouts: List<Workout>,
    onSelect: (Int) -> Unit,
    onUpload: () -> Unit,
    onStartFreeRide: () -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredWorkouts = remember(workouts, searchQuery) {
        workouts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartFreeRide,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Free Ride")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                                    .clickable(enabled = true) {
                                        workout.id.let(onSelect)
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = workout.name,
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
                                    Text(
                                        text = "by ${workout.author}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
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
fun ResyncDialog(
    currentTotalElapsed: Duration,
    onAdjust: (Duration) -> Unit,
    onDismiss: () -> Unit
) {
    val currentSeconds = currentTotalElapsed.inWholeSeconds.toDouble()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resynchronize workout") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Prominent real-time ticking display
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatSeconds(currentSeconds),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Current Position",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ResyncAdjustButton("-10s") { onAdjust((-10).seconds) }
                        ResyncAdjustButton("-5s") { onAdjust((-5).seconds) }
                        ResyncAdjustButton("-1s") { onAdjust((-1).seconds) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ResyncAdjustButton("+1s") { onAdjust(1.seconds) }
                        ResyncAdjustButton("+5s") { onAdjust(5.seconds) }
                        ResyncAdjustButton("+10s") { onAdjust(10.seconds) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun ResyncAdjustButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
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
                            Text("BPM: ${rec.song?.bpm ?: "-"}")
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
    widthDp = 360,
    heightDp = 720
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
            onMusicPlay = {},
            onMusicPause = {},
            onPauseResumeBoth = {},
            onSkip = {},
            onReset = {},
            onAdjustTime = {},
            onLikeSong = {},
            onUploadWorkout = {},
            onPickerVisibilityChange = {},
            onStartFreeRide = {},
            onToggleBle = {},
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
            description = "No description",
            intervals = emptyList()
        )
    )
}
private fun previewWorkoutState(): WorkoutUiState {
    val workout = previewWorkouts().first()
    return WorkoutUiState(
        workoutState = WorkoutSessionState(
            mode = WorkoutMode.Structured(workout),
            status = WorkoutStatus.RUNNING,
            totalElapsed = 180.seconds,
            currentCadence = 90.0,
            currentPower = 200.0,
            structuredState = StructuredState(
                intervalIndex = 3,
                currentInterval = Interval(type = "SteadyState", power = 0.8f, cadence = 90.toDouble(), duration = 60.0),
                elapsedInInterval = 30.seconds,
                remainingInInterval = 30.seconds,
                totalRemaining = 240.seconds,
            )
        )
    )
}
private fun previewMusicState(): MusicUiState {
    return MusicUiState(
        currentSong = Song(
            id = 1,
            title = "Test song",
            artist = "test artist",
            releaseDate = "2024-01-01",
            genre = listOf("Electronic", "Synthwave"),
            duration = 210,
            bpm = 120,
            startTime = 0f,
            endTime = 210f,
            image = "https://usercontent.jamendo.com?type=album&id=4117&width=300&trackid=33324",
            shareUrl = "https://example.com/share",
            tags = listOf("energetic"),
        ),
        isMusicAvailable = true
    )
}
