package dev.lastlight.reborn.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lastlight.reborn.data.GameFilesStatus
import dev.lastlight.reborn.data.GyroMode
import dev.lastlight.reborn.data.LauncherConfig
import dev.lastlight.reborn.data.PerfPreset
import dev.lastlight.reborn.ui.theme.MetroColors
import java.util.Locale

@Composable
fun LauncherScreen(
    config: LauncherConfig,
    filesStatus: GameFilesStatus,
    hasStoragePermission: Boolean,
    saveFilesCount: Int,
    statusMessage: String?,
    onConfigChange: (LauncherConfig) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onPlayClick: () -> Unit,
    onPickGameFolder: () -> Unit,
    onAutoFindGameFolder: () -> Unit,
    onPickTurnipZip: () -> Unit,
    onExportSaves: () -> Unit,
    onImportSaves: () -> Unit
) {
    val s = stringsFor(config.language)
    var selectedTab by remember { mutableIntStateOf(0) }

    val coverBitmap = remember(filesStatus.iconFile?.absolutePath) {
        filesStatus.iconFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MetroColors.BgDeep)
    ) {
        // Atmospheric backdrop: subtle dimmed art + warm tunnel glow
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.18f)
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(MetroColors.Amber.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.25f),
                    radius = size.width * 0.65f
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MetroColors.BgDeep.copy(alpha = 0.72f),
                        MetroColors.BgDeep.copy(alpha = 0.52f),
                        MetroColors.BgDeep.copy(alpha = 0.94f)
                    )
                )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Sleek AAA top navigation bar
            TopNavBar(
                selectedTab = selectedTab,
                language = config.language,
                s = s,
                onSelectTab = { selectedTab = it },
                onToggleLang = {
                    val next = if (config.language == "ru") "en" else "ru"
                    onConfigChange(config.copy(language = next))
                }
            )

            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 18.dp)
            ) {
                when (selectedTab) {
                    0 -> HomeTab(
                        config = config,
                        filesStatus = filesStatus,
                        coverBitmap = coverBitmap,
                        hasStoragePermission = hasStoragePermission,
                        statusMessage = statusMessage,
                        s = s,
                        onRequestStoragePermission = onRequestStoragePermission,
                        onPlayClick = onPlayClick,
                        onOpenFilesTab = { selectedTab = 3 }
                    )
                    1 -> GraphicsTab(config, filesStatus, s, onConfigChange)
                    2 -> ControlsTab(config, s, onConfigChange)
                    3 -> FilesTab(
                        config = config,
                        filesStatus = filesStatus,
                        saveFilesCount = saveFilesCount,
                        statusMessage = statusMessage,
                        s = s,
                        onPickGameFolder = onPickGameFolder,
                        onAutoFindGameFolder = onAutoFindGameFolder,
                        onPickTurnipZip = onPickTurnipZip,
                        onExportSaves = onExportSaves,
                        onImportSaves = onImportSaves
                    )
                }
            }
        }
    }
}

@Composable
private fun TopNavBar(
    selectedTab: Int,
    language: String,
    s: UiStrings,
    onSelectTab: (Int) -> Unit,
    onToggleLang: () -> Unit
) {
    val tabs = listOf(s.tabHome, s.tabGraphics, s.tabControls, s.tabFiles)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MetroColors.BgDeep.copy(alpha = 0.70f))
            .padding(horizontal = 32.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand mark
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MetroColors.Amber.copy(alpha = 0.16f))
                    .border(1.5.dp, MetroColors.Amber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = MetroColors.AmberBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(
                text = "LastLight Reborn",
                color = MetroColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        // Center tabs with subtle amber underline
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { idx, title ->
                val active = idx == selectedTab
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectTab(idx) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = if (active) MetroColors.TextPrimary else MetroColors.TextMuted,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(if (active) 24.dp else 0.dp)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(MetroColors.Amber)
                    )
                }
            }
        }

        // Language pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MetroColors.SurfaceGlass)
                .border(1.dp, MetroColors.BorderSubtle, RoundedCornerShape(20.dp))
                .clickable(onClick = onToggleLang)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RU",
                color = if (language == "ru") MetroColors.AmberBright else MetroColors.TextMuted,
                fontWeight = if (language == "ru") FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
            Text("·", color = MetroColors.BorderSteel, fontSize = 12.sp)
            Text(
                text = "EN",
                color = if (language == "en") MetroColors.AmberBright else MetroColors.TextMuted,
                fontWeight = if (language == "en") FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun HomeTab(
    config: LauncherConfig,
    filesStatus: GameFilesStatus,
    coverBitmap: ImageBitmap?,
    hasStoragePermission: Boolean,
    statusMessage: String?,
    s: UiStrings,
    onRequestStoragePermission: () -> Unit,
    onPlayClick: () -> Unit,
    onOpenFilesTab: () -> Unit
) {
    val ready = hasStoragePermission && filesStatus.isReadyToPlay

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: sleek cover art card
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MetroColors.SurfaceGlass)
                .border(1.dp, MetroColors.BorderSteel.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("M", color = MetroColors.Amber, fontWeight = FontWeight.Bold, fontSize = 64.sp)
            }
        }

        // Right: title, status, active config pills, and Play CTA
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = s.appTitle,
                color = MetroColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = s.appSubtitle,
                color = MetroColors.TextSecondary,
                fontSize = 13.sp
            )

            // Status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MetroColors.SurfaceGlass)
                    .border(
                        1.dp,
                        if (ready) MetroColors.PhosphorGreen.copy(alpha = 0.35f) else MetroColors.Amber.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = hasStoragePermission && !ready) { onOpenFilesTab() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = if (ready) MetroColors.PhosphorGreen else MetroColors.Amber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when {
                        !hasStoragePermission -> s.statusNeedStoragePerm
                        ready -> s.statusReady
                        else -> s.statusMissingFiles
                    },
                    color = if (ready) MetroColors.TextPrimary else MetroColors.AmberBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Active settings chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTag("${config.fpsCap} FPS")
                InfoTag(String.format(Locale.US, "%.2fx", config.resScale))
                InfoTag(config.upscaler.uppercase(Locale.US))
                InfoTag(if (config.useTurnip) (filesStatus.turnipName ?: "Turnip") else s.driverSystem)
            }

            if (!statusMessage.isNullOrBlank()) {
                Text(statusMessage, color = MetroColors.AmberBright, fontSize = 12.sp)
            }

            Spacer(Modifier.height(4.dp))

            // Primary Play button
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!hasStoragePermission) {
                    Button(
                        onClick = onRequestStoragePermission,
                        colors = ButtonDefaults.buttonColors(containerColor = MetroColors.Amber),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text(s.grantStoragePerm, color = MetroColors.BgDeep, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .height(52.dp)
                            .width(210.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (ready) Brush.horizontalGradient(listOf(MetroColors.AmberBright, MetroColors.Amber))
                                else Brush.horizontalGradient(listOf(MetroColors.SurfaceElevated, MetroColors.SurfaceElevated))
                            )
                            .clickable(enabled = ready, onClick = onPlayClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = if (ready) MetroColors.BgDeep else MetroColors.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (ready) s.play else s.notReady,
                                color = if (ready) MetroColors.BgDeep else MetroColors.TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MetroColors.SurfaceElevated.copy(alpha = 0.85f))
            .border(1.dp, MetroColors.BorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = MetroColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GraphicsTab(
    config: LauncherConfig,
    filesStatus: GameFilesStatus,
    s: UiStrings,
    onConfigChange: (LauncherConfig) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(s.presetTitle, color = MetroColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetCard(s.presetCool, s.presetCoolDesc, Icons.Rounded.AcUnit, config.preset == PerfPreset.COOL, Modifier.weight(1f)) {
                onConfigChange(config.copy(preset = PerfPreset.COOL, fpsCap = 30, resScale = 0.75f, upscaler = "sgsr1"))
            }
            PresetCard(s.presetBalanced, s.presetBalancedDesc, Icons.Rounded.Balance, config.preset == PerfPreset.BALANCED, Modifier.weight(1f)) {
                onConfigChange(config.copy(preset = PerfPreset.BALANCED, fpsCap = 45, resScale = 0.85f, upscaler = "sgsr1"))
            }
            PresetCard(s.presetMax, s.presetMaxDesc, Icons.Rounded.LocalFireDepartment, config.preset == PerfPreset.MAX, Modifier.weight(1f)) {
                onConfigChange(config.copy(preset = PerfPreset.MAX, fpsCap = 60, resScale = 1.0f, upscaler = "sgsr1"))
            }
        }

        GlassCard {
            SettingSlider(
                label = "${s.resScale}: ${String.format(Locale.US, "%.2fx", config.resScale)}",
                value = config.resScale,
                range = 0.50f..1.00f
            ) { v ->
                val snapped = (Math.round(v * 20f) / 20f).coerceIn(0.5f, 1.0f)
                onConfigChange(config.copy(preset = PerfPreset.CUSTOM, resScale = snapped))
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.fpsLimit, color = MetroColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentChip("30 FPS", config.fpsCap == 30) { onConfigChange(config.copy(preset = PerfPreset.CUSTOM, fpsCap = 30)) }
                    SegmentChip("45 FPS", config.fpsCap == 45) { onConfigChange(config.copy(preset = PerfPreset.CUSTOM, fpsCap = 45)) }
                    SegmentChip("60 FPS", config.fpsCap == 60) { onConfigChange(config.copy(preset = PerfPreset.CUSTOM, fpsCap = 60)) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.gpuDriver, color = MetroColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val turnipLabel = filesStatus.turnipName ?: s.driverTurnip
                        SegmentChip(turnipLabel, config.useTurnip) { onConfigChange(config.copy(useTurnip = true)) }
                        SegmentChip(s.driverSystem, !config.useTurnip) { onConfigChange(config.copy(useTurnip = false)) }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.upscaler, color = MetroColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentChip("SGSR1", config.upscaler == "sgsr1") { onConfigChange(config.copy(upscaler = "sgsr1")) }
                        SegmentChip("FSR1", config.upscaler == "fsr1") { onConfigChange(config.copy(upscaler = "fsr1")) }
                        SegmentChip("Off", config.upscaler == "off") { onConfigChange(config.copy(upscaler = "off")) }
                    }
                }
            }
        }

        GlassCard {
            SettingSwitch(s.perfOverlay, config.showPerfOverlay) {
                onConfigChange(config.copy(showPerfOverlay = it))
            }
            Spacer(Modifier.height(6.dp))
            SettingSwitch(s.fillScreen, config.fillScreen) {
                onConfigChange(config.copy(fillScreen = it))
            }
        }
    }
}

@Composable
private fun ControlsTab(config: LauncherConfig, s: UiStrings, onConfigChange: (LauncherConfig) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassCard {
            SettingSwitch(s.touchHud, config.touchHudEnabled) {
                onConfigChange(config.copy(touchHudEnabled = it))
            }
            Spacer(Modifier.height(6.dp))
            SettingSwitch(s.adsToggle, config.adsToggle) {
                onConfigChange(config.copy(adsToggle = it))
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.gyroMode, color = MetroColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentChip(s.gyroOff, config.gyroMode == GyroMode.OFF) { onConfigChange(config.copy(gyroMode = GyroMode.OFF)) }
                    SegmentChip(s.gyroAds, config.gyroMode == GyroMode.ADS) { onConfigChange(config.copy(gyroMode = GyroMode.ADS)) }
                    SegmentChip(s.gyroAlways, config.gyroMode == GyroMode.ALWAYS) { onConfigChange(config.copy(gyroMode = GyroMode.ALWAYS)) }
                }
            }
        }

        GlassCard {
            SettingSlider(
                label = "${s.gyroSens}: ${String.format(Locale.US, "%.2f", config.gyroSensitivity)}",
                value = config.gyroSensitivity,
                range = 0.2f..3.0f
            ) { onConfigChange(config.copy(gyroSensitivity = it)) }

            SettingSlider(
                label = "${s.lookSens}: ${String.format(Locale.US, "%.2f", config.touchLookSensitivity)}",
                value = config.touchLookSensitivity,
                range = 0.4f..3.0f
            ) { onConfigChange(config.copy(touchLookSensitivity = it)) }

            SettingSlider(
                label = "${s.adsSens}: ${String.format(Locale.US, "%.2f", config.touchAdsSensitivity)}",
                value = config.touchAdsSensitivity,
                range = 0.2f..2.0f
            ) { onConfigChange(config.copy(touchAdsSensitivity = it)) }

            SettingSlider(
                label = "${s.deadzoneComp}: ${String.format(Locale.US, "%.0f%%", config.touchDeadzoneComp * 100)}",
                value = config.touchDeadzoneComp,
                range = 0.0f..0.35f
            ) { onConfigChange(config.copy(touchDeadzoneComp = it)) }

            SettingSlider(
                label = "${s.hudOpacity}: ${String.format(Locale.US, "%.0f%%", config.hudOpacity * 100)}",
                value = config.hudOpacity,
                range = 0.25f..1.0f
            ) { onConfigChange(config.copy(hudOpacity = it)) }
        }
    }
}

@Composable
private fun FilesTab(
    config: LauncherConfig,
    filesStatus: GameFilesStatus,
    saveFilesCount: Int,
    statusMessage: String?,
    s: UiStrings,
    onPickGameFolder: () -> Unit,
    onAutoFindGameFolder: () -> Unit,
    onPickTurnipZip: () -> Unit,
    onExportSaves: () -> Unit,
    onImportSaves: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Game folder card with native system picker
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MetroColors.Amber, modifier = Modifier.size(18.dp))
                        Text(s.gameFolder, color = MetroColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text(
                        text = config.gameFolder.ifBlank { "—" },
                        color = MetroColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FileCheckBadge("NUP", filesStatus.nupFile != null)
                        FileCheckBadge("Pack", filesStatus.patchedDir != null)
                        FileCheckBadge("Shaders", filesStatus.shaderLibFile != null)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAutoFindGameFolder, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MetroColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.autoFind, color = MetroColors.TextPrimary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onPickGameFolder,
                        colors = ButtonDefaults.buttonColors(containerColor = MetroColors.Amber),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(s.pickFolder, color = MetroColors.BgDeep, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Turnip driver card with native system picker
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = MetroColors.Amber, modifier = Modifier.size(18.dp))
                        Text(s.turnipFile, color = MetroColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text(
                        text = filesStatus.turnipName ?: s.driverNotSelected,
                        color = if (filesStatus.turnipReady) MetroColors.PhosphorGreen else MetroColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = onPickTurnipZip,
                    colors = ButtonDefaults.buttonColors(containerColor = MetroColors.SurfaceElevated),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(s.pickDriver, color = MetroColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        }

        // Saves backup card
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Save, contentDescription = null, tint = MetroColors.Amber, modifier = Modifier.size(18.dp))
                        Text(s.savesTitle, color = MetroColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text(s.savesCount(saveFilesCount), color = MetroColors.TextSecondary, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportSaves, shape = RoundedCornerShape(10.dp)) {
                        Text(s.exportSaves, color = MetroColors.TextPrimary, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onImportSaves, shape = RoundedCornerShape(10.dp)) {
                        Text(s.importSaves, color = MetroColors.TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(statusMessage, color = MetroColors.PhosphorGreen, fontSize = 12.sp)
        }
        Text(s.onDeviceNote, color = MetroColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun FileCheckBadge(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (ok) MetroColors.PhosphorGreen else MetroColors.DangerRed)
        )
        Text(label, color = if (ok) MetroColors.TextPrimary else MetroColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MetroColors.SurfaceGlass)
            .border(1.dp, MetroColors.BorderSubtle, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun PresetCard(
    title: String,
    desc: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MetroColors.Amber.copy(alpha = 0.14f) else MetroColors.SurfaceGlass)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MetroColors.Amber else MetroColors.BorderSubtle,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MetroColors.AmberBright else MetroColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = if (selected) MetroColors.TextPrimary else MetroColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
        Text(desc, color = MetroColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SegmentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MetroColors.Amber else MetroColors.SurfaceElevated)
            .border(1.dp, if (selected) MetroColors.Amber else MetroColors.BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) MetroColors.BgDeep else MetroColors.TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = MetroColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MetroColors.AmberBright,
                activeTrackColor = MetroColors.Amber,
                inactiveTrackColor = MetroColors.BorderSubtle
            )
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MetroColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MetroColors.BgDeep,
                checkedTrackColor = MetroColors.Amber,
                uncheckedTrackColor = MetroColors.SurfaceElevated
            )
        )
    }
}
