package com.chewplay.vision

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Обёртка над ML Kit Face Detection.
 *
 * Нам не нужны «красивые» 468 точек: достаточно контуров губ.
 * UPPER_LIP_BOTTOM и LOWER_LIP_TOP — это внутренние края губ,
 * расстояние между ними и есть раствор рта.
 */
class FaceAnalyzer(
    private val onObservation: (FaceObservation, Long) -> Unit,
    private val onError: (String) -> Unit,
) {

    private val options: FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setMinFaceSize(0.15f)
        .build()

    private val detector: FaceDetector = FaceDetection.getClient(options)
    private val busy = AtomicBoolean(false)
    private var lastTimestamp = 0L

    /**
     * Отдаёт кадр в ML Kit. Возвращает true, если кадр принят на обработку
     * (тогда владелец кадра НЕ должен его освобождать), и false, если кадр
     * пропущен и его можно переработать.
     */
    fun analyze(bitmap: Bitmap, rotationDegrees: Int): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        val timestamp = SystemClock.elapsedRealtime()
        return try {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces -> onSuccess(faces, timestamp) }
                .addOnFailureListener { error ->
                    busy.set(false)
                    Log.w(TAG, "ML Kit не смог обработать кадр", error)
                    onError(error.message ?: "Ошибка распознавания лица")
                }
            true
        } catch (t: Throwable) {
            busy.set(false)
            Log.e(TAG, "Кадр не отправлен в ML Kit", t)
            false
        }
    }

    fun close() {
        runCatching { detector.close() }
    }

    private fun onSuccess(faces: List<Face>, timestamp: Long) {
        busy.set(false)
        if (timestamp <= lastTimestamp) return
        lastTimestamp = timestamp
        val face = faces.maxByOrNull { it.boundingBox.width() }
        onObservation(if (face == null) FaceObservation.NONE else describe(face), timestamp)
    }

    private fun describe(face: Face): FaceObservation {
        val faceWidth = face.boundingBox.width().toFloat()
        val eulerX = face.headEulerAngleX
        val eulerY = face.headEulerAngleY

        val upper = centerOf(face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points)
        val lower = centerOf(face.getContour(FaceContour.LOWER_LIP_TOP)?.points)

        if (faceWidth < MIN_FACE_WIDTH_PX || upper == null || lower == null) {
            return FaceObservation(
                present = true,
                reliable = false,
                openRatio = 0f,
                widthRatio = 0f,
                eulerX = eulerX,
                eulerY = eulerY,
            )
        }

        val openRatio = hypot(upper.x - lower.x, upper.y - lower.y) / faceWidth
        val widthRatio = horizontalSpan(face.getContour(FaceContour.UPPER_LIP_TOP)?.points) / faceWidth
        val reliable = abs(eulerY) <= MAX_YAW_DEGREES && abs(eulerX) <= MAX_PITCH_DEGREES

        return FaceObservation(
            present = true,
            reliable = reliable,
            openRatio = openRatio,
            widthRatio = widthRatio,
            eulerX = eulerX,
            eulerY = eulerY,
        )
    }

    private fun centerOf(points: List<PointF>?): PointF? {
        if (points.isNullOrEmpty()) return null
        var x = 0f
        var y = 0f
        for (point in points) {
            x += point.x
            y += point.y
        }
        return PointF(x / points.size, y / points.size)
    }

    private fun horizontalSpan(points: List<PointF>?): Float {
        if (points.isNullOrEmpty()) return 0f
        var minimum = Float.MAX_VALUE
        var maximum = -Float.MAX_VALUE
        for (point in points) {
            if (point.x < minimum) minimum = point.x
            if (point.x > maximum) maximum = point.x
        }
        return (maximum - minimum).coerceAtLeast(0f)
    }

    private companion object {
        const val TAG = "ChewPlayVision"
        const val MIN_FACE_WIDTH_PX = 80f
        const val MAX_YAW_DEGREES = 45f
        const val MAX_PITCH_DEGREES = 35f
    }
}
