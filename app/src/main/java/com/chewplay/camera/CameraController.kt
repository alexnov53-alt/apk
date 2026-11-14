package com.chewplay.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Отдельный поток для ImageAnalysis + ML Kit: кадры не должны попадать в UI-поток.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (Bitmap, Int) -> Unit,
    private val onError: (String) -> Unit = {},
) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var useFront = true
    private var running = false

    fun start(front: Boolean) {
        useFront = front
        running = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                provider = future.get()
                bind()
            } catch (t: Throwable) {
                Log.e(TAG, "Камера недоступна", t)
                onError("Камера недоступна")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        running = false
        provider?.unbindAll()
    }

    fun attachPreview(view: PreviewView?) {
        if (previewView === view) return
        previewView = view
        if (running && provider != null) {
            bind()
        }
    }

    fun close() {
        running = false
        provider?.unbindAll()
        provider = null
        analysisExecutor.shutdown()
    }

    private fun bind() {
        val cameraProvider = provider ?: return
        cameraProvider.unbindAll()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetResolution(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
            .build()
        analysis.setAnalyzer(analysisExecutor) { proxy -> handleFrame(proxy) }

        val currentPreviewView = previewView
        val preview = currentPreviewView?.let { view ->
            Preview.Builder()
                .setTargetResolution(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
                .build()
                .also { it.setSurfaceProvider(view.surfaceProvider) }
        }

        val selector = if (useFront) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            if (preview != null) {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } else {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Не удалось привязать камеру, пробую другую", t)
            val fallback = if (useFront) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            try {
                if (preview != null) {
                    cameraProvider.bindToLifecycle(lifecycleOwner, fallback, preview, analysis)
                } else {
                    cameraProvider.bindToLifecycle(lifecycleOwner, fallback, analysis)
                }
            } catch (t2: Throwable) {
                Log.e(TAG, "Камера недоступна", t2)
                onError("Не удалось включить камеру")
            }
        }
    }

    private fun handleFrame(proxy: ImageProxy) {
        try {
            val bitmap = proxy.toRgbaBitmap() ?: return
            onFrame(bitmap, proxy.imageInfo.rotationDegrees)
        } catch (t: Throwable) {
            Log.w(TAG, "Кадр не обработан", t)
        } finally {
            proxy.close()
        }
    }

    /**
     * CameraX умеет отдавать RGBA_8888 — тогда не нужна ручная конвертация из YUV.
     * Строка буфера может быть выровнена, поэтому учитываем rowStride.
     */
    private fun ImageProxy.toRgbaBitmap(): Bitmap? {
        val plane = planes.firstOrNull() ?: return null
        if (plane.pixelStride != 4) return null

        val rowStride = plane.rowStride
        val bufferWidth = rowStride / plane.pixelStride
        if (bufferWidth <= 0) return null

        val buffer = plane.buffer
        buffer.rewind()

        val raw = Bitmap.createBitmap(bufferWidth, height, Bitmap.Config.ARGB_8888)
        if (buffer.remaining() < raw.byteCount) {
            raw.recycle()
            return null
        }
        raw.copyPixelsFromBuffer(buffer)

        if (bufferWidth == width) return raw
        val cropped = Bitmap.createBitmap(raw, 0, 0, width, height)
        raw.recycle()
        return cropped
    }

    private companion object {
        const val TAG = "ChewPlayCamera"
        const val ANALYSIS_WIDTH = 640
        const val ANALYSIS_HEIGHT = 480
    }
}
