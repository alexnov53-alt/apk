package com.chewplay.vision

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

private const val KEEP_MS = 5000L
private const val SMOOTHING_TAU_MS = 120f

/** Сколько миллисекунд после последнего «зубца» считаем, что человек всё ещё жуёт. */
private const val CHEW_ACTIVE_WINDOW_MS = 1600L

private data class ChannelStats(
    val peaks: Int,
    val lastPeakAt: Long,
    val amplitude: Float,
)

/**
 * Превращает поток наблюдений лица в признак «жуёт / не жуёт».
 *
 * Идея: раствор рта при жевании колеблется с частотой примерно 0,8–2 Гц.
 * Мы сглаживаем сигнал, убираем медленный дрейф (среднее по окну) и считаем
 * локальные максимумы, заметно превышающие средний уровень. Два и более
 * таких «зубца» за 2,6 с — это жевание.
 */
class ChewingSignal {

    private class Channel {
        private val times = ArrayDeque<Long>()
        private val values = ArrayDeque<Float>()
        private var smoothed = Float.NaN
        private var lastPushAt = -1L

        fun push(timestampMs: Long, raw: Float) {
            // Коэффициент сглаживания зависит от реального FPS (на слабых
            // телефонах ML Kit отдаёт 5–10 кадров/с, и фиксированная альфа
            // слишком сильно резала бы жевание).
            val dt = if (lastPushAt < 0L) {
                100f
            } else {
                (timestampMs - lastPushAt).toFloat().coerceIn(10f, 1000f)
            }
            lastPushAt = timestampMs
            val alpha = (1f - exp(-dt / SMOOTHING_TAU_MS)).coerceIn(0.15f, 0.85f)

            smoothed = if (smoothed.isNaN()) raw else smoothed + alpha * (raw - smoothed)
            times.addLast(timestampMs)
            values.addLast(smoothed)
            while (times.isNotEmpty() && timestampMs - times.first() > KEEP_MS) {
                times.removeFirst()
                values.removeFirst()
            }
        }

        fun reset() {
            times.clear()
            values.clear()
            smoothed = Float.NaN
            lastPushAt = -1L
        }

        fun valuesSince(from: Long): FloatArray {
            val out = ArrayList<Float>(values.size)
            val timeIterator = times.iterator()
            val valueIterator = values.iterator()
            while (valueIterator.hasNext()) {
                val time = timeIterator.next()
                val value = valueIterator.next()
                if (time >= from) out.add(value)
            }
            return out.toFloatArray()
        }

        fun analyse(from: Long, prominence: Float): ChannelStats {
            val filteredTimes = ArrayList<Long>(times.size)
            val filteredValues = ArrayList<Float>(values.size)
            val timeIterator = times.iterator()
            val valueIterator = values.iterator()
            while (valueIterator.hasNext()) {
                val time = timeIterator.next()
                val value = valueIterator.next()
                if (time >= from) {
                    filteredTimes.add(time)
                    filteredValues.add(value)
                }
            }
            if (filteredValues.size < 5) return ChannelStats(0, -1L, 0f)

            var sum = 0f
            for (value in filteredValues) sum += value
            val mean = sum / filteredValues.size

            var minimum = Float.MAX_VALUE
            var maximum = -Float.MAX_VALUE
            var peaks = 0
            var lastPeakAt = -1L

            for (index in filteredValues.indices) {
                val value = filteredValues[index]
                if (value < minimum) minimum = value
                if (value > maximum) maximum = value
                if (index in 1 until filteredValues.size - 1) {
                    val isPeak = value > filteredValues[index - 1] &&
                        value >= filteredValues[index + 1] &&
                        value - mean > prominence
                    if (isPeak) {
                        peaks++
                        lastPeakAt = filteredTimes[index]
                    }
                }
            }
            return ChannelStats(peaks, lastPeakAt, maximum - minimum)
        }
    }

    @Volatile
    var params: ChewingParams = ChewingParams()

    private val gapChannel = Channel()
    private val widthChannel = Channel()

    private var calibratingSince = -1L
    private val calibrationSamples = ArrayList<Float>()

    val isCalibrating: Boolean
        get() = calibratingSince >= 0L

    fun reset() {
        gapChannel.reset()
        widthChannel.reset()
        calibratingSince = -1L
        calibrationSamples.clear()
    }

    fun startCalibration(nowMs: Long) {
        calibrationSamples.clear()
        calibratingSince = nowMs
    }

    /**
     * Возвращает оценку шума покоя (90-й процентиль колебаний раствора рта).
     * Используется, чтобы поднять порог срабатывания и убрать ложные запуски.
     */
    fun finishCalibration(): Float? {
        if (calibratingSince < 0L) return null
        calibratingSince = -1L
        if (calibrationSamples.size < 12) return null
        val sorted = calibrationSamples.sorted()
        val index = ((sorted.size - 1) * 0.9f).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    fun cancelCalibration() {
        calibratingSince = -1L
        calibrationSamples.clear()
    }

    fun onObservation(observation: FaceObservation, timestampMs: Long): ChewingSnapshot {
        val current = params

        if (!observation.present) {
            return ChewingSnapshot(timestampMs = timestampMs, threshold = current.prominence)
        }
        if (!observation.reliable) {
            return ChewingSnapshot(
                timestampMs = timestampMs,
                facePresent = true,
                threshold = current.prominence,
            )
        }

        gapChannel.push(timestampMs, observation.openRatio)
        widthChannel.push(timestampMs, observation.widthRatio)

        val from = timestampMs - current.windowMs
        val gapStats = gapChannel.analyse(from, current.prominence)
        val widthStats = widthChannel.analyse(from, current.prominence)

        if (isCalibrating) {
            collectCalibrationSample(observation.openRatio, from)
        }

        val peaks = max(gapStats.peaks, widthStats.peaks)
        val lastPeakAt = max(gapStats.lastPeakAt, widthStats.lastPeakAt)
        val amplitude = max(gapStats.amplitude, widthStats.amplitude)
        val chewingActive = lastPeakAt > 0L && timestampMs - lastPeakAt <= CHEW_ACTIVE_WINDOW_MS

        return ChewingSnapshot(
            timestampMs = timestampMs,
            facePresent = true,
            faceReliable = true,
            chewing = peaks >= current.minPeaks,
            chewingActive = chewingActive,
            peaks = peaks,
            amplitude = amplitude,
            openRatio = observation.openRatio,
            threshold = current.prominence,
            recent = gapChannel.valuesSince(from).toList(),
        )
    }

    private fun collectCalibrationSample(openRatio: Float, from: Long) {
        val since = gapChannel.valuesSince(from)
        if (since.isEmpty()) return
        var sum = 0f
        for (value in since) sum += value
        val mean = sum / since.size
        calibrationSamples.add(abs(openRatio - mean))
    }
}
