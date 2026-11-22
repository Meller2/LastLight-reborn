package dev.lastlight.reborn.data

import android.content.Context
import org.json.JSONObject
import java.io.File

enum class PerfPreset(val id: String, val fpsCap: Int, val resScale: Float, val upscaler: String) {
    COOL("cool", 30, 0.75f, "sgsr1"),
    BALANCED("balanced", 45, 0.85f, "sgsr1"),
    MAX("max", 60, 1.0f, "sgsr1"),
    CUSTOM("custom", 30, 1.0f, "sgsr1");

    companion object {
        fun fromId(id: String?): PerfPreset = entries.firstOrNull { it.id == id } ?: COOL
    }
}

enum class GyroMode(val id: String) {
    OFF("off"),
    ADS("ads"),
    ALWAYS("always");

    companion object {
        fun fromId(id: String?): GyroMode = entries.firstOrNull { it.id == id } ?: ADS
    }
}

data class LauncherConfig(
    val language: String = "ru",
    val gameFolder: String = "/sdcard/Download/content/Games/Nintendo/Games/Metro Last Light Redux",
    val turnipZipPath: String = "/sdcard/Download/content/Games/Nintendo/Turnip_v26.0.0_R8.zip",
    val useTurnip: Boolean = true,
    val preset: PerfPreset = PerfPreset.COOL,
    val fpsCap: Int = 30,
    val resScale: Float = 0.75f,
    val upscaler: String = "sgsr1",
    val fillScreen: Boolean = false,
    val showPerfOverlay: Boolean = true,
    val touchLookSensitivity: Float = 1.35f,
    val touchAdsSensitivity: Float = 0.75f,
    val touchDeadzoneComp: Float = 0.16f,
    val adsToggle: Boolean = true,
    val hudOpacity: Float = 0.68f,
    val touchHudEnabled: Boolean = true,
    val gyroMode: GyroMode = GyroMode.ADS,
    val gyroSensitivity: Float = 1.25f
)

/**
 * Process-safe JSON config stored in filesDir so changes made inside GameActivity (:game)
 * are immediately persisted and visible to MainActivity and future launches.
 */
class AppPrefs(context: Context) {
    private val file = File(context.applicationContext.filesDir, "lastlight_config.json")

    fun load(): LauncherConfig {
        if (!file.isFile) return LauncherConfig()
        return runCatching {
            val j = JSONObject(file.readText())
            val preset = PerfPreset.fromId(j.optString("preset", PerfPreset.COOL.id))
            LauncherConfig(
                language = j.optString("language", "ru"),
                gameFolder = j.optString("gameFolder", "/sdcard/Download/content/Games/Nintendo/Games/Metro Last Light Redux"),
                turnipZipPath = j.optString("turnipZipPath", "/sdcard/Download/content/Games/Nintendo/Turnip_v26.0.0_R8.zip"),
                useTurnip = j.optBoolean("useTurnip", true),
                preset = preset,
                fpsCap = j.optInt("fpsCap", preset.fpsCap),
                resScale = j.optDouble("resScale", preset.resScale.toDouble()).toFloat(),
                upscaler = j.optString("upscaler", preset.upscaler),
                fillScreen = j.optBoolean("fillScreen", false),
                showPerfOverlay = j.optBoolean("showPerfOverlay", true),
                touchLookSensitivity = j.optDouble("touchLookSensitivity", 1.35).toFloat(),
                touchAdsSensitivity = j.optDouble("touchAdsSensitivity", 0.75).toFloat(),
                touchDeadzoneComp = j.optDouble("touchDeadzoneComp", 0.16).toFloat(),
                adsToggle = j.optBoolean("adsToggle", true),
                hudOpacity = j.optDouble("hudOpacity", 0.68).toFloat(),
                touchHudEnabled = j.optBoolean("touchHudEnabled", true),
                gyroMode = GyroMode.fromId(j.optString("gyroMode", GyroMode.ADS.id)),
                gyroSensitivity = j.optDouble("gyroSensitivity", 1.25).toFloat()
            )
        }.getOrElse { LauncherConfig() }
    }

    fun save(c: LauncherConfig) {
        val j = JSONObject().apply {
            put("language", c.language)
            put("gameFolder", c.gameFolder)
            put("turnipZipPath", c.turnipZipPath)
            put("useTurnip", c.useTurnip)
            put("preset", c.preset.id)
            put("fpsCap", c.fpsCap)
            put("resScale", c.resScale.toDouble())
            put("upscaler", c.upscaler)
            put("fillScreen", c.fillScreen)
            put("showPerfOverlay", c.showPerfOverlay)
            put("touchLookSensitivity", c.touchLookSensitivity.toDouble())
            put("touchAdsSensitivity", c.touchAdsSensitivity.toDouble())
            put("touchDeadzoneComp", c.touchDeadzoneComp.toDouble())
            put("adsToggle", c.adsToggle)
            put("hudOpacity", c.hudOpacity.toDouble())
            put("touchHudEnabled", c.touchHudEnabled)
            put("gyroMode", c.gyroMode.id)
            put("gyroSensitivity", c.gyroSensitivity.toDouble())
        }
        val tmp = File(file.parentFile, "lastlight_config.json.tmp")
        tmp.writeText(j.toString(2))
        tmp.renameTo(file)
    }
}
