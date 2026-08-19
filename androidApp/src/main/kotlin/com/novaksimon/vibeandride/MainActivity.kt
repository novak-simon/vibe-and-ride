package com.novaksimon.vibeandride

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novaksimon.vibeandride.music.MusicViewModel
import com.novaksimon.vibeandride.player.AndroidAudioPlayer
import com.novaksimon.vibeandride.ui.WorkoutScreen
import com.novaksimon.vibeandride.workout.WorkoutViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private lateinit var audioPlayer: AndroidAudioPlayer

    private lateinit var workoutViewModel: WorkoutViewModel
    private lateinit var musicViewModel: MusicViewModel
    private val pickWorkoutLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleWorkoutFile(it) }
    }
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkNotificationPermission()

        audioPlayer = AndroidAudioPlayer(applicationContext)

        lifecycleScope.launch {
            audioPlayer.initialize()
        }
        val workoutFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WorkoutViewModel() as T
            }

        }
        val musicFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MusicViewModel(workoutViewModel, audioPlayer) as T
            }
        }

        setContent {
            MaterialTheme {
                workoutViewModel = viewModel(factory = workoutFactory)
                musicViewModel = viewModel(factory = musicFactory)
                WorkoutScreen(
                    workoutViewModel = workoutViewModel,
                    musicViewModel = musicViewModel,
                    onUploadWorkout = {
                        pickWorkoutLauncher.launch("*/*")
                    }
                )
            }
        }
    }

    private fun handleWorkoutFile(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            }
            val fileName = getFileName(uri) ?: "Uploaded Workout"

            if (content != null) {
                workoutViewModel.uploadWorkoutFromFile(content, fileName)
            }
        } catch (e: Exception) {
            println("Error reading workout file: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}