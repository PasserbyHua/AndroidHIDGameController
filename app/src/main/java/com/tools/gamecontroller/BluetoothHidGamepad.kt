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

        // 按键位定义（对应报告中的低 8 位）
        const val BUTTON_A = 0x01
        const val BUTTON_B = 0x02
        const val BUTTON_X = 0x04
        const val BUTTON_Y = 0x08
        const val BUTTON_START = 0x10
        const val BUTTON_SELECT = 0x20
        const val BUTTON_LB = 0x40
        const val BUTTON_RB = 0x80.toInt()
        // 摇杆范围 -127 ~ 127
    }

    private var buttonState = 0
    private var leftX = 0
    private var leftY = 0
    private var rightX = 0

    fun sendReport() {
        if (hidDevice == null || remoteDevice == null) {
            Log.e(TAG, "HID device or remote device is null")
            return
        }
        val report = byteArrayOf(
            buttonState.toByte(),
            leftX.toByte(),
            leftY.toByte(),
            rightX.toByte()
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

    fun setRightStick(x: Int) {
        rightX = x.coerceIn(-127, 127)
        sendReport()
    }

    // 快捷方法：设置所有摇杆
    fun setSticks(leftX: Int, leftY: Int, rightX: Int) {
        this.leftX = leftX.coerceIn(-127, 127)
        this.leftY = leftY.coerceIn(-127, 127)
        this.rightX = rightX.coerceIn(-127, 127)
        sendReport()
    }

    // 重置所有状态
    fun reset() {
        buttonState = 0
        leftX = 0
        leftY = 0
        rightX = 0
        sendReport()
    }
}