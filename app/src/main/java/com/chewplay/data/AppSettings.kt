package com.chewplay.data

/**
 * Настройки приложения. Хранятся в SharedPreferences и отдаются в UI как StateFlow.
 */
data class AppSettings(
    /** URI выбранного видео (content://...). null — видео ещё не выбрано. */
    val videoUri: String? = null,
    /** Чувствительность детектора жевания: 1 — ловит слабые движения, 0 — только явное жевание. */
    val sensitivity: Float = 0.55f,
    /** Через сколько миллисекунд после последнего «жевательного» движения ставить паузу. */
    val pauseDelayMs: Int = 2500,
    /** Сколько миллисекунд жевания нужно, чтобы снова запустить видео. */
    val resumeDelayMs: Int = 450,
    /** Через сколько миллисекунд после потери лица ставить паузу. */
    val faceLostDelayMs: Int = 1500,
    /** true — фронтальная камера, false — основная. */
    val useFrontCamera: Boolean = true,
    /** Показывать маленькое превью камеры поверх видео. */
    val showPreview: Boolean = true,
    /** Показывать плашку статуса. */
    val showStatus: Boolean = true,
    /** Показывать отладочный график сигнала жевания. */
    val showDebug: Boolean = false,
    /** Шум, измеренный при калибровке (доля ширины лица). */
    val calibrationNoise: Float = 0f,
    /** Главный выключатель контроля: когда выключен, видео просто играет. */
    val enforcementEnabled: Boolean = true,
)
