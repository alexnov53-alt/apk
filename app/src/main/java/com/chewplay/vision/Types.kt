package com.chewplay.vision

import com.chewplay.data.AppSettings
import kotlin.math.max

/** Почему видео сейчас на паузе (для плашки статуса). */
enum class PauseReason {
    NO_VIDEO,
    NO_CAMERA,
    DISABLED,
    NO_FACE,
    NOT_CHEWING,
}

/**
 * Параметры детектора жевания.
 *
 * [prominence] — минимальная амплитуда «зубца» жевания, выраженная в долях ширины лица.
 * Так как все расстояния делятся на ширину лица, порог не зависит от расстояния до камеры.
 */
data class ChewingParams(
    val prominence: Float = 0.014f,
    val minPeaks: Int = 2,
    val windowMs: Long = 2600L,
)

/**
 * Переводит пользовательскую чувствительность в порог детектора.
 * Чем выше чувствительность, тем ниже порог (легче поймать жевание).
 * Если была калибровка, порог не опускается ниже двух шумов покоя.
 */
fun AppSettings.toChewingParams(): ChewingParams {
    val normalized = sensitivity.coerceIn(0f, 1f)
    val base = 0.006f + (1f - normalized) * 0.026f
    // Калибровка может ошибочно «услышать» само жевание, если человек жевал
    // во время измерения. Ограничиваем её вклад, чтобы детектор не умер.
    val calibrated = (calibrationNoise * 2.2f).coerceAtMost(0.04f)
    return ChewingParams(prominence = max(base, calibrated).coerceIn(0.004f, 0.05f))
}

/**
 * Одно наблюдение лица на кадре.
 *
 * [openRatio]  — раствор рта: расстояние между внутренними краями губ / ширина лица.
 * [widthRatio] — ширина рта / ширина лица (ловит жевание с сомкнутыми губами).
 */
data class FaceObservation(
    val present: Boolean,
    val reliable: Boolean,
    val openRatio: Float,
    val widthRatio: Float,
    val eulerX: Float,
    val eulerY: Float,
) {
    companion object {
        val NONE = FaceObservation(
            present = false,
            reliable = false,
            openRatio = 0f,
            widthRatio = 0f,
            eulerX = 0f,
            eulerY = 0f,
        )
    }
}

/** Результат обработки одного кадра детектором жевания. */
data class ChewingSnapshot(
    val timestampMs: Long = 0L,
    val facePresent: Boolean = false,
    val faceReliable: Boolean = false,
    val chewing: Boolean = false,
    val chewingActive: Boolean = false,
    val peaks: Int = 0,
    val amplitude: Float = 0f,
    val openRatio: Float = 0f,
    val threshold: Float = 0f,
    val recent: List<Float> = emptyList(),
)
