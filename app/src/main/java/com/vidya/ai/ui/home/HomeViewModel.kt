package com.vidya.ai.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidya.ai.core.NetworkMonitor
import com.vidya.ai.ui.components.OrbState
import kotlinx.coroutines.flow.*

data class HomeUiState(
    val orb: OrbState = OrbState.Idle,
    val online: Boolean = true
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val orb = MutableStateFlow(OrbState.Idle)

    val state: StateFlow<HomeUiState> =
        combine(orb, NetworkMonitor(app).isOnline) { o, n -> HomeUiState(o, n) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Preview only: cycles orb states so the animations can be seen.
     *  Replaced by real speech state in Phase 4/5. */
    fun onMicTap() {
        orb.value = when (orb.value) {
            OrbState.Idle -> OrbState.Listening
            OrbState.Listening -> OrbState.Thinking
            OrbState.Thinking -> OrbState.Speaking
            OrbState.Speaking -> OrbState.Idle
        }
    }
}
