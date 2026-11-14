package com.chewplay

import com.chewplay.vision.PauseReason

/** Состояние для интерфейса: обновляется примерно 10 раз в секунду. */
data class RuntimeStatus(
    val facePresent: Boolean = false,
    val faceReliable: Boolean = false,
    val chewing: Boolean = false,
    val eating: Boolean = false,
    val reason: PauseReason = PauseReason.NO_VIDEO,
    val peaks: Int = 0,
    val amplitude: Float = 0f,
    val threshold: Float = 0f,
    val recent: List<Float> = emptyList(),
    val calibrating: Boolean = false,
    val cameraAvailable: Boolean = true,
    val cameraError: String? = null,
)
