package com.chewplay

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chewplay.camera.CameraController
import com.chewplay.data.AppSettings
import com.chewplay.data.SettingsRepository
import com.chewplay.player.VideoController
import com.chewplay.vision.ChewingSignal
import com.chewplay.vision.EatingStateMachine
import com.chewplay.vision.FaceAnalyzer
import com.chewplay.vision.FaceObservation
import com.chewplay.vision.PauseReason
import com.chewplay.vision.toChewingParams
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Связывает камеру, распознавание лица, автомат состояний и плеер.
 *
 * Поток данных: CameraX → FaceAnalyzer (ML Kit) → ChewingSignal → EatingStateMachine → ExoPlayer.
 * Колбэки ML Kit приходят в главный поток, поэтому пауза/возобновление плеера
 * безопасно вызывается без переключения потоков.
 */
class AppController(private val activity: ComponentActivity) {

    private val repository = SettingsRepository(activity)
    val settings: StateFlow<AppSettings> = repository.state

    private val _status = MutableStateFlow(RuntimeStatus())
    val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

    val video = VideoController(activity)

    private val signal = ChewingSignal()
    private val machine = EatingStateMachine()

    private val analyzer = FaceAnalyzer(
        onObservation = { observation, timestampMs -> handleObservation(observation, timestampMs) },
        onError = { message -> _status.update { it.copy(cameraError = message) } },
    )

    private val camera = CameraController(
        context = activity,
        lifecycleOwner = activity,
        onFrame = { bitmap, rotationDegrees ->
            // Пока видео не выбрано, распознавание не нужно — не жжём батарею зря.
            val accepted = settings.value.videoUri != null &&
                analyzer.analyze(bitmap, rotationDegrees)
            if (!accepted) {
                bitmap.recycle()
            }
        },
        onError = { message -> _status.update { it.copy(cameraError = message) } },
    )

    private val desiredPlay = MutableStateFlow(false)

    private var lastUiAt = 0L
    private var lastEating = false
    private var cameraRunning = false
    private var cameraGranted = false
    private var appliedFrontCamera = true
    private var calibrationJob: Job? = null

    init {
        val initial = settings.value
        signal.params = initial.toChewingParams()
        appliedFrontCamera = initial.useFrontCamera
        video.setVideo(initial.videoUri)

        activity.lifecycleScope.launch {
            desiredPlay.collect { play -> video.setPlaying(play) }
        }

        activity.lifecycleScope.launch {
            settings.collect { updated ->
                signal.params = updated.toChewingParams()
                video.setVideo(updated.videoUri)
                if (updated.useFrontCamera != appliedFrontCamera) {
                    appliedFrontCamera = updated.useFrontCamera
                    if (cameraRunning) {
                        camera.start(updated.useFrontCamera)
                    }
                }
            }
        }
    }

    fun onStart() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraGranted = hasCameraPermission()
        if (cameraGranted) {
            startCamera()
        } else {
            // Без камеры контроль невозможен — пусть видео просто играет.
            desiredPlay.value = settings.value.videoUri != null
        }
    }

    fun onStop() {
        cameraRunning = false
        camera.stop()
        desiredPlay.value = false
        machine.reset()
        signal.reset()
        _status.update {
            it.copy(
                facePresent = false,
                chewing = false,
                eating = false,
                reason = PauseReason.NO_FACE,
            )
        }
    }

    fun onDestroy() {
        calibrationJob?.cancel()
        camera.close()
        analyzer.close()
        video.release()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        cameraGranted = granted
        if (granted) {
            startCamera()
            _status.update { it.copy(cameraAvailable = true, cameraError = null) }
        } else {
            desiredPlay.value = settings.value.videoUri != null
            _status.update { it.copy(cameraAvailable = false) }
        }
    }

    fun setVideo(uri: Uri) {
        repository.update { it.copy(videoUri = uri.toString()) }
        if (!cameraGranted) {
            // Камеры нет — значит и контроля нет: видео не должно стоять на паузе.
            desiredPlay.value = true
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        repository.update(transform)
    }

    fun resetSettings() {
        repository.reset()
    }

    fun attachPreview(view: PreviewView?) {
        camera.attachPreview(view)
    }

    fun startCalibration() {
        if (_status.value.calibrating) return
        signal.startCalibration(SystemClock.elapsedRealtime())
        _status.update { it.copy(calibrating = true) }
        calibrationJob?.cancel()
        calibrationJob = activity.lifecycleScope.launch {
            delay(CALIBRATION_DURATION_MS)
            val noise = signal.finishCalibration()
            if (noise != null) {
                repository.update { it.copy(calibrationNoise = noise) }
            }
            _status.update { it.copy(calibrating = false) }
        }
    }

    private fun startCamera() {
        if (cameraRunning) return
        cameraRunning = true
        camera.start(settings.value.useFrontCamera)
    }

    private fun handleObservation(observation: FaceObservation, timestampMs: Long) {
        val current = settings.value
        val snapshot = signal.onObservation(observation, timestampMs)
        val now = SystemClock.elapsedRealtime()

        val update = machine.update(
            now = now,
            facePresent = snapshot.facePresent,
            chewingActive = snapshot.chewingActive,
            settings = current,
        )

        val shouldPlay = when {
            current.videoUri == null -> false
            !cameraGranted -> true
            !current.enforcementEnabled -> true
            else -> update.eating
        }
        if (desiredPlay.value != shouldPlay) {
            desiredPlay.value = shouldPlay
        }

        val reason = when {
            !cameraGranted -> PauseReason.NO_CAMERA
            current.videoUri == null -> PauseReason.NO_VIDEO
            !current.enforcementEnabled -> PauseReason.DISABLED
            else -> update.reason
        }

        val eatingChanged = update.eating != lastEating
        lastEating = update.eating

        if (eatingChanged || now - lastUiAt >= UI_UPDATE_INTERVAL_MS) {
            lastUiAt = now
            _status.update { previous ->
                RuntimeStatus(
                    facePresent = snapshot.facePresent,
                    faceReliable = snapshot.faceReliable,
                    chewing = snapshot.chewingActive,
                    eating = update.eating,
                    reason = reason ?: PauseReason.NOT_CHEWING,
                    peaks = snapshot.peaks,
                    amplitude = snapshot.amplitude,
                    threshold = snapshot.threshold,
                    recent = snapshot.recent,
                    calibrating = previous.calibrating,
                    cameraAvailable = cameraGranted,
                    cameraError = null,
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val UI_UPDATE_INTERVAL_MS = 90L
        const val CALIBRATION_DURATION_MS = 3200L
    }
}
