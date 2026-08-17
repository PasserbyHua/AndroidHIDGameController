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
        const val HAT_UP_RIGHT = 1
        const val HAT_RIGHT = 2
        const val HAT_DOWN_RIGHT = 3
        const val HAT_DOWN = 4
        const val HAT_DOWN_LEFT = 5
        const val HAT_LEFT = 6
        const val HAT_UP_LEFT = 7
        const val HAT_CENTER = 0x08
    }

    // 内部状态寄存器
    private var buttonState = 0
    private var hatSwitch = HAT_CENTER
    private var leftX = 0
    private var leftY = 0
    // 左右扳机线性轴（Brake / Gas）
    private var triggerL = 0
    private var triggerR = 0
    private var rightX = 0
    private var rightY = 0
    private val reportBuffer = ByteArray(GamepadReportDescriptor.REPORT_LENGTH)

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
        // 右摇杆：Z / Rz（字节 5、6），Android 被控端识别此映射
        reportBuffer[5] = rightX.toByte()
        reportBuffer[6] = rightY.toByte()
        // 左右扳机：Brake / Clutch（字节 7、8），线性压感
        reportBuffer[7] = triggerL.toByte()
        reportBuffer[8] = triggerR.toByte()
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
        triggerL = 0
        triggerR = 0
        rightX = 0
        rightY = 0
        sendReport()
    }

    fun setLeftStick(x: Int, y: Int) {
        leftX = x.coerceIn(-127, 127)
        leftY = y.coerceIn(-127, 127)
    }

    fun setRightStick(x: Int, y: Int) {
        rightX = x.coerceIn(-127, 127)
        rightY = y.coerceIn(-127, 127)
    }

    // 设置左右扳机线性压感值：left 对应 Brake（左扳机），right 对应 Accelerator/Gas（右扳机）
    // 逻辑范围为 0~255，保证 Android /255 归一化后满量程
    fun setTriggers(left: Int, right: Int) {
        triggerL = left.coerceIn(0, 255)
        triggerR = right.coerceIn(0, 255)
    }
}
