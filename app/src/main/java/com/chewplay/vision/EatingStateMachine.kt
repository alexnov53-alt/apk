package com.chewplay.vision

import com.chewplay.data.AppSettings

data class EatingUpdate(
    val eating: Boolean,
    val reason: PauseReason?,
)

/**
 * Гистерезис: не дёргаем видео на каждом кадре.
 *
 * - чтобы поставить паузу, нужно [AppSettings.pauseDelayMs] без признаков жевания;
 * - чтобы запустить видео, нужно [AppSettings.resumeDelayMs] непрерывного жевания;
 * - если лицо пропало дольше [AppSettings.faceLostDelayMs] — пауза.
 */
class EatingStateMachine {

    private var eating = false
    private var lastChewAt = -1L
    private var chewBeganAt = -1L
    private var lastFaceAt = -1L
    private var faceSeen = false

    fun reset() {
        eating = false
        lastChewAt = -1L
        chewBeganAt = -1L
        lastFaceAt = -1L
        faceSeen = false
    }

    fun update(
        now: Long,
        facePresent: Boolean,
        chewingActive: Boolean,
        settings: AppSettings,
    ): EatingUpdate {
        if (facePresent) {
            lastFaceAt = now
            faceSeen = true
        }
        if (chewingActive) {
            lastChewAt = now
        }

        if (eating) {
            val chewExpired = lastChewAt < 0L || now - lastChewAt > settings.pauseDelayMs
            val faceExpired =
                !facePresent && (!faceSeen || now - lastFaceAt > settings.faceLostDelayMs)
            if (chewExpired || faceExpired) {
                eating = false
                chewBeganAt = -1L
                return EatingUpdate(
                    eating = false,
                    reason = if (!facePresent) PauseReason.NO_FACE else PauseReason.NOT_CHEWING,
                )
            }
            return EatingUpdate(eating = true, reason = null)
        }

        if (!facePresent) {
            chewBeganAt = -1L
            return EatingUpdate(eating = false, reason = PauseReason.NO_FACE)
        }
        if (!chewingActive) {
            chewBeganAt = -1L
            return EatingUpdate(eating = false, reason = PauseReason.NOT_CHEWING)
        }
        if (chewBeganAt < 0L) {
            chewBeganAt = now
        }
        if (now - chewBeganAt >= settings.resumeDelayMs) {
            eating = true
            return EatingUpdate(eating = true, reason = null)
        }
        return EatingUpdate(eating = false, reason = PauseReason.NOT_CHEWING)
    }
}
