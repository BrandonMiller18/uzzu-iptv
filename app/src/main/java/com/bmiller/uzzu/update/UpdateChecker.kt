package com.bmiller.uzzu.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app update state shown on S1 (the "Update available" bar) and S7 ("Check for updates"). */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAtMillis: Long) : UpdateState
    data class Available(val version: String, val currentVersion: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * GitHub-release update check + install (PLAN "In-app updates"). M5 provides the real
 * implementation; until then [PlaceholderUpdateChecker] keeps the UI slots wired.
 */
interface UpdateChecker {
    val state: StateFlow<UpdateState>

    /** Checks now (Settings) or at most once per 24 h when [force] is false (launch). */
    suspend fun check(force: Boolean)

    /** Downloads the APK and hands it to the system installer. */
    fun install()

    /** "Later" on the S1 bar: hide it for this process. */
    fun dismiss()
}

/** M3 stand-in until M5: never finds an update and says so when asked from Settings. */
class PlaceholderUpdateChecker : UpdateChecker {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    override suspend fun check(force: Boolean) {
        if (force) _state.value = UpdateState.Failed("Update check not available yet")
    }

    override fun install() = Unit

    override fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
