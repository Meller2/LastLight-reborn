package dev.lastlight.reborn.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lastlight.reborn.R
import dev.lastlight.reborn.data.DeviceHardwareInfo
import dev.lastlight.reborn.data.DeviceInfo
import dev.lastlight.reborn.data.GameFilesStatus
import dev.lastlight.reborn.data.GyroMode
import dev.lastlight.reborn.data.LauncherConfig
import dev.lastlight.reborn.data.PerfPreset
import dev.lastlight.reborn.ui.theme.MetroColors
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

private const val TAB_HOME = 0
private const val TAB_CONTROLS = 1
private const val TAB_GRAPHICS = 2
private const val TAB_FILES = 3
private const val TAB_ABOUT = 4

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
    val context = LocalContext.current
    val s = stringsFor(config.language)
    var selectedTab by remember { mutableIntStateOf(TAB_HOME) }

    val hwInfo = remember { DeviceInfo.readHardware(context) }
    var batteryTempC by remember { mutableStateOf(DeviceInfo.readBatteryTempC(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            batteryTempC = DeviceInfo.readBatteryTempC(context)
            delay(3000L)
        }
    }

    val coverBitmap = remember(filesStatus.iconFile?.absolutePath) {
        filesStatus.iconFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MetroColors.BgDeep)
    ) {
        // Atmospheric Metro backdrop behind entire right stage
        AtmosphericBackdrop(coverBitmap = coverBitmap)

        Row(modifier = Modifier.fillMaxSize()) {
            // Left slim glassmorphic navigation rail
            LeftNavigationRail(
                selectedTab = selectedTab,
                language = config.language,
                s = s,
                onSelectTab = { selectedTab = it },
                onToggleLang = {
                    val next = if (config.language == "ru") "en" else "ru"
                    onConfigChange(config.copy(language = next))
                }
            )

            // Main content stage with smooth animated tab transitions
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 28 })
                            .togetherWith(
                                fadeOut(tween(150)) +
                                    slideOutVertically(tween(150)) { -it / 40 }
                            )
                    },
                    label = "tabTransition"
                ) { tab ->
                    when (tab) {
                        TAB_HOME -> HomeDashboard(
                            config = config,
                            filesStatus = filesStatus,
                            hwInfo = hwInfo,
                            batteryTempC = batteryTempC,
                            coverBitmap = coverBitmap,
                            hasStoragePermission = hasStoragePermission,
                            saveFilesCount = saveFilesCount,
                            statusMessage = statusMessage,
                            s = s,
                            onConfigChange = onConfigChange,
                            onRequestStoragePermission = onRequestStoragePermission,
                            onPlayClick = onPlayClick,
                            onPickGameFolder = onPickGameFolder,
                            onAutoFindGameFolder = onAutoFindGameFolder,
                            onPickTurnipZip = onPickTurnipZip,
                            onExportSaves = onExportSaves,
                            onNavigateTab = { selectedTab = it }
                        )
                        TAB_CONTROLS -> ControlsTab(config, s, onConfigChange)
                        TAB_GRAPHICS -> GraphicsTab(config, filesStatus, s, onConfigChange)
                        TAB_FILES -> FilesTab(
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
                        TAB_ABOUT -> AboutTab(
                            config = config,
                            hwInfo = hwInfo,
                            filesStatus = filesStatus,
                            s = s
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AtmosphericBackdrop(coverBitmap: ImageBitmap?) {
    val infinite = rememberInfiniteTransition(label = "ambientMetro")
    val emberPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "embers"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.21f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Generated widescreen Metro hero key art (always crisp, zero external dependency)
        Image(
            painter = painterResource(id = R.drawable.metro_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.88f)
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Breathing warm tunnel amber glow behind the stalker & rails
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MetroColors.Amber.copy(alpha = glowPulse),
                        MetroColors.AmberDim.copy(alpha = glowPulse * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.72f, size.height * 0.34f),
                    radius = size.width * 0.45f
                )
            )
            // Left-to-right cinematic scrim blending sidebar into hero negative space
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to MetroColors.BgDeep.copy(alpha = 0.94f),
                    0.22f to MetroColors.BgDeep.copy(alpha = 0.78f),
                    0.50f to MetroColors.BgDeep.copy(alpha = 0.22f),
                    0.82f to MetroColors.BgDeep.copy(alpha = 0.26f),
                    1.0f to MetroColors.BgDeep.copy(alpha = 0.62f)
                )
            )
            // Bottom grounding vignette so bento cards stay razor sharp
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to MetroColors.BgDeep.copy(alpha = 0.28f),
                    0.46f to Color.Transparent,
                    0.78f to MetroColors.BgDeep.copy(alpha = 0.72f),
                    1.0f to MetroColors.BgDeep.copy(alpha = 0.95f)
                )
            )
            // Subtle drifting tunnel embers
            val seeds = floatArrayOf(0.28f, 0.39f, 0.48f, 0.57f, 0.66f, 0.74f, 0.83f, 0.91f)
            seeds.forEachIndexed { idx, sx ->
                val p = (emberPhase + idx * 0.137f) % 1f
                val x = size.width * (sx + 0.018f * sin((p * 6.283f + idx).toDouble()).toFloat())
                val y = size.height * (0.95f - p * 0.85f)
                val alpha = (sin((p * Math.PI)).toFloat() * 0.45f).coerceIn(0f, 0.45f)
                val r = if (idx % 3 == 0) 2.4f else 1.5f
                drawCircle(
                    color = MetroColors.AmberBright.copy(alpha = alpha),
                    radius = r,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun LeftNavigationRail(
    selectedTab: Int,
    language: String,
    s: UiStrings,
    onSelectTab: (Int) -> Unit,
    onToggleLang: () -> Unit
) {
    val items = listOf(
        Triple(TAB_HOME, s.tabHome, Icons.Rounded.Home),
        Triple(TAB_CONTROLS, s.tabControls, Icons.Rounded.SportsEsports),
        Triple(TAB_GRAPHICS, s.tabGraphics, Icons.Rounded.Tune),
        Triple(TAB_FILES, s.tabFiles, Icons.Rounded.Folder),
        Triple(TAB_ABOUT, s.tabAbout, Icons.Rounded.Info)
    )

    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(164.dp)
                .fillMaxHeight()
                .background(MetroColors.SidebarBg)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Brand logo mark: geometric golden Metro M + LastLight Reborn
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetroLogoMark(modifier = Modifier.size(30.dp))
                    Column {
                        Text(
                            text = "LastLight",
                            color = MetroColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = "Reborn",
                            color = MetroColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Navigation buttons with smooth animated active state
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEach { (id, label, icon) ->
                        val active = id == selectedTab
                        val bgTint by animateColorAsState(
                            targetValue = if (active) MetroColors.Amber.copy(alpha = 0.22f) else Color.Transparent,
                            animationSpec = tween(200),
                            label = "navBg"
                        )
                        val borderTint by animateColorAsState(
                            targetValue = if (active) MetroColors.BorderGlass else Color.Transparent,
                            animationSpec = tween(200),
                            label = "navBorder"
                        )
                        val iconTint by animateColorAsState(
                            targetValue = if (active) MetroColors.AmberBright else MetroColors.TextMuted,
                            animationSpec = tween(200),
                            label = "navIcon"
                        )
                        val textTint by animateColorAsState(
                            targetValue = if (active) MetroColors.TextPrimary else MetroColors.TextSecondary,
                            animationSpec = tween(200),
                            label = "navText"
                        )
                        val barHeight by animateDpAsState(
                            targetValue = if (active) 22.dp else 0.dp,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "navBar"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            bgTint,
                                            if (active) MetroColors.SurfaceElevated.copy(alpha = 0.72f) else Color.Transparent
                                        )
                                    )
                                )
                                .border(1.dp, borderTint, RoundedCornerShape(11.dp))
                                .clickable { onSelectTab(id) },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (barHeight > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(3.dp)
                                        .height(barHeight)
                                        .clip(CircleShape)
                                        .background(MetroColors.AmberBright)
                                )
                            }
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = label,
                                    color = textTint,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Bottom language toggle pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MetroColors.SurfaceCard)
                    .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleLang)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RU",
                    color = if (language == "ru") MetroColors.AmberBright else MetroColors.TextMuted,
                    fontWeight = if (language == "ru") FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp
                )
                Text("  ·  ", color = MetroColors.BorderSteel, fontSize = 11.sp)
                Text(
                    text = "EN",
                    color = if (language == "en") MetroColors.AmberBright else MetroColors.TextMuted,
                    fontWeight = if (language == "en") FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp
                )
            }
        }

        // Hairline vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MetroColors.BorderGlass)
        )
    }
}

@Composable
private fun MetroLogoMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.lastlight_emblem),
        contentDescription = "LastLight Reborn",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MetroColors.Amber.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    )
}

@Composable
private fun HomeDashboard(
    config: LauncherConfig,
    filesStatus: GameFilesStatus,
    hwInfo: DeviceHardwareInfo,
    batteryTempC: Float?,
    coverBitmap: ImageBitmap?,
    hasStoragePermission: Boolean,
    saveFilesCount: Int,
    statusMessage: String?,
    s: UiStrings,
    onConfigChange: (LauncherConfig) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onPlayClick: () -> Unit,
    onPickGameFolder: () -> Unit,
    onAutoFindGameFolder: () -> Unit,
    onPickTurnipZip: () -> Unit,
    onExportSaves: () -> Unit,
    onNavigateTab: (Int) -> Unit
) {
    val ready = hasStoragePermission && filesStatus.isReadyToPlay

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. TOP HERO ROW: Left = Metro Title + Status Pills + Launch CTA | Right = Device Info Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Iconic Metro: Last Light Redux hero wordmark
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "METRO",
                        color = MetroColors.TextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        letterSpacing = 1.5.sp,
                        lineHeight = 32.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "LAST LIGHT",
                            color = MetroColors.TextPrimary.copy(alpha = 0.90f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.8.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(MetroColors.AmberBright, MetroColors.Amber)
                                    )
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REDUX",
                                color = Color(0xFF140D04),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 2.2.sp
                            )
                        }
                    }
                }

                // Status & Platform pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Port readiness pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (ready) MetroColors.PhosphorGreen.copy(alpha = 0.14f)
                                else MetroColors.Amber.copy(alpha = 0.16f)
                            )
                            .border(
                                1.dp,
                                if (ready) MetroColors.PhosphorGreen.copy(alpha = 0.35f)
                                else MetroColors.Amber.copy(alpha = 0.45f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !ready) {
                                if (!hasStoragePermission) onRequestStoragePermission() else onNavigateTab(TAB_FILES)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (ready) MetroColors.PhosphorGreen else MetroColors.AmberBright,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = when {
                                !hasStoragePermission -> s.statusNeedStoragePerm
                                ready -> s.statusReadyShort
                                else -> s.statusMissingShort
                            },
                            color = if (ready) MetroColors.PhosphorGreen else MetroColors.AmberBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    HeroPill("v1.0.0")
                    HeroPill(s.appSubtitle)
                }

                Spacer(Modifier.height(2.dp))

                // Primary Launch CTA + Folder quick button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!hasStoragePermission) {
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(MetroColors.AmberBright, MetroColors.AmberDeep)
                                    )
                                )
                                .clickable(onClick = onRequestStoragePermission)
                                .padding(horizontal = 22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s.grantStoragePerm,
                                color = Color(0xFF140D04),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val playInteraction = remember { MutableInteractionSource() }
                        val playPressed by playInteraction.collectIsPressedAsState()
                        val playScale by animateFloatAsState(
                            targetValue = if (playPressed) 0.96f else 1f,
                            animationSpec = tween(110),
                            label = "playScale"
                        )
                        Box(
                            modifier = Modifier
                                .scale(playScale)
                                .height(44.dp)
                                .width(196.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (ready) Brush.horizontalGradient(
                                        listOf(MetroColors.AmberBright, MetroColors.AmberDeep)
                                    ) else Brush.horizontalGradient(
                                        listOf(MetroColors.SurfaceElevated, MetroColors.SurfaceElevated)
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (ready) Color(0x55FFE4A0) else MetroColors.BorderGlass,
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .clickable(
                                    enabled = ready,
                                    interactionSource = playInteraction,
                                    indication = null,
                                    onClick = onPlayClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = if (ready) Color(0xFF140D04) else MetroColors.TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (ready) s.play else s.notReady,
                                    color = if (ready) Color(0xFF140D04) else MetroColors.TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    // Translucent folder button beside Launch
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MetroColors.SurfaceCard)
                            .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(13.dp))
                            .clickable(onClick = onPickGameFolder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = s.pickFolder,
                            tint = MetroColors.TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage,
                        color = MetroColors.AmberBright,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right-hand real Device Info glass card
            DeviceInfoGlassCard(
                hwInfo = hwInfo,
                turnipName = if (config.useTurnip) filesStatus.turnipName else null,
                s = s,
                modifier = Modifier.width(236.dp)
            )
        }

        // 2. MIDDLE BENTO ROW: Performance | Temperature | Driver & Files
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Performance target & scale
            BentoGlassCard(
                modifier = Modifier
                    .weight(1.25f)
                    .height(78.dp)
                    .clickable { onNavigateTab(TAB_GRAPHICS) }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.BarChart,
                                contentDescription = null,
                                tint = MetroColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = s.perfCardTitle,
                                color = MetroColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${config.fpsCap} FPS",
                            color = MetroColors.PhosphorGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.2fx", config.resScale)} • ${config.upscaler.uppercase(Locale.US)}",
                            color = MetroColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    // Subtle flatline target pacing visual
                    Canvas(modifier = Modifier.width(62.dp).height(26.dp)) {
                        val y = size.height * 0.42f
                        val linePath = Path().apply {
                            moveTo(0f, y + 1f)
                            lineTo(size.width * 0.22f, y - 1f)
                            lineTo(size.width * 0.48f, y + 0.5f)
                            lineTo(size.width * 0.74f, y - 0.8f)
                            lineTo(size.width, y)
                        }
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            fillPath,
                            brush = Brush.verticalGradient(
                                listOf(MetroColors.PhosphorGreen.copy(alpha = 0.24f), Color.Transparent)
                            )
                        )
                        drawPath(
                            linePath,
                            color = MetroColors.PhosphorGreen,
                            style = Stroke(width = 1.8f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Card 2: Real live battery temperature
            BentoGlassCard(
                modifier = Modifier
                    .weight(0.85f)
                    .height(78.dp)
            ) {
                val tempColor = when {
                    batteryTempC == null -> MetroColors.TextSecondary
                    batteryTempC >= 43f -> MetroColors.DangerRed
                    batteryTempC >= 39f -> MetroColors.AmberBright
                    else -> MetroColors.PhosphorGreen
                }
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeviceThermostat,
                            contentDescription = null,
                            tint = MetroColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = s.tempCardTitle,
                            color = MetroColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = batteryTempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "—",
                        color = tempColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(1.dp))
                }
            }

            // Card 3: Driver & Files status
            BentoGlassCard(
                modifier = Modifier
                    .weight(1.15f)
                    .height(78.dp)
                    .clickable { onNavigateTab(TAB_FILES) }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = MetroColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = s.driverCardTitle,
                            color = MetroColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = if (config.useTurnip) (filesStatus.turnipName ?: s.driverTurnip) else s.driverSystem,
                        color = MetroColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FileCheckBadge("NUP", filesStatus.nupFile != null)
                        FileCheckBadge("Pack", filesStatus.patchedDir != null)
                        FileCheckBadge("Shaders", filesStatus.shaderLibFile != null)
                    }
                }
            }
        }

        // 3. BOTTOM BENTO ROW: Profiles quick switcher | Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Profiles card with direct 1-tap preset switcher
            Box(
                modifier = Modifier
                    .weight(1.35f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MetroColors.SurfaceCard)
                    .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(14.dp))
                    .clickable { onNavigateTab(TAB_GRAPHICS) }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Atmospheric thumbnail accent on left
                    Box(
                        modifier = Modifier
                            .width(74.dp)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(MetroColors.AmberDim.copy(alpha = 0.35f), MetroColors.SurfaceCard)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverBitmap != null) {
                            Image(
                                bitmap = coverBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().alpha(0.55f)
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MetroColors.AmberBright.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = s.profilesTitle,
                                color = MetroColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = when (config.preset) {
                                    PerfPreset.COOL -> s.presetCool
                                    PerfPreset.BALANCED -> s.presetBalanced
                                    PerfPreset.MAX -> s.presetMax
                                    PerfPreset.CUSTOM -> s.presetCustom
                                },
                                color = MetroColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            MiniPresetPill("30", config.preset == PerfPreset.COOL) {
                                onConfigChange(config.copy(preset = PerfPreset.COOL, fpsCap = 30, resScale = 0.75f, upscaler = "sgsr1"))
                            }
                            MiniPresetPill("45", config.preset == PerfPreset.BALANCED) {
                                onConfigChange(config.copy(preset = PerfPreset.BALANCED, fpsCap = 45, resScale = 0.85f, upscaler = "sgsr1"))
                            }
                            MiniPresetPill("60", config.preset == PerfPreset.MAX) {
                                onConfigChange(config.copy(preset = PerfPreset.MAX, fpsCap = 60, resScale = 1.0f, upscaler = "sgsr1"))
                            }
                        }
                    }
                }
            }

            // Quick Actions card (matching the 4-button reference strip)
            BentoGlassCard(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                paddingVertical = 8.dp,
                paddingHorizontal = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MetroColors.AmberBright,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = s.quickActionsTitle,
                            color = MetroColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickActionButton(Icons.Rounded.FolderOpen, s.pickFolder, onPickGameFolder)
                        QuickActionButton(Icons.Rounded.Search, s.autoFind, onAutoFindGameFolder)
                        QuickActionButton(Icons.Rounded.Memory, s.pickDriver, onPickTurnipZip)
                        QuickActionButton(Icons.Rounded.Save, s.exportSaves, onExportSaves)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoGlassCard(
    hwInfo: DeviceHardwareInfo,
    turnipName: String?,
    s: UiStrings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MetroColors.SurfaceCard)
            .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header: Device title + model
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = MetroColors.TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = s.deviceTitle,
                    color = MetroColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = hwInfo.deviceName,
                color = MetroColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MetroColors.BorderGlass)
        )

        DeviceSpecRow(Icons.Rounded.DeveloperBoard, hwInfo.socName)
        DeviceSpecRow(
            Icons.Rounded.Memory,
            if (!turnipName.isNullOrBlank()) "${hwInfo.gpuName} • Turnip" else hwInfo.gpuName
        )
        if (hwInfo.ramGb > 0) {
            DeviceSpecRow(Icons.Rounded.Storage, "${hwInfo.ramGb} GB RAM")
        }
        DeviceSpecRow(Icons.Rounded.Android, hwInfo.androidVersion)
    }
}

@Composable
private fun DeviceSpecRow(icon: ImageVector, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MetroColors.TextMuted,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = value,
            color = MetroColors.TextPrimary.copy(alpha = 0.90f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeroPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MetroColors.SurfaceCard)
            .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = MetroColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun MiniPresetPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MetroColors.Amber else MetroColors.SurfaceElevated, tween(180), label = "miniBg")
    val border by animateColorAsState(if (selected) MetroColors.AmberBright else MetroColors.BorderGlass, tween(180), label = "miniBorder")
    val text by animateColorAsState(if (selected) Color(0xFF140D04) else MetroColors.TextSecondary, tween(180), label = "miniTxt")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MetroColors.SurfaceElevated.copy(alpha = 0.9f))
            .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = MetroColors.TextPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun BentoGlassCard(
    modifier: Modifier = Modifier,
    paddingHorizontal: androidx.compose.ui.unit.Dp = 14.dp,
    paddingVertical: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MetroColors.SurfaceCard)
            .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(14.dp))
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical)
    ) {
        content()
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(s.presetTitle, color = MetroColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
private fun AboutTab(
    config: LauncherConfig,
    hwInfo: DeviceHardwareInfo,
    filesStatus: GameFilesStatus,
    s: UiStrings
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetroLogoMark(modifier = Modifier.size(34.dp))
                Column {
                    Text(s.aboutTitle, color = MetroColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(s.appSubtitle, color = MetroColors.AmberBright, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(s.aboutDesc, color = MetroColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            Text(s.aboutEngine, color = MetroColors.TextPrimary, fontSize = 12.sp)
            Text(s.aboutBuild, color = MetroColors.TextSecondary, fontSize = 12.sp)
        }

        GlassCard {
            Text(s.deviceTitle, color = MetroColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text("${hwInfo.deviceName} • ${hwInfo.socName} • ${hwInfo.gpuName}", color = MetroColors.TextPrimary, fontSize = 13.sp)
            Text(
                text = "${hwInfo.ramGb} GB RAM • ${hwInfo.androidVersion} • ${if (config.useTurnip) (filesStatus.turnipName ?: s.driverTurnip) else s.driverSystem}",
                color = MetroColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FileCheckBadge(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (ok) MetroColors.PhosphorGreen else MetroColors.DangerRed)
        )
        Text(label, color = if (ok) MetroColors.TextPrimary else MetroColors.TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MetroColors.SurfaceCard)
            .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(14.dp))
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
            .background(if (selected) MetroColors.Amber.copy(alpha = 0.16f) else MetroColors.SurfaceCard)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MetroColors.Amber else MetroColors.BorderGlass,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MetroColors.AmberBright else MetroColors.TextSecondary,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = title,
                color = if (selected) MetroColors.TextPrimary else MetroColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
        Text(desc, color = MetroColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SegmentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MetroColors.Amber else MetroColors.SurfaceElevated)
            .border(1.dp, if (selected) MetroColors.Amber else MetroColors.BorderGlass, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF140D04) else MetroColors.TextSecondary,
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
                checkedTrackColor = MetroColors.Amber
            )
        )
    }
}
