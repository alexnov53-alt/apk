package com.chewplay.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("chewplay_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    /** Атомарно меняет настройки: transform получает текущее состояние. */
    fun update(transform: (AppSettings) -> AppSettings) {
        val current = _state.value
        val next = transform(current)
        if (next == current) return
        write(next)
        _state.value = next
    }

    fun reset() {
        update { AppSettings() }
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            videoUri = prefs.getString(KEY_VIDEO, defaults.videoUri),
            sensitivity = prefs.getFloat(KEY_SENSITIVITY, defaults.sensitivity),
            pauseDelayMs = prefs.getInt(KEY_PAUSE_DELAY, defaults.pauseDelayMs),
            resumeDelayMs = prefs.getInt(KEY_RESUME_DELAY, defaults.resumeDelayMs),
            faceLostDelayMs = prefs.getInt(KEY_FACE_LOST, defaults.faceLostDelayMs),
            useFrontCamera = prefs.getBoolean(KEY_FRONT_CAMERA, defaults.useFrontCamera),
            showPreview = prefs.getBoolean(KEY_PREVIEW, defaults.showPreview),
            showStatus = prefs.getBoolean(KEY_STATUS, defaults.showStatus),
            showDebug = prefs.getBoolean(KEY_DEBUG, defaults.showDebug),
            calibrationNoise = prefs.getFloat(KEY_CALIBRATION, defaults.calibrationNoise),
            enforcementEnabled = prefs.getBoolean(KEY_ENFORCEMENT, defaults.enforcementEnabled),
        )
    }

    private fun write(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_VIDEO, settings.videoUri)
            .putFloat(KEY_SENSITIVITY, settings.sensitivity)
            .putInt(KEY_PAUSE_DELAY, settings.pauseDelayMs)
            .putInt(KEY_RESUME_DELAY, settings.resumeDelayMs)
            .putInt(KEY_FACE_LOST, settings.faceLostDelayMs)
            .putBoolean(KEY_FRONT_CAMERA, settings.useFrontCamera)
            .putBoolean(KEY_PREVIEW, settings.showPreview)
            .putBoolean(KEY_STATUS, settings.showStatus)
            .putBoolean(KEY_DEBUG, settings.showDebug)
            .putFloat(KEY_CALIBRATION, settings.calibrationNoise)
            .putBoolean(KEY_ENFORCEMENT, settings.enforcementEnabled)
            .apply()
    }

    private companion object {
        const val KEY_VIDEO = "video_uri"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_PAUSE_DELAY = "pause_delay_ms"
        const val KEY_RESUME_DELAY = "resume_delay_ms"
        const val KEY_FACE_LOST = "face_lost_ms"
        const val KEY_FRONT_CAMERA = "front_camera"
        const val KEY_PREVIEW = "show_preview"
        const val KEY_STATUS = "show_status"
        const val KEY_DEBUG = "show_debug"
        const val KEY_CALIBRATION = "calibration_noise"
        const val KEY_ENFORCEMENT = "enforcement"
    }
}
