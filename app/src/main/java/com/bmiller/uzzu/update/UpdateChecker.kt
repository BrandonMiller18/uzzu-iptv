package com.bmiller.uzzu.update

import kotlinx.coroutines.flow.StateFlow

/** In-app update state shown on S1 (the "Update available" bar) and S7 ("Check for updates"). */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAtMillis: Long) : UpdateState

    /**
     * A newer release exists. [progress]: download in progress (mono line in the bar, buttons hidden).
     * [note]/[error]: what the last install attempt needs or why it failed. [dismissed]: "Later" was
     * pressed, so S1 hides the bar for this process (S7 still reports it).
     */
    data class Available(
        val version: String,
        val currentVersion: String,
        val progress: String? = null,
        val note: String? = null,
        val error: String? = null,
        val dismissed: Boolean = false,
    ) : UpdateState

    data class Failed(val message: String) : UpdateState
}

/** GitHub-release update check + install (PLAN "In-app updates"); see [GitHubUpdateChecker]. */
interface UpdateChecker {
    val state: StateFlow<UpdateState>

    /** Checks now (Settings) or at most once per 24 h when [force] is false (launch). */
    suspend fun check(force: Boolean)

    /** Downloads the APK and hands it to the system installer. */
    fun install()

    /** "Later" on the S1 bar: hide it for this process. */
    fun dismiss()
}
