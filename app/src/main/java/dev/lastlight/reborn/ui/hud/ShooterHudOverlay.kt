package dev.lastlight.reborn.ui.hud

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lastlight.reborn.data.GyroMode
import dev.lastlight.reborn.data.LauncherConfig
import dev.lastlight.reborn.nativecore.NativeBridge
import dev.lastlight.reborn.ui.UiStrings
import dev.lastlight.reborn.ui.theme.MetroColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * CoD Mobile / Standoff 2 style Shooter Touch HUD:
 * - Left half: Floating virtual movement stick with auto-sprint (L3) when pushed past top threshold.
 * - Right half: Free Touch-Look area converting swipe velocity into Right Stick (AXIS_Z / AXIS_RZ)
 *   with deadzone compensation and separate ADS sensitivity.
 * - Metro-tailored action buttons (Fire x2, ADS, Reload/Use, Jump, Crouch, Mask, Light, Weapon, Med).
 */
@Composable
fun ShooterHudOverlay(
    config: LauncherConfig,
    strings: UiStrings,
    visible: Boolean,
    onOpenMenu: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val dev = NativeBridge.VIRTUAL_PAD_DEVICE_ID
    val alpha = config.hudOpacity.coerceIn(0.25f, 1f)

    // Shared stick/trigger state pushed to NativeBridge
    var lx by remember { mutableFloatStateOf(0f) }
    var ly by remember { mutableFloatStateOf(0f) }
    var rx by remember { mutableFloatStateOf(0f) }
    var ry by remember { mutableFloatStateOf(0f) }
    var gx by remember { mutableFloatStateOf(0f) }
    var gy by remember { mutableFloatStateOf(0f) }
    var lt by remember { mutableFloatStateOf(0f) }
    var rt by remember { mutableFloatStateOf(0f) }
    var adsActive by remember { mutableStateOf(false) }
    var autoSprintHeld by remember { mutableStateOf(false) }
    var lastLookTouchMs by remember { mutableStateOf(0L) }

    val axesIds = remember {
        intArrayOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER
        )
    }
    val axesVals = remember { FloatArray(6) }

    fun pushAxes() {
        axesVals[0] = lx
        axesVals[1] = ly
        axesVals[2] = (rx + gx).coerceIn(-1f, 1f)
        axesVals[3] = (ry + gy).coerceIn(-1f, 1f)
        axesVals[4] = lt
        axesVals[5] = rt
        NativeBridge.inputAxes(dev, axesIds, axesVals, 6)
    }

    // Hardware gyroscope aiming (Always or ADS-only) blended smoothly with Touch-Look
    DisposableEffect(config.gyroMode, config.gyroSensitivity, config.touchDeadzoneComp, adsActive, visible) {
        val gyroEnabled = visible && (config.gyroMode == GyroMode.ALWAYS || (config.gyroMode == GyroMode.ADS && adsActive))
        if (!gyroEnabled) {
            if (gx != 0f || gy != 0f) {
                gx = 0f
                gy = 0f
                pushAxes()
            }
            onDispose { }
        } else {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val rot = runCatching { context.display?.rotation }.getOrNull() ?: Surface.ROTATION_90
                    val flip = if (rot == Surface.ROTATION_270) -1f else 1f
                    // In ROTATION_90: phone yaw right = -values[0], pitch down = +values[1]
                    val yawRate = -event.values[0] * flip
                    val pitchRate = event.values[1] * flip
                    val sens = config.gyroSensitivity * 0.48f
                    val dz = config.touchDeadzoneComp * 0.55f
                    val rawX = if (abs(yawRate) > 0.015f) yawRate * sens else 0f
                    val rawY = if (abs(pitchRate) > 0.015f) pitchRate * sens else 0f
                    gx = if (abs(rawX) > 0.005f) (sign(rawX) * (dz + abs(rawX) * (1f - dz))).coerceIn(-1f, 1f) else 0f
                    gy = if (abs(rawY) > 0.005f) (sign(rawY) * (dz + abs(rawY) * (1f - dz))).coerceIn(-1f, 1f) else 0f
                    pushAxes()
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            if (gyro != null) {
                sm.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
            }
            onDispose {
                sm?.unregisterListener(listener)
                gx = 0f
                gy = 0f
                pushAxes()
            }
        }
    }

    // Decay right stick to 0 immediately when finger pauses on the touch-look zone
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            if ((rx != 0f || ry != 0f) && (System.currentTimeMillis() - lastLookTouchMs > 28L)) {
                rx = 0f
                ry = 0f
                pushAxes()
            }
        }
    }

    // Visual state for floating left stick
    var stickOrigin by remember { mutableStateOf<Offset?>(null) }
    var stickKnob by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Background touch zones: Left = Floating Move Stick, Right = Free Touch-Look
        Row(modifier = Modifier.fillMaxSize()) {
            // Left half: Floating movement stick
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        val maxRadius = 125f
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            stickOrigin = down.position
                            stickKnob = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    stickOrigin = null
                                    stickKnob = null
                                    lx = 0f
                                    ly = 0f
                                    if (autoSprintHeld) {
                                        autoSprintHeld = false
                                        NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_THUMBL, false)
                                    }
                                    pushAxes()
                                    break
                                }
                                val origin = stickOrigin ?: down.position
                                val dx = ch.position.x - origin.x
                                val dy = ch.position.y - origin.y
                                val dist = hypot(dx, dy)
                                val clamped = dist.coerceAtMost(maxRadius)
                                val nx = if (dist > 1f) (dx / dist) * (clamped / maxRadius) else 0f
                                val ny = if (dist > 1f) (dy / dist) * (clamped / maxRadius) else 0f
                                stickKnob = Offset(origin.x + nx * maxRadius, origin.y + ny * maxRadius)
                                lx = nx.coerceIn(-1f, 1f)
                                ly = ny.coerceIn(-1f, 1f)

                                // Auto-sprint when pushing stick almost fully forward
                                val wantSprint = ny < -0.90f && abs(nx) < 0.45f
                                if (wantSprint != autoSprintHeld) {
                                    autoSprintHeld = wantSprint
                                    NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_THUMBL, wantSprint)
                                }
                                pushAxes()
                                ch.consume()
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val o = stickOrigin
                    val k = stickKnob
                    if (o != null && k != null) {
                        drawCircle(
                            color = MetroColors.TextPrimary.copy(alpha = alpha * 0.22f),
                            radius = 125f,
                            center = o,
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = (if (autoSprintHeld) MetroColors.PhosphorGreen else MetroColors.Amber).copy(alpha = alpha * 0.65f),
                            radius = 46f,
                            center = k
                        )
                    }
                }
            }

            // Right half: Free Touch-Look (CoD Mobile / Standoff 2 feel)
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .pointerInput(config.touchLookSensitivity, config.touchAdsSensitivity, config.touchDeadzoneComp, adsActive) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var prev = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    rx = 0f
                                    ry = 0f
                                    pushAxes()
                                    break
                                }
                                val dx = ch.position.x - prev.x
                                val dy = ch.position.y - prev.y
                                prev = ch.position
                                if (abs(dx) > 0.25f || abs(dy) > 0.25f) {
                                    val sens = if (adsActive) config.touchAdsSensitivity else config.touchLookSensitivity
                                    val dz = config.touchDeadzoneComp
                                    // Convert pixel delta to stick deflection with deadzone compensation
                                    val rawX = (dx / 26f) * sens
                                    val rawY = (dy / 26f) * sens
                                    rx = if (abs(rawX) > 0.01f) (sign(rawX) * (dz + abs(rawX) * (1f - dz))).coerceIn(-1f, 1f) else 0f
                                    ry = if (abs(rawY) > 0.01f) (sign(rawY) * (dz + abs(rawY) * (1f - dz))).coerceIn(-1f, 1f) else 0f
                                    lastLookTouchMs = System.currentTimeMillis()
                                    pushAxes()
                                }
                                ch.consume()
                            }
                        }
                    }
            )
        }

        // 2. Top bar: L / R / - / + / D-pad / Menu with real Nintendo Switch labels
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroPillButton("L", alpha, MetroColors.Amber) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_L1, down)
            }
            MetroPillButton("◀", alpha, MetroColors.TextSecondary) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_DPAD_LEFT, down)
            }
            MetroPillButton("▲", alpha, MetroColors.TextSecondary) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_DPAD_UP, down)
            }
            MetroPillButton("▼", alpha, MetroColors.TextSecondary) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_DPAD_DOWN, down)
            }
            MetroPillButton("▶", alpha, MetroColors.TextSecondary) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_DPAD_RIGHT, down)
            }
            MetroPillButton("R", alpha, MetroColors.Amber) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_R1, down)
            }
            MetroPillButton("−", alpha, MetroColors.TextSecondary) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_SELECT, down)
            }
            MetroPillButton("+", alpha, MetroColors.TextSecondary) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_START, down)
            }
            MetroPillButton("≡", alpha, MetroColors.Amber) { down ->
                if (down) onOpenMenu()
            }
        }

        // 3. Left upper ZR button (for 3-finger claw shooting)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 56.dp)
        ) {
            MetroCircleButton(
                label = "ZR",
                size = 62.dp,
                accent = MetroColors.DangerRed,
                alpha = alpha
            ) { down ->
                rt = if (down) 1f else 0f
                pushAxes()
            }
        }

        // 4. Right side shooter cluster with real Nintendo Switch button names (ZR, ZL, A, B, X, Y, R3)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
        ) {
            // Main ZR (Fire) button
            MetroCircleButton(
                label = "ZR",
                size = 74.dp,
                accent = MetroColors.DangerRed,
                alpha = alpha,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 36.dp)
            ) { down ->
                rt = if (down) 1f else 0f
                pushAxes()
            }

            // ZL (ADS) button above ZR
            MetroCircleButton(
                label = "ZL",
                size = 56.dp,
                accent = if (adsActive) MetroColors.PhosphorGreen else MetroColors.Amber,
                alpha = alpha,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 122.dp)
            ) { down ->
                if (config.adsToggle) {
                    if (down) {
                        adsActive = !adsActive
                        lt = if (adsActive) 1f else 0f
                        pushAxes()
                    }
                } else {
                    adsActive = down
                    lt = if (down) 1f else 0f
                    pushAxes()
                }
            }

            // Nintendo X (top)
            MetroCircleButton(
                label = "X",
                size = 50.dp,
                accent = MetroColors.Amber,
                alpha = alpha,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 104.dp, bottom = 128.dp)
            ) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_X, down)
            }

            // Nintendo Y (left)
            MetroCircleButton(
                label = "Y",
                size = 50.dp,
                accent = MetroColors.TextPrimary,
                alpha = alpha,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 154.dp, bottom = 78.dp)
            ) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_Y, down)
            }

            // Nintendo A (right)
            MetroCircleButton(
                label = "A",
                size = 50.dp,
                accent = MetroColors.TextPrimary,
                alpha = alpha,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 94.dp, bottom = 68.dp)
            ) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_A, down)
            }

            // Nintendo B (bottom)
            MetroCircleButton(
                label = "B",
                size = 50.dp,
                accent = MetroColors.TextPrimary,
                alpha = alpha,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 136.dp, bottom = 14.dp)
            ) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_B, down)
            }

            // R3 (melee / stick click)
            MetroCircleButton(
                label = "R3",
                size = 44.dp,
                accent = MetroColors.TextSecondary,
                alpha = alpha,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 196.dp, bottom = 16.dp)
            ) { down ->
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_THUMBR, down)
            }
        }
    }
}

@Composable
private fun MetroCircleButton(
    label: String,
    size: Dp,
    accent: Color,
    alpha: Float,
    modifier: Modifier = Modifier,
    onStateChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = if (pressed) accent.copy(alpha = (alpha * 0.55f).coerceAtMost(0.9f))
                else MetroColors.BgDeep.copy(alpha = alpha * 0.58f),
                shape = CircleShape
            )
            .border(
                width = if (pressed) 2.dp else 1.5.dp,
                color = accent.copy(alpha = alpha * 0.9f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onStateChanged(true)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                    }
                    pressed = false
                    onStateChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MetroColors.TextPrimary.copy(alpha = (alpha + 0.2f).coerceAtMost(1f)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MetroPillButton(
    label: String,
    alpha: Float,
    accent: Color,
    onStateChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .background(
                color = if (pressed) accent.copy(alpha = alpha * 0.6f)
                else MetroColors.BgDeep.copy(alpha = alpha * 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(1.dp, accent.copy(alpha = alpha * 0.85f), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onStateChanged(true)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                    }
                    pressed = false
                    onStateChanged(false)
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MetroColors.TextPrimary.copy(alpha = (alpha + 0.2f).coerceAtMost(1f)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}
