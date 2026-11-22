package dev.lastlight.reborn.nativecore

import android.view.Surface

object NativeBridge {
    const val VIRTUAL_PAD_DEVICE_ID = 0x70000001

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    System.loadLibrary("nxhost")
                    System.loadLibrary("lastlight_bridge")
                    loaded = true
                }
            }
        }
    }

    @JvmStatic external fun startHost(patchedDir: String, envVars: Array<String>, logPath: String): Boolean
    @JvmStatic external fun setWindow(surface: Surface?)
    @JvmStatic external fun lifecycle(event: Int)
    @JvmStatic external fun audioInterrupt(on: Boolean)
    @JvmStatic external fun touchControlsEnable(on: Boolean)
    @JvmStatic external fun inputDeviceAdded(device: Int, name: String, labelled: Int): Int
    @JvmStatic external fun inputDeviceRemoved(device: Int)
    @JvmStatic external fun inputKey(device: Int, keycode: Int, down: Boolean): Boolean
    @JvmStatic external fun inputAxes(device: Int, axes: IntArray, values: FloatArray, count: Int): Boolean
    @JvmStatic external fun inputDeadzone(dz: Float)
    @JvmStatic external fun fpsCapSet(fps: Int)
    @JvmStatic external fun getFps(out: FloatArray): Int
    @JvmStatic external fun terminateProcess()
}
