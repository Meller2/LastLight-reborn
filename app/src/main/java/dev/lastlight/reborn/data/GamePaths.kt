package dev.lastlight.reborn.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

data class GameFilesStatus(
    val nupFile: File?,
    val packDir: File?,
    val patchedDir: File?,
    val shaderLibFile: File?,
    val turnipName: String?,
    val turnipReady: Boolean,
    val iconFile: File?
) {
    val isReadyToPlay: Boolean
        get() = nupFile != null && patchedDir != null
}

object GamePaths {
    const val TITLE_ID = "0100F0400E850000"

    fun inspect(context: Context, config: LauncherConfig): GameFilesStatus {
        val folder = File(config.gameFolder)
        val nup = if (folder.isDirectory) {
            folder.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".nup", ignoreCase = true) }
        } else null
        val icon = if (folder.isDirectory) {
            folder.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".png", ignoreCase = true) }
        } else null

        val packCandidates = listOf(
            File(folder, "$TITLE_ID-work/android-pack"),
            File(folder, "android-pack"),
            folder
        )
        val packDir = packCandidates.firstOrNull { isValidPackDir(it) }
        val patchedDir = packDir?.let { p ->
            File(p, "patched").takeIf { File(it, "main.patched.bin").isFile || File(it, "main.bin").isFile } ?: p
        }
        val shaderLib = packDir?.let { File(it, "shaders") }
            ?.takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.firstOrNull { it.isFile && it.name == "dmsh_vk.lib" }

        val turnipInfo = inspectTurnip(context, config.turnipZipPath)
        return GameFilesStatus(
            nupFile = nup,
            packDir = packDir,
            patchedDir = patchedDir,
            shaderLibFile = shaderLib,
            turnipName = turnipInfo.first,
            turnipReady = turnipInfo.second,
            iconFile = icon
        )
    }

    private fun isValidPackDir(d: File): Boolean {
        if (!d.isDirectory) return false
        return File(d, "main.patched.bin").isFile ||
            File(d, "pack.tsv").isFile ||
            File(d, "patched/main.patched.bin").isFile ||
            File(d, "main.bin").isFile
    }

    fun autoDetectGameFolder(): String? {
        val roots = listOf(
            File("/sdcard/Download/content/Games/Nintendo/Games/Metro Last Light Redux"),
            File("/sdcard/Download/content/Games/Nintendo/Games"),
            File("/sdcard/Peanut Roms"),
            File("/sdcard/Download")
        )
        for (r in roots) {
            if (!r.isDirectory) continue
            if (isFolderWithMetro(r)) return r.absolutePath
            r.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                if (isFolderWithMetro(sub)) return sub.absolutePath
            }
        }
        return null
    }

    private fun isFolderWithMetro(dir: File): Boolean {
        val hasNup = dir.listFiles()?.any { it.isFile && it.name.endsWith(".nup", ignoreCase = true) && it.name.contains(TITLE_ID, ignoreCase = true) } == true
        val hasPack = isValidPackDir(File(dir, "$TITLE_ID-work/android-pack")) || isValidPackDir(dir)
        return hasNup || hasPack
    }

    fun resolveTreeUriToPath(treeUri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val parts = docId.split(":")
        if (parts.size == 2 && parts[0].equals("primary", ignoreCase = true)) {
            return "/sdcard/${parts[1]}"
        }
        if (parts.size == 2) {
            return "/storage/${parts[0]}/${parts[1]}"
        }
        return null
    }

    private fun driverCacheDir(context: Context): File = File(context.filesDir, "gpu_driver")

    private fun inspectTurnip(context: Context, zipPath: String): Pair<String?, Boolean> {
        val importedMeta = File(driverCacheDir(context), "driver_name.txt")
        val importedSo = driverCacheDir(context).listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".so") }
        if (importedSo != null && importedMeta.isFile) {
            return importedMeta.readText().trim() to true
        }
        val zipFile = File(zipPath)
        if (zipFile.isFile) {
            val name = readTurnipMetaName(zipFile) ?: zipFile.nameWithoutExtension
            return name to true
        }
        return null to false
    }

    private fun readTurnipMetaName(zipFile: File): String? = runCatching {
        ZipFile(zipFile).use { zf ->
            val e = zf.getEntry("meta.json") ?: return null
            val json = JSONObject(zf.getInputStream(e).bufferedReader().use { it.readText() })
            val name = json.optString("name").trim()
            val ver = json.optString("driverVersion").trim()
            when {
                name.isNotEmpty() && ver.isNotEmpty() && !name.contains(ver) -> "$name ($ver)"
                name.isNotEmpty() -> name
                else -> null
            }
        }
    }.getOrNull()

    fun importTurnipFromStream(context: Context, input: InputStream, fallbackName: String): String? {
        val tmpZip = File(context.cacheDir, "imported_turnip.zip")
        FileOutputStream(tmpZip).use { out -> input.copyTo(out) }
        val so = extractTurnipZip(context, tmpZip, fallbackName)
        tmpZip.delete()
        return so
    }

    fun prepareTurnipDriver(context: Context, zipPath: String): String? {
        val cachedDir = driverCacheDir(context)
        val cachedSo = cachedDir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".so") }
        if (cachedSo != null) return cachedSo.absolutePath
        val zipFile = File(zipPath)
        if (!zipFile.isFile) return null
        return extractTurnipZip(context, zipFile, zipFile.nameWithoutExtension)
    }

    private fun extractTurnipZip(context: Context, zipFile: File, fallbackName: String): String? {
        val outDir = driverCacheDir(context)
        outDir.deleteRecursively()
        outDir.mkdirs()

        var preferredName: String? = null
        var displayName: String = fallbackName
        ZipFile(zipFile).use { zf ->
            val metaEntry = zf.getEntry("meta.json")
            if (metaEntry != null) {
                val json = JSONObject(zf.getInputStream(metaEntry).bufferedReader().use { it.readText() })
                preferredName = json.optString("libraryName").takeIf { it.isNotBlank() }
                val n = json.optString("name").trim()
                val v = json.optString("driverVersion").trim()
                if (n.isNotEmpty()) displayName = if (v.isNotEmpty() && !n.contains(v)) "$n ($v)" else n
            }
            var extractedMain: File? = null
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val name = File(e.name).name
                if (name.endsWith(".so")) {
                    val dst = File(outDir, name)
                    zf.getInputStream(e).use { inp ->
                        FileOutputStream(dst).use { out -> inp.copyTo(out) }
                    }
                    dst.setReadable(true, false)
                    dst.setExecutable(true, false)
                    if (preferredName != null && name == preferredName) {
                        extractedMain = dst
                    } else if (extractedMain == null) {
                        extractedMain = dst
                    }
                }
            }
            if (extractedMain != null) {
                File(outDir, "driver_name.txt").writeText(displayName)
                return extractedMain.absolutePath
            }
        }
        return null
    }

    fun savesDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "saves/$TITLE_ID").apply { mkdirs() }
    }

    fun tempDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "temp/$TITLE_ID").apply { mkdirs() }
    }

    fun logsDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "logs").apply { mkdirs() }
    }

    fun buildHostEnv(context: Context, config: LauncherConfig, status: GameFilesStatus): Array<String> {
        val env = mutableListOf<String>()
        val nup = status.nupFile!!
        env += "DM_ROMFS=${nup.absolutePath}:base/romfs"
        env += "DM_SAVE=${savesDir(context).absolutePath}"
        env += "DM_TEMP=${tempDir(context).absolutePath}"
        status.shaderLibFile?.let { env += "DM_VK_SHLIB=${it.absolutePath}" }

        if (config.resScale < 0.99f) {
            env += String.format(Locale.US, "DM_RES_SCALE=%.2f", config.resScale)
        }
        when (config.upscaler) {
            "sgsr1" -> {
                env += "DM_VK_NDSR=4"
                env += "DM_VK_FSR=0"
            }
            "fsr1" -> {
                env += "DM_VK_NDSR=2"
                env += "DM_VK_FSR=1"
            }
            else -> {
                env += "DM_VK_NDSR=1"
                env += "DM_VK_FSR=0"
            }
        }
        if (config.fpsCap > 30) {
            // Unlock 4A Engine's nvnWindowSetPresentInterval(win, 2) 30 FPS cap
            env += "DM_PRESENT_INTERVAL=display"
            env += "DM_PRESENT_HZ=${config.fpsCap}"
        } else {
            env += "DM_PRESENT_INTERVAL=game"
            env += "DM_PRESENT_HZ=60"
        }
        if (config.fpsCap > 0) {
            env += "DM_FPS_TARGET=${config.fpsCap}"
        }

        // Fill ultrawide phone screen (20:9 / 22:9) with true 4A Engine aspect ratio patch:
        // Patches display_device init (0x5a5c8/0x5a5cc/0x5a5f0) AND both dynamic-res update
        // functions (0x5a504, 0x5a6c4) that otherwise overwrite [device+0xb8/0xbc/0xc0] back to 16:9.
        if (config.fillScreen) {
            val wm = context.getSystemService(android.view.WindowManager::class.java)
            val bounds = wm?.maximumWindowMetrics?.bounds
            val rawW = bounds?.width() ?: context.resources.displayMetrics.widthPixels
            val rawH = bounds?.height() ?: context.resources.displayMetrics.heightPixels
            val sw = maxOf(rawW, rawH)
            val sh = minOf(rawW, rawH)
            if (sw > 0 && sh > 0) {
                env += "DM_VK_PICTURE_ASPECT=$sw:$sh"
                env += "DM_ASPECT_PLAN=" + listOf(
                    "85c362cc9790f0edf23749b041953c51,ratio_fmov,m0x5a504,0118211e",
                    "85c362cc9790f0edf23749b041953c51,ratio_movz,m0x5a5c8,29c79152",
                    "85c362cc9790f0edf23749b041953c51,ratio_movk,m0x5a5cc,69fca772",
                    "85c362cc9790f0edf23749b041953c51,ratio_fmov,m0x5a5f0,009941bd",
                    "85c362cc9790f0edf23749b041953c51,ratio_fmov,m0x5a6c4,0118211e"
                ).joinToString(";")
            }
        }

        // Disable host-side raw SixAxis motion so ShooterHudOverlay's ADS-gated gyro has sole control
        env += "DM_MOTION=0"
        env += "DM_PHONE_MOTION=0"

        // Reduce write-protection page fault churn (64 KiB dirty blocks from start) to lower CPU heat
        env += "DM_DIRTY_BLOCK_AT=0"
        env += "DM_DIRTY_BLOCK_KB=64"

        if (config.useTurnip) {
            prepareTurnipDriver(context, config.turnipZipPath)?.let { soPath ->
                env += "DM_VK_DRIVER=$soPath"
            }
        }

        env += "DM_VK_PSO_ASYNC=0"
        env += "DM_VK_OVERLAY=0"
        env += "DM_VK_PRESENT_NEAREST=0"
        env += "DM_LANG=${if (config.language == "ru") "ru" else "en-US"}"
        return env.toTypedArray()
    }
}
