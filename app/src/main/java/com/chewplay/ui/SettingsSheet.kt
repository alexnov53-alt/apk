package com.chewplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chewplay.RuntimeStatus
import com.chewplay.data.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    status: RuntimeStatus,
    onDismiss: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onPickVideo: () -> Unit,
    onCalibrate: () -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Видео играет, пока человек жуёт. Точность настраивается под конкретное лицо " +
                    "и освещение — проще всего через калибровку.",
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
            )

            SectionTitle("Видео")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (settings.videoUri == null) "Видео не выбрано" else "Видео выбрано",
                    color = Color(0xFFB0BEC5),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onPickVideo) {
                    Text("Выбрать")
                }
            }

            SectionTitle("Контроль")
            SwitchRow(
                title = "Следить за жеванием",
                checked = settings.enforcementEnabled,
                onCheckedChange = { value -> onUpdate { it.copy(enforcementEnabled = value) } },
            )
            SliderRow(
                title = "Чувствительность",
                value = settings.sensitivity,
                valueRange = 0f..1f,
                steps = 9,
                valueLabel = sensitivityLabel(settings.sensitivity),
                onValueChange = { value -> onUpdate { it.copy(sensitivity = value.coerceIn(0f, 1f)) } },
            )
            SliderRow(
                title = "Пауза после остановки",
                value = settings.pauseDelayMs / 1000f,
                valueRange = 1f..6f,
                steps = 9,
                valueLabel = "%.1f с".format(settings.pauseDelayMs / 1000f),
                onValueChange = { value ->
                    onUpdate { it.copy(pauseDelayMs = (value * 1000f).roundToInt()) }
                },
            )
            SliderRow(
                title = "Задержка возобновления",
                value = settings.resumeDelayMs / 1000f,
                valueRange = 0f..1.5f,
                steps = 5,
                valueLabel = "%.2f с".format(settings.resumeDelayMs / 1000f),
                onValueChange = { value ->
                    onUpdate { it.copy(resumeDelayMs = (value * 1000f).roundToInt()) }
                },
            )

            SectionTitle("Камера")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.useFrontCamera,
                    onClick = { onUpdate { it.copy(useFrontCamera = true) } },
                    label = { Text("Фронтальная") },
                )
                FilterChip(
                    selected = !settings.useFrontCamera,
                    onClick = { onUpdate { it.copy(useFrontCamera = false) } },
                    label = { Text("Основная") },
                )
            }
            SwitchRow(
                title = "Превью камеры",
                checked = settings.showPreview,
                onCheckedChange = { value -> onUpdate { it.copy(showPreview = value) } },
            )

            SectionTitle("Интерфейс")
            SwitchRow(
                title = "Плашка статуса",
                checked = settings.showStatus,
                onCheckedChange = { value -> onUpdate { it.copy(showStatus = value) } },
            )
            SwitchRow(
                title = "Отладочный график",
                checked = settings.showDebug,
                onCheckedChange = { value -> onUpdate { it.copy(showDebug = value) } },
            )

            SectionTitle("Калибровка")
            Text(
                text = "Сядьте спокойно, не жуя, и нажмите кнопку. Три секунды приложение измеряет " +
                    "фоновый шум (мимику, дыхание) и поднимает порог, чтобы видео не дёргалось зря.",
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onCalibrate, enabled = !status.calibrating) {
                    Text(if (status.calibrating) "Измеряю…" else "Калибровать")
                }
                if (settings.calibrationNoise > 0f) {
                    Text(
                        text = "шум %.3f".format(settings.calibrationNoise),
                        color = Color(0xFF9E9E9E),
                        fontSize = 12.sp,
                    )
                }
            }

            SectionTitle("Прочее")
            TextButton(onClick = onReset) {
                Text("Сбросить настройки")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(
        text = text,
        color = Color(0xFF9CCC65),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, fontSize = 15.sp)
            Text(text = valueLabel, color = Color(0xFF9CCC65), fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

private fun sensitivityLabel(value: Float): String = when {
    value < 0.34f -> "низкая"
    value < 0.67f -> "средняя"
    else -> "высокая"
}
