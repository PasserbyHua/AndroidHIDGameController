package com.tools.gamecontroller

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.util.Log

class BluetoothHidGamepad(
    private val hidDevice: BluetoothHidDevice?,
    private val remoteDevice: BluetoothDevice?
) {
    companion object {
        private const val TAG = "HID_DEBUG"
        const val BUTTON_A = 0x0001
        const val BUTTON_B = 0x0002
        const val BUTTON_X = 0x0008
        const val BUTTON_Y = 0x0010
        const val BUTTON_LB = 0x0040
        const val BUTTON_RB = 0x0080
        const val BUTTON_LT = 0x0100
        const val BUTTON_RT = 0x0200
        const val BUTTON_BACK = 0x0400
        const val BUTTON_START = 0x0800
        const val HAT_UP = 0
        const val HAT_RIGHT = 2
        const val HAT_DOWN = 4
        const val HAT_LEFT = 6
        const val HAT_CENTER = 0x08
    }

    // 内部状态寄存器
    private var buttonState = 0
    private var hatSwitch = HAT_CENTER
    private var leftX = 0
    private var leftY = 0
    private val reportBuffer = ByteArray(5)

    fun getButtonState(): Int = buttonState

    // 【核心修改】纯寄存器写入，不发送
    fun setState(newButtonState: Int, newHatSwitch: Int) {
        buttonState = newButtonState
        hatSwitch = newHatSwitch
        // 不调用 sendReport()，完全依赖外部轮询
    }

    // 供轮询循环调用的发送方法
    fun sendReport() {
        if (hidDevice == null || remoteDevice == null) return
        reportBuffer[0] = (buttonState and 0xFF).toByte()
        reportBuffer[1] = ((buttonState shr 8) and 0xFF).toByte()
        reportBuffer[2] = hatSwitch.toByte()
        reportBuffer[3] = leftX.toByte()
        reportBuffer[4] = leftY.toByte()
        hidDevice.sendReport(remoteDevice, 1, reportBuffer)
    }

    fun setHatSwitch(direction: Int) {
        if (hatSwitch != direction) {
            hatSwitch = direction.coerceIn(0, 15)
            sendReport()
        }
    }

    fun setButton(button: Int, pressed: Boolean) {
        val oldState = buttonState
        if (pressed) {
            buttonState = buttonState or button
        } else {
            buttonState = buttonState and button.inv()
        }
        if (oldState != buttonState) {
            sendReport()
        }
    }

    fun reset() {
        buttonState = 0
        hatSwitch = HAT_CENTER
        leftX = 0
        leftY = 0
        sendReport()
    }

    // 在 BluetoothHidGamepad 类中添加
    fun setLeftStick(x: Int, y: Int) {
        leftX = x.coerceIn(-127, 127)
        leftY = y.coerceIn(-127, 127)
    }
}
