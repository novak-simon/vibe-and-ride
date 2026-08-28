package com.novaksimon.vibeandride

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.novaksimon.vibeandride.database.initializeProfile
import com.novaksimon.vibeandride.home.HomeViewModel
import com.novaksimon.vibeandride.music.MusicViewModel
import com.novaksimon.vibeandride.player.AndroidPlaybackController
import com.novaksimon.vibeandride.player.PlaybackService
import com.novaksimon.vibeandride.ui.HomeScreen
import com.novaksimon.vibeandride.ui.SettingsScreen
import com.novaksimon.vibeandride.ui.WorkoutScreen
import com.novaksimon.vibeandride.util.VibeLog
import com.novaksimon.vibeandride.workout.AndroidBluetoothCadenceSource
import com.novaksimon.vibeandride.workout.WorkoutViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

// App Color Palette
private val COLOR_BACKGROUND = Color(0xFF000000)
private val COLOR_PRIMARY = Color(0xFF146EB8)    // Blue
private val COLOR_SECONDARY = Color(0xFFCC8B2A)  // Yellow
private val COLOR_TERTIARY = Color(0xFF990FB5)   // Purple

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var mediaController: MediaController? = null

    private lateinit var workoutViewModel: WorkoutViewModel
    private lateinit var musicViewModel: MusicViewModel
    private lateinit var homeViewModel: HomeViewModel
    private val cadenceSource by lazy { AndroidBluetoothCadenceSource(applicationContext) }

    private val pickWorkoutLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleWorkoutFile(it) }
    }

    private val requestBlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            workoutViewModel.toggleBle()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.IO).launch {
            initializeProfile()
        }

        checkNotificationPermission()

        connectToPlaybackService()
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(
            this,
            ComponentName(
                this,
                PlaybackService::class.java
            )
        )

        val controllerFuture = MediaController.Builder(
            this,
            sessionToken
        ).buildAsync()

        controllerFuture.addListener(
            {
                try {
                    mediaController = controllerFuture.get()
                    initializeUi()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun initializeUi() {
        val controller = mediaController
            ?: error("MediaController failed to initialize")

        val workoutFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                return WorkoutViewModel(cadenceSource) as T
            }
        }

        val musicFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                return MusicViewModel(
                    AndroidPlaybackController(controller)
                ) as T
            }
        }

        val homeFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                return HomeViewModel() as T
            }
        }

        workoutViewModel = ViewModelProvider(this, workoutFactory)[WorkoutViewModel::class.java]
        musicViewModel = ViewModelProvider(this, musicFactory)[MusicViewModel::class.java]
        homeViewModel = ViewModelProvider(this, homeFactory)[HomeViewModel::class.java]

        workoutViewModel.initMusicViewModel(musicViewModel)
        musicViewModel.initWorkoutViewModel(workoutViewModel)

        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = COLOR_PRIMARY,
                    secondary = COLOR_SECONDARY,
                    tertiary = COLOR_TERTIARY,
                    background = COLOR_BACKGROUND,
                    surface = COLOR_BACKGROUND,
                    surfaceVariant = COLOR_BACKGROUND,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onTertiary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val screens = listOf(Screen.Home, Screen.Ride, Screen.Settings)
                            screens.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    label = { Text(screen.label) },
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            Screen.Home -> HomeScreen(homeViewModel)
                            Screen.Ride -> WorkoutScreen(
                                workoutViewModel = workoutViewModel,
                                musicViewModel = musicViewModel,
                                onUploadWorkout = {
                                    pickWorkoutLauncher.launch("*/*")
                                }
                            )
                            Screen.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    private sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
        object Home : Screen("home", "Home", Icons.Default.Home)
        object Ride : Screen("ride", "Ride", Icons.AutoMirrored.Filled.DirectionsBike)
        object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    }

    private fun handleWorkoutFile(uri: Uri) {
        try {
            val content = contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->
                    BufferedReader(
                        InputStreamReader(inputStream)
                    ).readText()
                }

            val fileName =
                getFileName(uri) ?: "Uploaded Workout"

            if (content != null) {
                workoutViewModel.uploadWorkoutFromFile(
                    content,
                    fileName
                )
            }

        } catch (e: Exception) {
            VibeLog.e(TAG, "Error reading workout file", e)
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null

        if (uri.scheme == "content") {
            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    val nameIndex =
                        cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        )

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
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun requestBlePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        // Location is almost always required for BLE scanning to work on many devices
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        requestBlePermissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onDestroy() {
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }
}
