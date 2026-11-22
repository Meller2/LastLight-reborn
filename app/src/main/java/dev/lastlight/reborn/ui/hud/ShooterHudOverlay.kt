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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.pow

/**
 * Angular-displacement reservoir for Touch-Look over an analog right stick.
 * Instead of discarding swipe velocity that exceeds stick magnitude 1.0 on fast flicks,
 * excess angular impulse is queued (up to maxPending stick-seconds) and drained smoothly
 * at max stick rate, while instant direction reversals or stationary finger holds cancel
 * carry-over immediately for crisp micro-aiming.
 */
private class LookAccumulator {
    var pendingX = 0f
    var pendingY = 0f
    var fingerDown = false
    var lastMoveNs = 0L

    fun clear() {
        pendingX = 0f
        pendingY = 0f
    }
}

/**
 * Shooter Touch HUD tailored for Metro: Last Light Redux (Nintendo Switch):
 * - Left half: Floating virtual movement stick with auto-sprint (L3).
 * - Right half: DP-normalized Touch-Look with angular displacement carry-over queue
 *   and 2D radial deadzone/curve linearization so fast flicks & slow drags match angles.
 * - Hardware gyroscope strictly gated to OFF / ADS-only (ZL) / ALWAYS with EMA smoothing.
 * - Authentic Nintendo Switch labels (ZR, ZL, A, B, X, Y, R3, L, R, −, +, D-pad).
 */
@Composable
fun ShooterHudOverlay(
    config: LauncherConfig,
    strings: UiStrings,
    visible: Boolean,
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density.coerceAtLeast(1f)
    val dev = NativeBridge.VIRTUAL_PAD_DEVICE_ID

    val latestConfig by rememberUpdatedState(config)
    val latestVisible by rememberUpdatedState(visible)
    val latestOnOpenMenu by rememberUpdatedState(onOpenMenu)

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
    val latestAds by rememberUpdatedState(adsActive)
    val lookAcc = remember { LookAccumulator() }

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

    fun cancelAdsIfLatched() {
        if (adsActive) {
            adsActive = false
            lt = 0f
            gx = 0f
            gy = 0f
            pushAxes()
        }
    }

    // Reset all virtual inputs cleanly whenever HUD hides (e.g., pause menu or gamepad takeover)
    LaunchedEffect(visible) {
        if (!visible) {
            lookAcc.clear()
            lookAcc.fingerDown = false
            lx = 0f; ly = 0f
            rx = 0f; ry = 0f
            gx = 0f; gy = 0f
            lt = 0f; rt = 0f
            adsActive = false
            if (autoSprintHeld) {
                autoSprintHeld = false
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_THUMBL, false)
            }
            pushAxes()
        }
    }

    // Hardware gyroscope aiming strictly gated by GyroMode (OFF / ADS / ALWAYS)
    val gyroShouldRun = visible && (
        config.gyroMode == GyroMode.ALWAYS ||
            (config.gyroMode == GyroMode.ADS && adsActive)
        )
    DisposableEffect(gyroShouldRun) {
        if (!gyroShouldRun) {
            if (gx != 0f || gy != 0f) {
                gx = 0f
                gy = 0f
                pushAxes()
            }
            onDispose { }
        } else {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            var smoothYaw = 0f
            var smoothPitch = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val cfg = latestConfig
                    val active = latestVisible && (
                        cfg.gyroMode == GyroMode.ALWAYS ||
                            (cfg.gyroMode == GyroMode.ADS && latestAds)
                        )
                    if (!active) {
                        if (gx != 0f || gy != 0f) {
                            gx = 0f
                            gy = 0f
                            pushAxes()
                        }
                        return
                    }

                    val rot = runCatching { context.display?.rotation }.getOrNull() ?: Surface.ROTATION_90
                    val flip = if (rot == Surface.ROTATION_270) -1f else 1f
                    // In ROTATION_90: yaw right = -values[0], pitch down = +values[1]
                    val yawRate = -event.values[0] * flip
                    val pitchRate = event.values[1] * flip

                    // Light EMA filter to remove hand micro-tremor without noticeable lag
                    val ema = 0.42f
                    smoothYaw += (yawRate - smoothYaw) * ema
                    smoothPitch += (pitchRate - smoothPitch) * ema

                    val rateMag = hypot(smoothYaw, smoothPitch)
                    if (rateMag > 0.018f) {
                        val sens = cfg.gyroSensitivity * 0.46f
                        val dz = cfg.touchDeadzoneComp.coerceIn(0f, 0.35f) * 0.55f
                        val rawX = smoothYaw * sens
                        val rawY = smoothPitch * sens
                        val rawMag = hypot(rawX, rawY)
                        val clamped = rawMag.coerceAtMost(1f)
                        val outMag = (dz + clamped.pow(0.85f) * (1f - dz)).coerceAtMost(1f)
                        gx = (rawX / rawMag) * outMag
                        gy = (rawY / rawMag) * outMag
                    } else {
                        gx = 0f
                        gy = 0f
                    }
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

    // 120 Hz fixed-step angular displacement drainer for Touch-Look
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        var prevNs = System.nanoTime()
        val tau = 0.022f // 22 ms snappy smoothing constant
        while (true) {
            delay(8L)
            val nowNs = System.nanoTime()
            val dt = ((nowNs - prevNs) / 1_000_000_000f).coerceIn(0.004f, 0.035f)
            prevNs = nowNs

            // If finger is resting stationary on screen (>45 ms without motion), bleed residual carry-over quickly
            if (lookAcc.fingerDown && (nowNs - lookAcc.lastMoveNs) > 45_000_000L) {
                lookAcc.pendingX *= 0.40f
                lookAcc.pendingY *= 0.40f
                if (abs(lookAcc.pendingX) < 0.0005f) lookAcc.pendingX = 0f
                if (abs(lookAcc.pendingY) < 0.0005f) lookAcc.pendingY = 0f
            }

            var newRx = 0f
            var newRy = 0f
            val px = lookAcc.pendingX
            val py = lookAcc.pendingY
            if (abs(px) > 0.0003f || abs(py) > 0.0003f) {
                // Convert queued angular impulse to desired effective stick velocity
                var effX = px / tau
                var effY = py / tau
                val effMag = hypot(effX, effY)
                if (effMag > 1f) {
                    // Preserve exact diagonal direction while capping stick at 1.0;
                    // undelivered remainder stays in pendingX/Y for next ticks!
                    effX /= effMag
                    effY /= effMag
                }

                // Subtract only the impulse actually delivered during this tick
                val delX = effX * dt
                val delY = effY * dt
                lookAcc.pendingX = if (abs(delX) >= abs(px)) 0f else px - delX
                lookAcc.pendingY = if (abs(delY) >= abs(py)) 0f else py - delY

                // 2D radial deadzone + inverse curve compensation (linearizes 4A Engine stick curve)
                val outEffMag = hypot(effX, effY)
                if (outEffMag > 0.003f) {
                    val dz = latestConfig.touchDeadzoneComp.coerceIn(0f, 0.35f)
                    val curved = outEffMag.coerceAtMost(1f).pow(0.76f)
                    val outMag = (dz + curved * (1f - dz)).coerceAtMost(1f)
                    newRx = (effX / outEffMag) * outMag
                    newRy = (effY / outEffMag) * outMag
                }
            }

            if (newRx != rx || newRy != ry) {
                rx = newRx
                ry = newRy
                pushAxes()
            }
        }
    }

    if (!visible) return

    val alpha = config.hudOpacity.coerceIn(0.25f, 1f)
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
                    .pointerInput(density) {
                        val maxRadius = 46f * density
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

                                // Auto-sprint when pushing stick almost fully forward (cancels toggled ADS)
                                val wantSprint = ny < -0.90f && abs(nx) < 0.45f
                                if (wantSprint != autoSprintHeld) {
                                    autoSprintHeld = wantSprint
                                    if (wantSprint && latestConfig.adsToggle) {
                                        cancelAdsIfLatched()
                                    }
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
                        val outerR = 46f * density
                        val innerR = 17f * density
                        drawCircle(
                            color = MetroColors.TextPrimary.copy(alpha = alpha * 0.22f),
                            radius = outerR,
                            center = o,
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = (if (autoSprintHeld) MetroColors.PhosphorGreen else MetroColors.Amber).copy(alpha = alpha * 0.68f),
                            radius = innerR,
                            center = k
                        )
                    }
                }
            }

            // Right half: Free Touch-Look with angular displacement carry-over
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .pointerInput(density) {
                        val maxPending = 0.24f // <= 240ms max carry-over on hard flicks
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var prev = down.position
                            // Fresh touch cancels any residual flick immediately for crisp stopping
                            lookAcc.clear()
                            lookAcc.fingerDown = true
                            lookAcc.lastMoveNs = System.nanoTime()
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    lookAcc.fingerDown = false
                                    // Note: DO NOT clear pendingX/Y on lift — lets a fast thumb flick
                                    // smoothly finish its queued turn angle over <= 240 ms!
                                    break
                                }
                                val dxPx = ch.position.x - prev.x
                                val dyPx = ch.position.y - prev.y
                                prev = ch.position
                                if (abs(dxPx) > 0.15f || abs(dyPx) > 0.15f) {
                                    val dxDp = dxPx / density
                                    val dyDp = dyPx / density
                                    val cfg = latestConfig
                                    val sens = if (latestAds) cfg.touchAdsSensitivity else cfg.touchLookSensitivity
                                    val gain = 0.00185f * sens

                                    // Instant reversal cancellation: zero lag when flicking back
                                    if (dxDp * lookAcc.pendingX < 0f) lookAcc.pendingX = 0f
                                    if (dyDp * lookAcc.pendingY < 0f) lookAcc.pendingY = 0f

                                    val nextX = lookAcc.pendingX + dxDp * gain
                                    val nextY = lookAcc.pendingY + dyDp * gain
                                    val pMag = hypot(nextX, nextY)
                                    if (pMag > maxPending) {
                                        val s = maxPending / pMag
                                        lookAcc.pendingX = nextX * s
                                        lookAcc.pendingY = nextY * s
                                    } else {
                                        lookAcc.pendingX = nextX
                                        lookAcc.pendingY = nextY
                                    }
                                    lookAcc.lastMoveNs = System.nanoTime()
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
                if (down) cancelAdsIfLatched()
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_SELECT, down)
            }
            MetroPillButton("+", alpha, MetroColors.TextSecondary) { down ->
                if (down) cancelAdsIfLatched()
                NativeBridge.inputKey(dev, KeyEvent.KEYCODE_BUTTON_START, down)
            }
            MetroPillButton("≡", alpha, MetroColors.Amber) { down ->
                if (down) {
                    cancelAdsIfLatched()
                    latestOnOpenMenu()
                }
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

            // ZL (ADS) button above ZR with clear active indicator when latched
            MetroCircleButton(
                label = "ZL",
                size = 56.dp,
                accent = if (adsActive) MetroColors.PhosphorGreen else MetroColors.Amber,
                alpha = alpha,
                latched = adsActive,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 122.dp)
            ) { down ->
                if (latestConfig.adsToggle) {
                    if (down) {
                        val next = !adsActive
                        adsActive = next
                        lt = if (next) 1f else 0f
                        if (!next) {
                            gx = 0f
                            gy = 0f
                        }
                        pushAxes()
                    }
                } else {
                    adsActive = down
                    lt = if (down) 1f else 0f
                    if (!down) {
                        gx = 0f
                        gy = 0f
                    }
                    pushAxes()
                }
            }

            // Nintendo X (top — reload / interact)
            MetroCircleButton(
                label = "X",
                size = 50.dp,
                accent = MetroColors.Amber,
                alpha = alpha,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 104.dp, bottom = 128.dp)
            ) { down ->
                if (down && latestConfig.adsToggle) cancelAdsIfLatched()
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

            // Nintendo B (bottom — crouch)
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
    latched: Boolean = false,
    onStateChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnStateChanged by rememberUpdatedState(onStateChanged)
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = when {
                    pressed -> accent.copy(alpha = (alpha * 0.58f).coerceAtMost(0.92f))
                    latched -> accent.copy(alpha = (alpha * 0.30f).coerceAtMost(0.65f))
                    else -> MetroColors.BgDeep.copy(alpha = alpha * 0.58f)
                },
                shape = CircleShape
            )
            .border(
                width = if (pressed || latched) 2.dp else 1.5.dp,
                color = accent.copy(alpha = if (latched) 0.98f else alpha * 0.9f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    currentOnStateChanged(true)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                    }
                    pressed = false
                    currentOnStateChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MetroColors.TextPrimary.copy(alpha = (alpha + 0.25f).coerceAtMost(1f)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
        if (latched) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MetroColors.PhosphorGreen)
            )
        }
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
    val currentOnStateChanged by rememberUpdatedState(onStateChanged)
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
                    currentOnStateChanged(true)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                    }
                    pressed = false
                    currentOnStateChanged(false)
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
