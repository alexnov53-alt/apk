package com.chewplay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chewplay.RuntimeStatus
import kotlin.math.abs
import kotlin.math.max

/**
 * Мини-осциллограф сигнала «раствор рта». Нужен, чтобы вручную подобрать
 * чувствительность: настоящие зубцы жевания видны как ровный ритм.
 */
@Composable
fun DebugOverlay(status: RuntimeStatus, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(240.dp)
            .background(Color(0xCC0B0B0B), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            text = "пики ${status.peaks} · порог ${"%.3f".format(status.threshold)} · амп ${"%.3f".format(status.amplitude)}",
            color = Color(0xFF90A4AE),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
        ) {
            val data = status.recent
            if (data.size > 2) {
                var sum = 0f
                for (value in data) sum += value
                val mean = sum / data.size

                var maxDeviation = 1e-4f
                for (value in data) {
                    maxDeviation = max(maxDeviation, abs(value - mean))
                }

                val path = Path()
                val canvasWidth = size.width
                val canvasHeight = size.height
                data.forEachIndexed { index, value ->
                    val x = canvasWidth * index / (data.size - 1).toFloat()
                    val y = canvasHeight / 2f - (value - mean) / maxDeviation * (canvasHeight / 2f * 0.85f)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(path, Color(0xFF64B5F6), style = Stroke(width = 2f))
                drawLine(
                    color = Color(0x55FFB300),
                    start = Offset(0f, canvasHeight / 2f),
                    end = Offset(canvasWidth, canvasHeight / 2f),
                    strokeWidth = 1f,
                )
            }
        }

        Text(
            text = if (status.facePresent) "лицо в кадре" else "лица нет",
            color = Color(0xFF90A4AE),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
