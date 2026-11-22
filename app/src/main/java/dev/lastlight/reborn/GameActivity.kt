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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.lastlight.reborn.data.AppPrefs
import dev.lastlight.reborn.data.GamePaths
import dev.lastlight.reborn.data.LauncherConfig
import dev.lastlight.reborn.nativecore.NativeBridge
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
        NativeBridge.inputDeadzone(0.08f)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                menuOpen = !menuOpen
                NativeBridge.lifecycle(if (menuOpen) 4 else 5)
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
                            onOpenMenu = {
                                menuOpen = true
                                NativeBridge.lifecycle(4) // pause
                            }
                        )

                        if (config.showPerfOverlay) {
                            PerfOverlay(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 12.dp, top = 10.dp)
                            )
                        }

                        if (menuOpen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xBB080A0C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(360.dp)
                                        .background(MetroColors.SurfaceDark, RoundedCornerShape(10.dp))
                                        .border(1.dp, MetroColors.Amber, RoundedCornerShape(10.dp))
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "LAST LIGHT REBORN",
                                        color = MetroColors.Amber,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${s.lookSens}: ${String.format(Locale.US, "%.2f", config.touchLookSensitivity)}",
                                        color = MetroColors.TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                    Slider(
                                        value = config.touchLookSensitivity,
                                        onValueChange = {
                                            config = config.copy(touchLookSensitivity = it)
                                            prefs.save(config)
                                        },
                                        valueRange = 0.4f..3.0f,
                                        colors = SliderDefaults.colors(thumbColor = MetroColors.Amber, activeTrackColor = MetroColors.Amber)
                                    )
                                    Text(
                                        text = "${s.adsSens}: ${String.format(Locale.US, "%.2f", config.touchAdsSensitivity)}",
                                        color = MetroColors.TextPrimary,
                                        fontSize = 12.sp
                                    )
                                    Slider(
                                        value = config.touchAdsSensitivity,
                                        onValueChange = {
                                            config = config.copy(touchAdsSensitivity = it)
                                            prefs.save(config)
                                        },
                                        valueRange = 0.2f..2.0f,
                                        colors = SliderDefaults.colors(thumbColor = MetroColors.Amber, activeTrackColor = MetroColors.Amber)
                                    )
                                    Text(
                                        text = "${s.gyroSens}: ${String.format(Locale.US, "%.2f", config.gyroSensitivity)}",
                                        color = MetroColors.TextPrimary,
                                        fontSize = 12.sp
                                    )
                                    Slider(
                                        value = config.gyroSensitivity,
                                        onValueChange = {
                                            config = config.copy(gyroSensitivity = it)
                                            prefs.save(config)
                                        },
                                        valueRange = 0.2f..3.0f,
                                        colors = SliderDefaults.colors(thumbColor = MetroColors.Amber, activeTrackColor = MetroColors.Amber)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                menuOpen = false
                                                NativeBridge.lifecycle(5) // continue
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MetroColors.Amber),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(s.resumeGame, color = MetroColors.BgDeep, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                finish()
                                                NativeBridge.terminateProcess()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(s.exitToLauncher, color = MetroColors.DangerRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
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
            if (!gamepadActive) gamepadActive = true
            val down = event.action == KeyEvent.ACTION_DOWN
            if (NativeBridge.inputKey(event.deviceId, event.keyCode, down)) return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            menuOpen = !menuOpen
            NativeBridge.lifecycle(if (menuOpen) 4 else 5)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val src = event.source
        if ((src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        ) {
            if (!gamepadActive) gamepadActive = true
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
