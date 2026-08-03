package com.tools.gamecontroller

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.util.Log

class BluetoothHidGamepad(
    private val hidDevice: BluetoothHidDevice?,
    private val remoteDevice: BluetoothDevice?
) {
    companion object {
        private const val TAG = "BluetoothHidGamepad"

        // 按键位定义（16个按键，占用低16位）
        const val BUTTON_A = 0x0001
        const val BUTTON_B = 0x0002
        const val BUTTON_X = 0x0008      // 您自定义的顺序
        const val BUTTON_Y = 0x0010
        const val BUTTON_LB = 0x0010     // 注意：这里与 Y 冲突？需修正
        const val BUTTON_RB = 0x0020
        const val BUTTON_SELECT = 0x0040
        const val BUTTON_START = 0x0080
        const val BUTTON_9 = 0x0100
        const val BUTTON_10 = 0x0200

        // 帽子开关方向值（0-7表示8个方向，0x0F表示无方向）
        const val HAT_UP = 0
        const val HAT_UP_RIGHT = 1
        const val HAT_RIGHT = 2
        const val HAT_DOWN_RIGHT = 3
        const val HAT_DOWN = 4
        const val HAT_DOWN_LEFT = 5
        const val HAT_LEFT = 6
        const val HAT_UP_LEFT = 7
        const val HAT_CENTER = 0x09
    }

    private var buttonState = 0
    private var hatSwitch = HAT_CENTER  // 默认无方向
    private var leftX = 0
    private var leftY = 0

    fun getButtonState(): Int = buttonState

    fun setHatSwitch(direction: Int) {
        hatSwitch = direction.coerceIn(0, 15)  // 允许 0-15
        sendReport()
    }

    fun resetHatSwitch() {
        hatSwitch = HAT_CENTER
        sendReport()
    }

    fun sendReport() {
        if (hidDevice == null || remoteDevice == null) {
            Log.e(TAG, "HID device or remote device is null")
            return
        }
        // 构建5字节报告
        val report = byteArrayOf(
            (buttonState and 0xFF).toByte(),           // 字节0: 按键低8位
            ((buttonState shr 8) and 0xFF).toByte(),   // 字节1: 按键高8位
            hatSwitch.toByte(),                        // 字节2: 帽子开关
            leftX.toByte(),                            // 字节3: 左摇杆X
            leftY.toByte()                             // 字节4: 左摇杆Y
        )
        Log.d(TAG, "sendReport: ${report.joinToString()}")
        hidDevice.sendReport(remoteDevice, 1, report)
    }

    // 设置按键（按下或释放）
    fun setButton(button: Int, pressed: Boolean) {
        if (pressed) {
            buttonState = buttonState or button
        } else {
            buttonState = buttonState and button.inv()
        }
        sendReport()
    }

    // 设置摇杆值（范围 -127 ~ 127）
    fun setLeftStick(x: Int, y: Int) {
        leftX = x.coerceIn(-127, 127)
        leftY = y.coerceIn(-127, 127)
        sendReport()
    }

    // 重置所有状态
    fun reset() {
        buttonState = 0
        hatSwitch = HAT_CENTER
        leftX = 0
        leftY = 0
        sendReport()
    }
}
