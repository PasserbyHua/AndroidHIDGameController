package com.tools.gamecontroller

object GamepadReportDescriptor {
    // 报告 ID 为 1，长度为 4 字节（按键 + 左摇杆 X/Y + 右摇杆 X）
    val DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),          // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x05.toByte(),          // USAGE (Game Pad)
        0xA1.toByte(), 0x01.toByte(),          // COLLECTION (Application)
        0x85.toByte(), 0x01.toByte(),          //   REPORT_ID (1)
        // 按键 (8 个按钮，用 1 字节表示)
        0x05.toByte(), 0x09.toByte(),          //   USAGE_PAGE (Button)
        0x19.toByte(), 0x01.toByte(),          //   USAGE_MINIMUM (Button 1)
        0x29.toByte(), 0x08.toByte(),          //   USAGE_MAXIMUM (Button 8)
        0x15.toByte(), 0x00.toByte(),          //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),          //   LOGICAL_MAXIMUM (1)
        0x95.toByte(), 0x08.toByte(),          //   REPORT_COUNT (8)
        0x75.toByte(), 0x01.toByte(),          //   REPORT_SIZE (1)
        0x81.toByte(), 0x02.toByte(),          //   INPUT (Data,Var,Abs)
        // 左摇杆 X/Y (2 字节)
        0x05.toByte(), 0x01.toByte(),          //   USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),          //   USAGE (X)
        0x09.toByte(), 0x31.toByte(),          //   USAGE (Y)
        0x15.toByte(), 0x81.toByte(),          //   LOGICAL_MINIMUM (-127)
        0x25.toByte(), 0x7F.toByte(),          //   LOGICAL_MAXIMUM (127)
        0x75.toByte(), 0x08.toByte(),          //   REPORT_SIZE (8)
        0x95.toByte(), 0x02.toByte(),          //   REPORT_COUNT (2)
        0x81.toByte(), 0x06.toByte(),          //   INPUT (Data,Var,Rel)
        // 右摇杆 X (1 字节)
        0x09.toByte(), 0x32.toByte(),          //   USAGE (Z)
        0x15.toByte(), 0x81.toByte(),          //   LOGICAL_MINIMUM (-127)
        0x25.toByte(), 0x7F.toByte(),          //   LOGICAL_MAXIMUM (127)
        0x75.toByte(), 0x08.toByte(),          //   REPORT_SIZE (8)
        0x95.toByte(), 0x01.toByte(),          //   REPORT_COUNT (1)
        0x81.toByte(), 0x06.toByte(),          //   INPUT (Data,Var,Rel)
        0xC0.toByte()                           // END_COLLECTION
    )

    // 设备名称（将显示在其他设备的蓝牙列表中）
    const val DEVICE_NAME = "GameController"
    const val DESCRIPTION = "Android Gamepad"
    const val PROVIDER = "Example"
}