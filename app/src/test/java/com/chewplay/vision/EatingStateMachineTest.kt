package com.chewplay.vision

import com.chewplay.data.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EatingStateMachineTest {

    private val settings = AppSettings(
        pauseDelayMs = 2000,
        resumeDelayMs = 300,
        faceLostDelayMs = 1000,
    )

    @Test
    fun startsAfterSustainedChewing() {
        val machine = EatingStateMachine()
        var now = 1_000L

        assertFalse(
            machine.update(now, facePresent = true, chewingActive = false, settings = settings).eating,
        )

        machine.update(now, facePresent = true, chewingActive = true, settings = settings)
        now += 350L
        val update = machine.update(now, facePresent = true, chewingActive = true, settings = settings)

        assertTrue("после 350 мс жевания видео должно запускаться", update.eating)
    }

    @Test
    fun pausesAfterChewingStops() {
        val machine = EatingStateMachine()
        var now = 1_000L

        repeat(5) {
            now += 100L
            machine.update(now, facePresent = true, chewingActive = true, settings = settings)
        }
        assertTrue(machine.update(now, true, true, settings).eating)

        now += 2_100L
        val update = machine.update(now, facePresent = true, chewingActive = false, settings = settings)

        assertFalse("после паузы без жевания видео должно встать на паузу", update.eating)
    }

    @Test
    fun pausesWhenFaceDisappears() {
        val machine = EatingStateMachine()
        var now = 1_000L

        repeat(5) {
            now += 100L
            machine.update(now, facePresent = true, chewingActive = true, settings = settings)
        }
        assertTrue(machine.update(now, true, true, settings).eating)

        now += 1_100L
        val update = machine.update(now, facePresent = false, chewingActive = false, settings = settings)

        assertFalse("если лицо пропало, видео должно встать на паузу", update.eating)
    }
}
