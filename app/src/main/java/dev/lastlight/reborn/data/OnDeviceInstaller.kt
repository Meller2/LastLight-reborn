package dev.lastlight.reborn.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.nxnative.android.PackNative
import dev.peanuts.importer.Native
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

data class InstallState(
    val running: Boolean = false,
    val stepIndex: Int = 0,
    val totalSteps: Int = 4,
    val stageTitle: String = "",
    val detail: String = "",
    val progress: Float = 0f,
    val error: String? = null,
    val completedFolder: String? = null
)

object OnDeviceInstaller {
    private val cancelled = AtomicBoolean(false)
    private val progressRegex = Pattern.compile("^progress:?\\s+(\\d+)(?:\\s+|/)(\\d+)(?:\\s+(\\S+))?")
    private val stageRegex = Pattern.compile("^stage:\\s+(.+)$")

    fun hasFullPacker(context: Context): Boolean = PackNative.isAvailable(context)

    fun resolveDocumentUriToPath(uri: Uri): String? {
        if ("file".equals(uri.scheme, ignoreCase = true)) return uri.path
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val parts = docId.split(":", limit = 2)
        if (parts.size == 2) {
            if (parts[0].equals("primary", ignoreCase = true)) {
                return "/sdcard/${parts[1]}"
            }
            if (parts[0].equals("raw", ignoreCase = true)) {
                return parts[1]
            }
            if (parts[0].isNotBlank() && !parts[0].equals("msf", ignoreCase = true)) {
                return "/storage/${parts[0]}/${parts[1]}"
            }
        }
        return null
    }

    fun importKeysFile(context: Context, uri: Uri): File? {
        val dir = File(context.filesDir, "keys").apply { mkdirs() }
        val dst = File(dir, "prod.keys")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(dst).use { out -> inp.copyTo(out) }
            }
            if (dst.isFile && dst.length() > 32) dst else null
        }.getOrNull()
    }

    fun savedKeysFile(context: Context): File? {
        val f = File(context.filesDir, "keys/prod.keys")
        return f.takeIf { it.isFile && it.length() > 32 }
    }

    fun cancel() {
        cancelled.set(true)
        runCatching { Native.cancel(true) }
        runCatching { PackNative.interruptAsync() }
    }

    fun startInstall(
        context: Context,
        nspPath: String,
        keysFile: File,
        outputDir: File,
        isRussian: Boolean,
        onUpdate: (InstallState) -> Unit
    ) {
        cancelled.set(false)
        val app = context.applicationContext
        val fullPacker = hasFullPacker(app)
        val totalSteps = if (fullPacker) 4 else 1

        Thread({
            fun emit(state: InstallState) = onUpdate(state)

            try {
                outputDir.mkdirs()
                val nspFile = File(nspPath)
                if (!nspFile.isFile) {
                    emit(InstallState(error = if (isRussian) "Файл .nsp не найден: $nspPath" else ".nsp file not found: $nspPath"))
                    return@Thread
                }

                // ---- STEP 1: Unpack .nsp/.xci/.nsz -> .nup via libnximport.so ----
                emit(
                    InstallState(
                        running = true,
                        stepIndex = 1,
                        totalSteps = totalSteps,
                        stageTitle = if (isRussian) "Шаг 1/$totalSteps: Распаковка .nsp → .nup" else "Step 1/$totalSteps: Unpacking .nsp → .nup",
                        detail = if (isRussian) "Проверка ключей и образа..." else "Verifying keys & dump...",
                        progress = 0.02f
                    )
                )

                Native.ensureLoaded()
                Native.cancel(false)
                Native.setThreads(0)

                if (Native.keys(keysFile.absolutePath) != 0) {
                    throw IllegalStateException("prod.keys error: ${Native.lastError()}")
                }

                val probeJson = Native.probe(nspFile.absolutePath)
                    ?: throw IllegalStateException("Probe failed: ${Native.lastError()}")
                val arr = JSONArray(probeJson)
                var foundMetro = false
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val tid = obj.optString("title_id", "").uppercase(Locale.US)
                    val appId = obj.optString("app_id", "").uppercase(Locale.US)
                    if (tid == GamePaths.TITLE_ID || appId == GamePaths.TITLE_ID) {
                        foundMetro = true
                        break
                    }
                }
                if (!foundMetro) {
                    throw IllegalStateException(
                        if (isRussian) "В выбранном образе не найден Metro: Last Light Redux (${GamePaths.TITLE_ID})"
                        else "Metro: Last Light Redux (${GamePaths.TITLE_ID}) not found in selected dump"
                    )
                }

                // Extract cover PNG
                val outPng = File(outputDir, "Metro Last Light Redux [${GamePaths.TITLE_ID}][v0].png")
                Native.icon(nspFile.absolutePath, GamePaths.TITLE_ID, outPng.absolutePath)

                // Write .nup (skip if already valid & non-empty)
                val outNup = File(outputDir, "Metro Last Light Redux [${GamePaths.TITLE_ID}][v0].nup")
                if (!outNup.isFile || outNup.length() < 100L * 1024L * 1024L) {
                    val tmpNup = File(outputDir, "${outNup.name}.part")
                    tmpNup.delete()
                    val rc = Native.writeNup(
                        tmpNup.absolutePath,
                        arrayOf(nspFile.absolutePath),
                        GamePaths.TITLE_ID
                    ) { step, done, total ->
                        val frac = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                        val mbDone = done / (1024.0 * 1024.0)
                        val mbTot = total / (1024.0 * 1024.0)
                        val detail = if (total > 1024 * 1024) {
                            String.format(Locale.US, "%s • %.0f / %.0f MB", step, mbDone, mbTot)
                        } else {
                            "$step ($done/$total)"
                        }
                        emit(
                            InstallState(
                                running = true,
                                stepIndex = 1,
                                totalSteps = totalSteps,
                                stageTitle = if (isRussian) "Шаг 1/$totalSteps: Распаковка .nsp → .nup" else "Step 1/$totalSteps: Unpacking .nsp → .nup",
                                detail = detail,
                                progress = frac
                            )
                        )
                    }
                    if (cancelled.get()) {
                        tmpNup.delete()
                        emit(InstallState(error = if (isRussian) "Установка отменена" else "Installation cancelled"))
                        return@Thread
                    }
                    if (rc != 0) {
                        tmpNup.delete()
                        throw IllegalStateException("writeNup failed: ${Native.lastError()}")
                    }
                    if (!tmpNup.renameTo(outNup)) {
                        throw IllegalStateException("Cannot rename ${tmpNup.name} to ${outNup.name}")
                    }
                }

                if (!fullPacker) {
                    emit(
                        InstallState(
                            running = false,
                            stepIndex = 1,
                            totalSteps = 1,
                            stageTitle = if (isRussian) ".nup успешно создан" else ".nup created successfully",
                            detail = if (isRussian)
                                "Файл .nup готов! Для полной сборки шейдеров на телефоне используйте Standalone-сборку из GitHub Actions."
                            else
                                ".nup is ready! For full on-device shader packing, use the Standalone APK build from GitHub Actions.",
                            progress = 1f,
                            completedFolder = outputDir.absolutePath
                        )
                    )
                    return@Thread
                }

                // ---- Full on-device packer steps via libnxpack.so ----
                val workDir = File(outputDir, "${GamePaths.TITLE_ID}-work").apply { mkdirs() }
                val packDir = File(workDir, "android-pack").apply { mkdirs() }
                val ondeviceWork = File(app.filesDir, "ondevice/${GamePaths.TITLE_ID}").apply { mkdirs() }
                val splitDir = File(ondeviceWork, "split")
                val splitScan = File(ondeviceWork, "split_scan")
                val resultsDir = File(ondeviceWork, "shader_results").apply { mkdirs() }
                val libDir = File(ondeviceWork, "lib").apply { mkdirs() }
                val logsDir = File(GamePaths.logsDir(app), "pack").apply { mkdirs() }

                val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
                val shaderWorkers = (cpuCount / 2).coerceIn(2, 4)

                val baseEnv = linkedMapOf(
                    "DM_GAME" to GamePaths.TITLE_ID,
                    "DM_REWRITE_J" to "1",
                    "NX_PACK_JOBS" to shaderWorkers.toString(),
                    "J" to "1",
                    "DM_SPLIT_RESUME" to splitScan.absolutePath,
                    "DM_SPLIT_SELECTED" to "0",
                    "DM_SPLIT_TARGETED" to "1",
                    "DM_SPLIT_DIRECT" to "1",
                    "DM_PACKER_COMMIT" to "lastlight-ondevice",
                    "NX_PACK_WORK" to ondeviceWork.absolutePath,
                    "NX_PACK_VERSION" to "0",
                    "TMPDIR" to app.cacheDir.absolutePath,
                    "DMSH_VK_RESULTS" to resultsDir.absolutePath,
                    "DMSH_VK_DELTA_FROM" to File(packDir, "shaders").absolutePath,
                    "XLATE" to "libdmsh_xlate.so",
                    "SPIRV_VAL" to "libSPIRV-Tools-shared.so",
                    "DMSH_VK_REQUIRE_ALL" to "1",
                    "DMSH_VK_ZSTD_LEVEL" to "6",
                    "DMSH_VK_XLATE_J" to shaderWorkers.toString(),
                    "DMSH_VK_PROCS" to "1",
                    "DMSH_VK_WORKERS" to "1"
                )

                fun runStep(stepIdx: Int, titleRu: String, titleEn: String, tool: String, argv: Array<String>, logName: String) {
                    if (cancelled.get()) throw InterruptedException("Cancelled")
                    val title = if (isRussian) "Шаг $stepIdx/$totalSteps: $titleRu" else "Step $stepIdx/$totalSteps: $titleEn"
                    val logFile = File(logsDir, logName).apply { writeText("") }
                    emit(InstallState(running = true, stepIndex = stepIdx, totalSteps = totalSteps, stageTitle = title, detail = "...", progress = 0.02f))

                    val stopTail = AtomicBoolean(false)
                    val tailer = Thread({
                        var offset = 0L
                        var lastStage = ""
                        while (!stopTail.get()) {
                            runCatching {
                                if (logFile.length() > offset) {
                                    RandomAccessFile(logFile, "r").use { raf ->
                                        raf.seek(offset)
                                        var line = raf.readLine()
                                        while (line != null) {
                                            val sm = stageRegex.matcher(line)
                                            if (sm.find()) {
                                                lastStage = sm.group(1) ?: lastStage
                                            }
                                            val pm = progressRegex.matcher(line)
                                            if (pm.find()) {
                                                val d = pm.group(1)?.toLongOrNull() ?: 0L
                                                val t = pm.group(2)?.toLongOrNull() ?: 1L
                                                val st = pm.group(3) ?: lastStage
                                                val frac = if (t > 0) (d.toFloat() / t.toFloat()).coerceIn(0f, 1f) else 0f
                                                emit(
                                                    InstallState(
                                                        running = true,
                                                        stepIndex = stepIdx,
                                                        totalSteps = totalSteps,
                                                        stageTitle = title,
                                                        detail = if (st.isNotBlank()) "$st ($d/$t)" else "$d / $t",
                                                        progress = frac
                                                    )
                                                )
                                            }
                                            line = raf.readLine()
                                        }
                                        offset = raf.filePointer
                                    }
                                }
                            }
                            try { Thread.sleep(300L) } catch (_: InterruptedException) { break }
                        }
                    }, "ll-pack-tail")
                    tailer.isDaemon = true
                    tailer.start()

                    val rc = try {
                        PackNative.runTool(app, tool, argv, baseEnv, logFile.absolutePath)
                    } finally {
                        stopTail.set(true)
                        tailer.interrupt()
                    }
                    if (cancelled.get() || rc == 130) throw InterruptedException("Cancelled")
                    if (rc != 0) {
                        val redLine = runCatching {
                            logFile.readLines().lastOrNull { it.contains("[RED]") }
                        }.getOrNull()
                        throw IllegalStateException(redLine ?: "$tool exited with code $rc (see ${logFile.name})")
                    }
                }

                // STEP 2: Rewrite ARM64 code (main + sdk -> android-pack)
                runStep(
                    stepIdx = 2,
                    titleRu = "Патчинг ARM64 модулей (main + sdk)",
                    titleEn = "Rewriting ARM64 modules (main + sdk)",
                    tool = "scripts/pack_android.py",
                    argv = arrayOf(
                        outNup.absolutePath, "--version", "0",
                        "--out", packDir.absolutePath,
                        "--code-only", "-j", "1", "--in-process"
                    ),
                    logName = "rewrite.log"
                )

                // STEP 3: Split 854 Metro shaders from romfs/shaders.nx
                splitDir.deleteRecursively()
                splitDir.mkdirs()
                runStep(
                    stepIdx = 3,
                    titleRu = "Извлечение шейдеров (shaders.nx)",
                    titleEn = "Extracting shaders (shaders.nx)",
                    tool = "dm/gfx/tools/shader_split.py",
                    argv = arrayOf(
                        "${outNup.absolutePath}:base/romfs",
                        "${outNup.absolutePath}:base/exefs/main",
                        File(splitDir, "pkg").absolutePath,
                        File(splitDir, "shader_map.tsv").absolutePath,
                        File(splitDir, "shader_facts.tsv").absolutePath,
                        File(splitDir, "shader_pairs.tsv").absolutePath,
                        File(splitDir, "pso_pairs.tsv").absolutePath
                    ),
                    logName = "split.log"
                )

                // STEP 4: Translate 854 shaders to Vulkan SPIR-V & finalize pack
                runStep(
                    stepIdx = 4,
                    titleRu = "Трансляция 854 шейдеров → Vulkan SPIR-V",
                    titleEn = "Translating 854 shaders → Vulkan SPIR-V",
                    tool = "dm/gfx/tools/vk_android_build.py",
                    argv = arrayOf(
                        "--target", "vulkan",
                        File(splitDir, "pkg").absolutePath,
                        libDir.absolutePath
                    ),
                    logName = "shaders.log"
                )

                runStep(
                    stepIdx = 4,
                    titleRu = "Финализация android-pack",
                    titleEn = "Finalizing android-pack",
                    tool = "scripts/pack_android.py",
                    argv = arrayOf(
                        outNup.absolutePath, "--version", "0",
                        "--out", packDir.absolutePath,
                        "--shader-cache", libDir.absolutePath,
                        "--keep-code", "-j", "1", "--in-process",
                        "--made-by", "LastLight Reborn"
                    ),
                    logName = "pack.log"
                )

                // Clean temporary split files
                splitDir.deleteRecursively()

                emit(
                    InstallState(
                        running = false,
                        stepIndex = totalSteps,
                        totalSteps = totalSteps,
                        stageTitle = if (isRussian) "Установка завершена!" else "Installation complete!",
                        detail = if (isRussian) "Metro: Last Light Redux готов к запуску" else "Metro: Last Light Redux is ready to play",
                        progress = 1f,
                        completedFolder = outputDir.absolutePath
                    )
                )
            } catch (_: InterruptedException) {
                emit(InstallState(error = if (isRussian) "Установка отменена" else "Installation cancelled"))
            } catch (t: Throwable) {
                emit(InstallState(error = t.message ?: t.toString()))
            }
        }, "ll-ondevice-install").start()
    }
}
