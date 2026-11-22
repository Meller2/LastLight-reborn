package dev.lastlight.reborn.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.util.Locale
import kotlin.math.roundToInt

data class DeviceHardwareInfo(
    val deviceName: String,
    val socName: String,
    val gpuName: String,
    val ramGb: Int,
    val androidVersion: String
)

object DeviceInfo {
    fun readHardware(context: Context): DeviceHardwareInfo {
        val mfr = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val deviceName = if (model.startsWith(mfr, ignoreCase = true) || mfr.isEmpty()) {
            model.ifEmpty { "Android" }
        } else {
            "${mfr.replaceFirstChar { it.titlecase(Locale.US) }} $model"
        }

        val rawSoc = Build.SOC_MODEL.orEmpty().trim()
        val (socLabel, gpuLabel) = mapSocAndGpu(rawSoc)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        val totalBytes = if (am != null) {
            am.getMemoryInfo(mi)
            mi.totalMem
        } else 0L
        val ramGb = if (totalBytes > 0L) {
            val rawGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            // Round to nearest standard memory tier (4, 6, 8, 12, 16, 24)
            listOf(4, 6, 8, 12, 16, 24, 32).minByOrNull { kotlin.math.abs(it - rawGb) } ?: rawGb.roundToInt()
        } else 0

        val androidVersion = "Android ${Build.VERSION.RELEASE}"
        return DeviceHardwareInfo(
            deviceName = deviceName,
            socName = socLabel,
            gpuName = gpuLabel,
            ramGb = ramGb,
            androidVersion = androidVersion
        )
    }

    fun readBatteryTempC(context: Context): Float? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        return if (tenths > 0 && tenths < 1200) tenths / 10f else null
    }

    private fun mapSocAndGpu(rawSoc: String): Pair<String, String> {
        val upper = rawSoc.uppercase(Locale.US)
        return when {
            upper.startsWith("SM8750") -> "Snapdragon 8 Elite" to "Adreno 830"
            upper.startsWith("SM8650") -> "Snapdragon 8 Gen 3" to "Adreno 750"
            upper.startsWith("SM8635") -> "Snapdragon 8s Gen 3" to "Adreno 735"
            upper.startsWith("SM8550") -> "Snapdragon 8 Gen 2" to "Adreno 740"
            upper.startsWith("SM8475") -> "Snapdragon 8+ Gen 1" to "Adreno 730"
            upper.startsWith("SM8450") -> "Snapdragon 8 Gen 1" to "Adreno 730"
            upper.startsWith("SM7675") -> "Snapdragon 7+ Gen 3" to "Adreno 732"
            upper.startsWith("SM7475") -> "Snapdragon 7+ Gen 2" to "Adreno 725"
            upper.startsWith("SM8350") -> "Snapdragon 888" to "Adreno 660"
            upper.startsWith("SM8250") -> "Snapdragon 865 / 870" to "Adreno 650"
            rawSoc.isNotBlank() -> rawSoc to "Vulkan GPU"
            else -> (Build.HARDWARE ?: "ARM64 SoC") to "Vulkan GPU"
        }
    }
}
