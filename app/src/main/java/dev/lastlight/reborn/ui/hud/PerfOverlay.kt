package dev.lastlight.reborn.ui.hud

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lastlight.reborn.nativecore.NativeBridge
import dev.lastlight.reborn.ui.theme.MetroColors
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PerfOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fps by remember { mutableFloatStateOf(0f) }
    var tempC by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val out = FloatArray(6)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        while (true) {
            if (NativeBridge.getFps(out) != 0) {
                fps = out[0]
            }
            val bat = context.registerReceiver(null, filter)
            val rawTemp = bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (rawTemp > 0) tempC = rawTemp / 10f
            delay(500)
        }
    }

    val tempColor = when {
        tempC >= 47f -> MetroColors.DangerRed
        tempC >= 42f -> MetroColors.Amber
        else -> MetroColors.PhosphorGreen
    }

    Row(
        modifier = modifier
            .background(Color(0xAA0B0D0F), RoundedCornerShape(6.dp))
            .border(1.dp, MetroColors.BorderSteel.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format(Locale.US, "%.0f FPS", fps),
            color = MetroColors.PhosphorGreen,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Text(
            text = String.format(Locale.US, "%.1f°C", tempC),
            color = tempColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
