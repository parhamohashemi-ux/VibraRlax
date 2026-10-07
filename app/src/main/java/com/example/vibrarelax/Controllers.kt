package com.example.vibrarelax

import android.os.Build
import android.os.VibratorManager
import android.view.InputDevice

object Controllers {
    /** Vibrator managers of every connected gamepad that exposes a motor (Android 12+). */
    fun find(): List<VibratorManager> {
        if (Build.VERSION.SDK_INT < 31) return emptyList()
        val result = ArrayList<VibratorManager>()
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            val s = dev.sources
            val isPad = (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (!isPad) continue
            val vm = dev.vibratorManager
            if (vm.vibratorIds.isNotEmpty()) result.add(vm)
        }
        return result
    }
}
