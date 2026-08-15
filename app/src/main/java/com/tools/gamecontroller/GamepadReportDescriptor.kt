package com.tools.gamecontroller

object GamepadReportDescriptor {
    // 报告 ID 为 1，载荷 9 字节：
    // 按键 2 + Hat 1 + 左摇杆 X/Y 2 + 右摇杆主映射 Z/Rz 2 + 右摇杆别名映射 Rx/Ry 2
    // 报告载荷长度：必须与下方 DESCRIPTOR 声明的总位宽一致（72 bit = 9 byte）
    const val REPORT_LENGTH = 9
    // 描述符版本：描述符内容发生变化时必须 +1，用于检测目标设备是否缓存了旧描述符
    const val DESCRIPTOR_VERSION = 3

    val DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),          // Usage Page (Generic Desktop)
        0x09.toByte(), 0x05.toByte(),          // Usage (Game Pad)
        0xA1.toByte(), 0x01.toByte(),          // Collection (Application)
        0x85.toByte(), 0x01.toByte(),          //   Report ID (1)

        // ===== 1. 16个按键 (占用2个字节) =====
        0x05.toByte(), 0x09.toByte(),          //   Usage Page (Button)
        0x19.toByte(), 0x01.toByte(),          //   Usage Minimum (Button 1)
        0x29.toByte(), 0x10.toByte(),          //   Usage Maximum (Button 16)  // 支持16个按键
        0x15.toByte(), 0x00.toByte(),          //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),          //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),          //   Report Size (1)
        0x95.toByte(), 0x10.toByte(),          //   Report Count (16)         // 16个1比特字段 = 2字节
        0x81.toByte(), 0x02.toByte(),          //   Input (Data,Var,Abs)

        // ===== 2. 十字键 (D-Pad) 作为帽子开关 =====
        0x05.toByte(), 0x01.toByte(),          //   Usage Page (Generic Desktop)
        0x09.toByte(), 0x39.toByte(),          //   Usage (Hat Switch)
        0x15.toByte(), 0x00.toByte(),          //   Logical Minimum (0)
        0x25.toByte(), 0x07.toByte(),          //   Logical Maximum (7)       // 8个方向 (0-7)
        0x35.toByte(), 0x00.toByte(),          //   Physical Minimum (0)
        0x46.toByte(), 0x3B.toByte(), 0x01.toByte(),    //   Physical Maximum (315)    // 0-315度
        0x65.toByte(), 0x14.toByte(),          //   Unit (Degrees)
        0x75.toByte(), 0x08.toByte(),          //   Report Size (8)           // 使用1个字节
        0x95.toByte(), 0x01.toByte(),          //   Report Count (1)
        0x81.toByte(), 0x42.toByte(),          //   Input (Data,Var,Abs,Null) // 允许空值(无方向)

        // ===== 3. 左摇杆轴 (X, Y) =====
        0x09.toByte(), 0x30.toByte(),          //   Usage (X)
        0x09.toByte(), 0x31.toByte(),          //   Usage (Y)
        0x15.toByte(), 0x81.toByte(),          //   Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),          //   Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),          //   Report Size (8)
        0x95.toByte(), 0x02.toByte(),          //   Report Count (2)
        0x81.toByte(), 0x02.toByte(),          //   Input (Data,Var,Abs)

        // ===== 4. 右摇杆主映射 (Z, Rz) =====
        // 多数 Android / DirectInput 手柄把右摇杆映射为 Z、Rz
        0x09.toByte(), 0x32.toByte(),          //   Usage (Z)
        0x09.toByte(), 0x35.toByte(),          //   Usage (Rz)
        0x15.toByte(), 0x81.toByte(),          //   Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),          //   Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),          //   Report Size (8)
        0x95.toByte(), 0x02.toByte(),          //   Report Count (2)
        0x81.toByte(), 0x02.toByte(),          //   Input (Data,Var,Abs)


        // ===== 5. 右摇杆别名映射 (Rx, Ry) =====
        // 同时暴露 Rx/Ry，兼容只识别 Rx/Ry 的 PC / 主机
        0x09.toByte(), 0x33.toByte(),          //   Usage (Rx)
        0x09.toByte(), 0x34.toByte(),          //   Usage (Ry)
        0x15.toByte(), 0x81.toByte(),          //   Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),          //   Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),          //   Report Size (8)
        0x95.toByte(), 0x02.toByte(),          //   Report Count (2)
        0x81.toByte(), 0x02.toByte(),          //   Input (Data,Var,Abs)

        0xC0.toByte()                 // End Collection
    )

    // 设备名称（将显示在其他设备的蓝牙列表中）
    const val DEVICE_NAME = "GameController"
    const val DESCRIPTION = "Android Gamepad"
    const val PROVIDER = "Example"
}