package dev.lastlight.reborn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.lastlight.reborn.data.AppPrefs
import dev.lastlight.reborn.data.GamePaths
import dev.lastlight.reborn.data.InstallState
import dev.lastlight.reborn.data.LauncherConfig
import dev.lastlight.reborn.data.OnDeviceInstaller
import dev.lastlight.reborn.data.SaveBackupManager
import dev.lastlight.reborn.ui.LauncherScreen
import dev.lastlight.reborn.ui.theme.MetroTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var prefs: AppPrefs
    private var config by mutableStateOf(LauncherConfig())
    private var hasStoragePerm by mutableStateOf(false)
    private var saveCount by mutableIntStateOf(0)
    private var statusMsg by mutableStateOf<String?>(null)
    private var refreshTick by mutableIntStateOf(0)

    // Phase 3 on-device installer state
    private var selectedNspPath by mutableStateOf<String?>(null)
    private var hasProdKeys by mutableStateOf(false)
    private var installState by mutableStateOf(InstallState())

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val resolved = GamePaths.resolveTreeUriToPath(uri)
            if (resolved != null) {
                config = config.copy(gameFolder = resolved)
                prefs.save(config)
                statusMsg = if (config.language == "ru") "Папка выбрана: $resolved" else "Folder selected: $resolved"
                refreshTick++
            } else {
                statusMsg = if (config.language == "ru") "Не удалось определить путь к папке" else "Could not resolve folder path"
            }
        }
    }

    private val nspPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolved = OnDeviceInstaller.resolveDocumentUriToPath(uri)
            if (resolved != null && File(resolved).isFile) {
                selectedNspPath = resolved
                statusMsg = if (config.language == "ru") "Выбран образ: ${File(resolved).name}" else "Selected dump: ${File(resolved).name}"
            } else {
                statusMsg = if (config.language == "ru") "Выберите .nsp/.xci/.nsz из памяти телефона (Downloads)" else "Pick .nsp/.xci/.nsz from internal storage (Downloads)"
            }
        }
    }

    private val keysPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val imported = OnDeviceInstaller.importKeysFile(this, uri)
            if (imported != null) {
                hasProdKeys = true
                statusMsg = if (config.language == "ru") "Файл prod.keys импортирован" else "prod.keys imported"
            } else {
                statusMsg = if (config.language == "ru") "Не удалось прочитать prod.keys" else "Could not read prod.keys"
            }
        }
    }

    private val turnipPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryFileName(uri) ?: "Turnip"
            runCatching {
                contentResolver.openInputStream(uri)?.use { GamePaths.importTurnipFromStream(this, it, name) }
            }.onSuccess { so ->
                if (so != null) {
                    config = config.copy(useTurnip = true)
                    prefs.save(config)
                    statusMsg = if (config.language == "ru") "Драйвер Turnip установлен" else "Turnip driver installed"
                    refreshTick++
                } else {
                    statusMsg = if (config.language == "ru") "В архиве не найден .so драйвер" else "No .so driver found in archive"
                }
            }.onFailure { e ->
                statusMsg = "Driver error: ${e.message}"
            }
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { SaveBackupManager.exportToZip(this, it) } ?: 0
            }.onSuccess { n ->
                statusMsg = if (config.language == "ru") "Экспортировано файлов: $n" else "Exported files: $n"
            }.onFailure { e ->
                statusMsg = "Export error: ${e.message}"
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { SaveBackupManager.importFromZip(this, it) } ?: 0
            }.onSuccess { n ->
                saveCount = SaveBackupManager.countSaveFiles(this)
                statusMsg = if (config.language == "ru") "Импортировано файлов: $n" else "Imported files: $n"
            }.onFailure { e ->
                statusMsg = "Import error: ${e.message}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        prefs = AppPrefs(this)
        config = prefs.load()
        refreshState()

        setContent {
            MetroTheme {
                @Suppress("UNUSED_VARIABLE")
                val tick = refreshTick
                val filesStatus = GamePaths.inspect(this, config)
                LauncherScreen(
                    config = config,
                    filesStatus = filesStatus,
                    hasStoragePermission = hasStoragePerm,
                    saveFilesCount = saveCount,
                    statusMessage = statusMsg,
                    selectedNspPath = selectedNspPath,
                    hasProdKeys = hasProdKeys,
                    hasFullPacker = OnDeviceInstaller.hasFullPacker(this),
                    installState = installState,
                    onConfigChange = { updated ->
                        config = updated
                        prefs.save(updated)
                    },
                    onRequestStoragePermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    },
                    onPlayClick = {
                        prefs.save(config)
                        startActivity(Intent(this, GameActivity::class.java))
                    },
                    onPickGameFolder = {
                        folderPicker.launch(null)
                    },
                    onAutoFindGameFolder = {
                        val found = GamePaths.autoDetectGameFolder()
                        if (found != null) {
                            config = config.copy(gameFolder = found)
                            prefs.save(config)
                            statusMsg = if (config.language == "ru") "Найдено: $found" else "Found: $found"
                            refreshTick++
                        } else {
                            statusMsg = if (config.language == "ru") "Файлы Metro не найдены автоматически" else "Metro files not found automatically"
                        }
                    },
                    onPickTurnipZip = {
                        turnipPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    onExportSaves = {
                        exportLauncher.launch("LastLightReborn-saves.zip")
                    },
                    onImportSaves = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    onPickNsp = {
                        nspPicker.launch(arrayOf("*/*"))
                    },
                    onPickKeys = {
                        keysPicker.launch(arrayOf("*/*"))
                    },
                    onStartInstall = {
                        val nsp = selectedNspPath
                        val keys = OnDeviceInstaller.savedKeysFile(this)
                        if (nsp != null && keys != null) {
                            val outDir = File(
                                config.gameFolder.ifBlank { "/sdcard/Download/LastLightReborn/Metro Last Light Redux" }
                            )
                            OnDeviceInstaller.startInstall(
                                context = this,
                                nspPath = nsp,
                                keysFile = keys,
                                outputDir = outDir,
                                isRussian = config.language == "ru"
                            ) { state ->
                                runOnUiThread {
                                    installState = state
                                    val doneFolder = state.completedFolder
                                    if (!state.running && doneFolder != null) {
                                        config = config.copy(gameFolder = doneFolder)
                                        prefs.save(config)
                                        refreshTick++
                                    }
                                }
                            }
                        }
                    },
                    onCancelInstall = {
                        OnDeviceInstaller.cancel()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        refreshState()
    }

    private fun refreshState() {
        config = prefs.load()
        hasStoragePerm = Environment.isExternalStorageManager()
        saveCount = SaveBackupManager.countSaveFiles(this)
        hasProdKeys = OnDeviceInstaller.savedKeysFile(this) != null
        refreshTick++
    }

    private fun queryFileName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
