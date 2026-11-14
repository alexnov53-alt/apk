package com.chewplay.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chewplay.RuntimeStatus
import com.chewplay.data.AppSettings
import com.chewplay.vision.PauseReason

@Composable
fun StatusPill(
    status: RuntimeStatus,
    settings: AppSettings,
    cameraGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val (targetColor, text) = statusVisual(status, settings, cameraGranted)
    val dotColor by animateColorAsState(targetColor, label = "statusDot")

    Row(
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusVisual(
    status: RuntimeStatus,
    settings: AppSettings,
    cameraGranted: Boolean,
): Pair<Color, String> = when {
    !cameraGranted -> Color(0xFFE53935) to "Нет доступа к камере"
    status.cameraError != null -> Color(0xFFE53935) to "Ошибка камеры"
    settings.videoUri == null -> Color(0xFF9E9E9E) to "Видео не выбрано"
    !settings.enforcementEnabled -> Color(0xFF9E9E9E) to "Контроль выключен"
    status.eating -> Color(0xFF66BB6A) to "Жуёт — видео идёт"
    status.reason == PauseReason.NO_FACE -> Color(0xFFFFB300) to "Не вижу лицо — пауза"
    status.facePresent && !status.faceReliable -> Color(0xFFFFB300) to "Лицо мелко или повёрнуто"
    else -> Color(0xFFFFB300) to "Не жуёт — пауза"
}
