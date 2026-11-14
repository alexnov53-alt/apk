package com.chewplay.vision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Синтетический сигнал: раствор рта колеблется с частотой 1 Гц при 10 кадрах в секунду.
 * Такой ритм детектор обязан распознать как жевание, а ровный сигнал — нет.
 */
class ChewingSignalTest {

    @Test
    fun detectsOneHertzChewing() {
        val signal = ChewingSignal()
        signal.params = ChewingParams(prominence = 0.01f, minPeaks = 2, windowMs = 2600L)

        var timestamp = 10_000L
        var sawChewing = false

        repeat(45) { frame ->
            val phase = frame * 2.0 * PI / 10.0
            val openRatio = 0.02f + 0.02f * sin(phase).toFloat()
            val snapshot = signal.onObservation(observation(openRatio), timestamp)
            if (snapshot.chewing) sawChewing = true
            timestamp += 100L
        }

        assertTrue("ритмичное жевание должно распознаваться", sawChewing)
    }

    @Test
    fun silenceAfterChewingResetsDetection() {
        val signal = ChewingSignal()
        signal.params = ChewingParams(prominence = 0.01f, minPeaks = 2, windowMs = 2600L)

        var timestamp = 10_000L
        repeat(45) { frame ->
            val phase = frame * 2.0 * PI / 10.0
            signal.onObservation(observation(0.02f + 0.02f * sin(phase).toFloat()), timestamp)
            timestamp += 100L
        }

        var last = signal.onObservation(observation(0.02f), timestamp)
        repeat(35) {
            timestamp += 100L
            last = signal.onObservation(observation(0.02f), timestamp)
        }

        assertFalse("после трёх секунд покоя жевания быть не должно", last.chewing)
        assertFalse(last.chewingActive)
    }

    private fun observation(openRatio: Float) = FaceObservation(
        present = true,
        reliable = true,
        openRatio = openRatio,
        widthRatio = 0.35f,
        eulerX = 0f,
        eulerY = 0f,
    )
}
