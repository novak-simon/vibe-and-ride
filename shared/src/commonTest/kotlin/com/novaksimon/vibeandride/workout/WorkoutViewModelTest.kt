package com.novaksimon.vibeandride.workout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeCadenceSource = FakeCadenceSource()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLiveCadenceUpdatesUiState() = runTest {
        val viewModel = WorkoutViewModel(fakeCadenceSource)
        
        fakeCadenceSource.emitCadence(88.0)
        
        val state = viewModel.uiState.value.workoutState
        assertEquals(88.0, state?.currentCadence)
    }

    @Test
    fun testBleStatusUpdatesUiState() = runTest {
        val viewModel = WorkoutViewModel(fakeCadenceSource)
        
        fakeCadenceSource.emitStatus(CadenceSourceStatus.CONNECTED)
        
        assertEquals(CadenceSourceStatus.CONNECTED, viewModel.uiState.value.bleStatus)
    }

    @Test
    fun testToggleBle() = runTest {
        val viewModel = WorkoutViewModel(fakeCadenceSource)
        
        // Initial state is IDLE
        assertEquals(CadenceSourceStatus.IDLE, viewModel.uiState.value.bleStatus)
        
        viewModel.toggleBle()
        assertEquals(CadenceSourceStatus.SCANNING, viewModel.uiState.value.bleStatus)
        
        fakeCadenceSource.emitStatus(CadenceSourceStatus.CONNECTED)
        assertEquals(CadenceSourceStatus.CONNECTED, viewModel.uiState.value.bleStatus)
        
        viewModel.toggleBle()
        assertEquals(CadenceSourceStatus.IDLE, viewModel.uiState.value.bleStatus)
    }
}
