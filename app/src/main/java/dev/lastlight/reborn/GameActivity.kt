package dev.lastlight.reborn

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.lastlight.reborn.data.AppPrefs
import dev.lastlight.reborn.data.GamePaths
import dev.lastlight.reborn.data.GyroMode
import dev.lastlight.reborn.data.LauncherConfig
import dev.lastlight.reborn.nativecore.NativeBridge
import dev.lastlight.reborn.ui.UiStrings
import dev.lastlight.reborn.ui.hud.PerfOverlay
import dev.lastlight.reborn.ui.hud.ShooterHudOverlay
import dev.lastlight.reborn.ui.stringsFor
import dev.lastlight.reborn.ui.theme.MetroColors
import dev.lastlight.reborn.ui.theme.MetroTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameActivity : ComponentActivity(), SurfaceHolder.Callback {
    private lateinit var prefs: AppPrefs
    private var config by mutableStateOf(LauncherConfig())
    private var menuOpen by mutableStateOf(false)
    private var gamepadActive by mutableStateOf(false)
    private var hostStarted = false

    private fun setPauseMenu(open: Boolean) {
        if (menuOpen == open) return
        menuOpen = open
        if (hostStarted) {
            NativeBridge.lifecycle(if (open) 4 else 5)
        }
        if (!open) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        prefs = AppPrefs(this)
        config = prefs.load()
        val files = GamePaths.inspect(this, config)
        if (!files.isReadyToPlay) {
            finish()
            return
        }

        NativeBridge.ensureLoaded()
        // Disable built-in peanuts touch controls: our Compose ShooterHudOverlay drives a virtual pad directly
        // labelled = 1 maps KEYCODE_BUTTON_A/B/X/Y 1:1 to Nintendo Switch A/B/X/Y labels
        NativeBridge.touchControlsEnable(false)
        NativeBridge.inputDeviceAdded(NativeBridge.VIRTUAL_PAD_DEVICE_ID, "LastLight Touch", 1)
        // Zero host deadzone while virtual touch controls drive the sticks (ShooterHudOverlay handles radial DZ cleanly)
        NativeBridge.inputDeadzone(0.0f)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setPauseMenu(!menuOpen)
            }
        })

        val root = FrameLayout(this)
        val surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@GameActivity)
        }
        root.addView(surfaceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val composeOverlay = ComposeView(this).apply {
            setContent {
                MetroTheme {
                    val s = stringsFor(config.language)
                    Box(modifier = Modifier.fillMaxSize()) {
                        ShooterHudOverlay(
                            config = config,
                            strings = s,
                            visible = config.touchHudEnabled && !gamepadActive && !menuOpen,
                            onOpenMenu = { setPauseMenu(true) }
                        )

                        if (config.showPerfOverlay) {
                            PerfOverlay(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 12.dp, top = 10.dp)
                            )
                        }

                        if (menuOpen) {
                            InGamePauseOverlay(
                                config = config,
                                s = s,
                                onUpdateConfig = { updated ->
                                    config = updated
                                    prefs.save(updated)
                                },
                                onResume = { setPauseMenu(false) },
                                onExit = {
                                    finish()
                                    NativeBridge.terminateProcess()
                                }
                            )
                        }
                    }
                }
            }
        }
        root.addView(composeOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        val env = GamePaths.buildHostEnv(this, config, files)
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val logFile = File(GamePaths.logsDir(this), "lastlight-$ts.log")
        hostStarted = NativeBridge.startHost(files.patchedDir!!.absolutePath, env, logFile.absolutePath)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        NativeBridge.setWindow(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        NativeBridge.setWindow(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        NativeBridge.setWindow(null)
    }

    override fun onPause() {
        super.onPause()
        if (hostStarted) NativeBridge.lifecycle(0)
    }

    override fun onStop() {
        super.onStop()
        if (hostStarted) NativeBridge.lifecycle(1)
    }

    override fun onStart() {
        super.onStart()
        if (hostStarted) NativeBridge.lifecycle(2)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (hostStarted) NativeBridge.lifecycle(3)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && hostStarted) {
            NativeBridge.terminateProcess()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val src = event.source
        if ((src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        ) {
            if (!gamepadActive) {
                gamepadActive = true
                NativeBridge.inputDeadzone(0.08f)
            }
            val down = event.action == KeyEvent.ACTION_DOWN
            if (NativeBridge.inputKey(event.deviceId, event.keyCode, down)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val src = event.source
        if ((src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        ) {
            if (!gamepadActive) {
                gamepadActive = true
                NativeBridge.inputDeadzone(0.08f)
            }
            val axes = intArrayOf(
                MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
            )
            val vals = FloatArray(axes.size) { i -> event.getAxisValue(axes[i]) }
            if (NativeBridge.inputAxes(event.deviceId, axes, vals, axes.size)) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (gamepadActive && ev.actionMasked == MotionEvent.ACTION_DOWN) {
            gamepadActive = false
            NativeBridge.inputDeadzone(0.0f)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun InGamePauseOverlay(
    config: LauncherConfig,
    s: UiStrings,
    onUpdateConfig: (LauncherConfig) -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit
) {
    // Clicking anywhere on the dimmed backdrop closes the pause menu
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xC807090C))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onResume
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .heightIn(max = 330.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MetroColors.SurfaceDark.copy(alpha = 0.96f))
                .border(1.dp, MetroColors.BorderGlass, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // absorb clicks inside card
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with explicit Close (X) button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MetroColors.Amber)
                    )
                    Text(
                        text = "LastLight Reborn",
                        color = MetroColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MetroColors.SurfaceElevated)
                        .border(1.dp, MetroColors.BorderSubtle, CircleShape)
                        .clickable(onClick = onResume),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = s.resumeGame,
                        tint = MetroColors.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Two-column compact scrollable body (guaranteed never to clip Resume/Exit)
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Left column: sensitivities
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactPauseSlider(
                        label = "${s.lookSens}: ${String.format(Locale.US, "%.2f", config.touchLookSensitivity)}",
                        value = config.touchLookSensitivity,
                        range = 0.4f..3.0f
                    ) { onUpdateConfig(config.copy(touchLookSensitivity = it)) }

                    CompactPauseSlider(
                        label = "${s.adsSens}: ${String.format(Locale.US, "%.2f", config.touchAdsSensitivity)}",
                        value = config.touchAdsSensitivity,
                        range = 0.2f..2.0f
                    ) { onUpdateConfig(config.copy(touchAdsSensitivity = it)) }

                    CompactPauseSlider(
                        label = "${s.gyroSens}: ${String.format(Locale.US, "%.2f", config.gyroSensitivity)}",
                        value = config.gyroSensitivity,
                        range = 0.2f..3.0f
                    ) { onUpdateConfig(config.copy(gyroSensitivity = it)) }
                }

                // Right column: gyro mode + ADS toggle + opacity
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(s.gyroMode, color = MetroColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PauseChip(s.gyroOff, config.gyroMode == GyroMode.OFF) {
                            onUpdateConfig(config.copy(gyroMode = GyroMode.OFF))
                        }
                        PauseChip(s.gyroAdsShort, config.gyroMode == GyroMode.ADS) {
                            onUpdateConfig(config.copy(gyroMode = GyroMode.ADS))
                        }
                        PauseChip(s.gyroAlways, config.gyroMode == GyroMode.ALWAYS) {
                            onUpdateConfig(config.copy(gyroMode = GyroMode.ALWAYS))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.adsToggle,
                            color = MetroColors.TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Switch(
                            checked = config.adsToggle,
                            onCheckedChange = { onUpdateConfig(config.copy(adsToggle = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MetroColors.BgDeep,
                                checkedTrackColor = MetroColors.Amber
                            )
                        )
                    }

                    CompactPauseSlider(
                        label = "${s.hudOpacity}: ${String.format(Locale.US, "%.0f%%", config.hudOpacity * 100)}",
                        value = config.hudOpacity,
                        range = 0.25f..1.0f
                    ) { onUpdateConfig(config.copy(hudOpacity = it)) }
                }
            }

            // Pinned bottom action bar — always visible and tappable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = MetroColors.Amber),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(
                        text = s.resumeGame,
                        color = MetroColors.BgDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(
                        text = s.exitToLauncher,
                        color = MetroColors.DangerRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPauseSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, color = MetroColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = MetroColors.AmberBright,
                activeTrackColor = MetroColors.Amber,
                inactiveTrackColor = MetroColors.BorderSubtle
            )
        )
    }
}

@Composable
private fun PauseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MetroColors.Amber else MetroColors.SurfaceElevated)
            .border(1.dp, if (selected) MetroColors.Amber else MetroColors.BorderSubtle, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) MetroColors.BgDeep else MetroColors.TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}
