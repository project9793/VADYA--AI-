package com.vidya.ai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vidya.ai.core.NetworkMonitor
import com.vidya.ai.ui.components.OrbState
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Subject(
    val id: String,
    val name: String,
    val chapter: String,
    val progress: Float,
    val lessonsLeft: Int,
    val accentArgb: Long
)

data class HomeUiState(
    val learnerName: String = "Siya",
    val greeting: String = "Hello",
    val streakDays: Int = 6,
    val minutesToday: Int = 25,
    val orbState: OrbState = OrbState.Idle,
    val subjects: List<Subject> = emptyList(),
    val tip: String = ""
)

class HomeViewModel(private val networkMonitor: NetworkMonitor) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private var orbResetJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { current ->
            current.copy(
                greeting = greetingForNow(),
                subjects = demoSubjects(),
                tip = tipForToday()
            )
        }
    }

    /** Tap cycles Idle -> Listening -> Thinking -> Speaking -> Idle, then settles back to Idle. */
    fun onOrbTapped() {
        val next = when (_uiState.value.orbState) {
            OrbState.Idle -> OrbState.Listening
            OrbState.Listening -> OrbState.Thinking
            OrbState.Thinking -> OrbState.Speaking
            OrbState.Speaking -> OrbState.Idle
        }
        _uiState.update { it.copy(orbState = next) }

        orbResetJob?.cancel()
        if (next != OrbState.Idle) {
            orbResetJob = viewModelScope.launch {
                delay(3200L)
                _uiState.update { state ->
                    if (state.orbState == next) state.copy(orbState = OrbState.Idle) else state
                }
            }
        }
    }

    private fun greetingForNow(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night study"
        }
    }

    private fun tipForToday(): String {
        val tips = listOf(
            "Revise one formula list before bed. Recall is strongest right after sleep.",
            "Teach the last topic to an empty chair. If you stumble, that is your weak spot.",
            "Do ten minutes of questions before opening the notes. Retrieval first, revision second.",
            "Write the answer in three lines, then expand. Structure beats length in board exams."
        )
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return tips[day % tips.size]
    }

    private fun demoSubjects(): List<Subject> = listOf(
        Subject("maths", "Mathematics", "Quadratic equations", 0.72f, 4, 0xFF7C5CFF),
        Subject("science", "Science", "Light - reflection", 0.45f, 7, 0xFF35E0D0),
        Subject("english", "English", "Letter writing", 0.88f, 2, 0xFFFFB020),
        Subject("sst", "Social Science", "The Constitution", 0.30f, 9, 0xFFFF6B9A)
    )

    companion object {
        fun factory(networkMonitor: NetworkMonitor) = viewModelFactory {
            initializer { HomeViewModel(networkMonitor) }
        }
    }
}
