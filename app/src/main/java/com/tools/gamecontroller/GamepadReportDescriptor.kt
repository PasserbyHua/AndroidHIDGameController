package com.tools.gamecontroller

object GamepadReportDescriptor {
    // ============================================================
    // 报告格式总览（Report ID=1，载荷 9 字节 = 72 bit）
    //
    //  字节偏移   字段                    占位         取值范围
    //  -----------------------------------------------
    //  0~1       16个按键                2 字节(16bit) 每位 0/1
    //  2         D-Pad 十字键(Hat)       1 字节(8bit)  0~7(方向) 或 15(空)
    //  3~4       左摇杆 X/Y              2 字节(8bit)  -127~127
    //  5~6       右摇杆 Z/Rz             2 字节(8bit)  -127~127
    //  7         左扳机 Brake            1 字节(8bit)  0~255
    //  8         右扳机 Accelerator      1 字节(8bit)  0~255
    //
    //  HID 描述符的通用格式：每个 Item = [前缀字节 0xNN] + [数据字节...]
    //  前缀字节 = bSize(低2位) | bType(中2位) | bTag(高4位)
    //    bSize=1 → 后跟 1 个数据字节
    //    bType: 0=Main(主项) 1=Global(全局项) 2=Local(局部项)
    //    高位 bTag 决定具体是什么项
    // ============================================================
    const val REPORT_LENGTH = 9
    // 描述符版本：描述符内容发生变化时必须 +1，用于检测目标设备是否缓存了旧描述符
    const val DESCRIPTOR_VERSION = 7

    val DESCRIPTOR = byteArrayOf(
        // ============================================================
        // ① 顶层集合：声明"这是一个游戏手柄"
        // ============================================================
        0x05.toByte(), 0x01.toByte(),
        //  0x05 = 全局项 | Usage Page（使用页），后跟 1 字节
        //  0x01 = 使用页 = Generic Desktop（通用桌面设备）

        0x09.toByte(), 0x05.toByte(),
        //  0x09 = 局部项 | Usage（用途），后跟 1 字节
        //  0x05 = 用途 = Game Pad（游戏手柄）

        0xA1.toByte(), 0x01.toByte(),
        //  0xA1 = 主项 | Collection（集合开始），后跟 1 字节
        //  0x01 = 集合类型 = Application（应用集合），
        //         表示"从这里开始描述一套完整的功能"

        0x85.toByte(), 0x01.toByte(),
        //  0x85 = 全局项 | Report ID（报告 ID），后跟 1 字节
        //  0x01 = 报告 ID = 1，本设备每帧上报都以字节 0x01 开头

        // ============================================================
        // ② 16 个按键（占用报告字节 0~1，共 2 字节）
        //    每个按键 1 bit：0=松开，1=按下
        // ============================================================
        0x05.toByte(), 0x09.toByte(),
        //  0x05 = 全局项 | Usage Page
        //  0x09 = 使用页 = Button（按键页，0x01~0xFFFF 都是键位）

        0x19.toByte(), 0x01.toByte(),
        //  0x19 = 局部项 | Usage Minimum（起始键位），后跟 1 字节
        //  0x01 = 从 Button 1 开始

        0x29.toByte(), 0x10.toByte(),
        //  0x29 = 局部项 | Usage Maximum（结束键位），后跟 1 字节
        //  0x10 = 到 Button 16 结束（1~16 共 16 个键位）
        //         键位序号在 Android 里对应：1=A 2=B 3=X 4=Y 5=LB
        //         6=RB 7=LT 8=RT 9=BACK 10=START（对应 0x01/0x02/0x08...掩码）

        0x15.toByte(), 0x00.toByte(),
        //  0x15 = 全局项 | Logical Minimum（逻辑最小值），后跟 1 字节
        //  0x00 = 每个按键位最小取值 0（松开）

        0x25.toByte(), 0x01.toByte(),
        //  0x25 = 全局项 | Logical Maximum（逻辑最大值）
        //  0x01 = 每个按键位最大取值 1（按下）
        //         → 所以每个按键是 1 bit 的开关量

        0x75.toByte(), 0x01.toByte(),
        //  0x75 = 全局项 | Report Size（每个字段的位宽），后跟 1 字节
        //  0x01 = 每个按键字段占 1 bit

        0x95.toByte(), 0x10.toByte(),
        //  0x95 = 全局项 | Report Count（字段数量）
        //  0x10 = 共 16 个字段（16 个按键 × 1bit = 16bit = 2 字节）
        //         → 对应报告的字节 0~1

        0x81.toByte(), 0x02.toByte(),
        //  0x81 = 主项 | Input（输入字段），后跟 1 字节属性
        //  0x02 = Data(可变数据), Variable(每个键独立), Absolute(绝对量)
        //         → 被控端读到这里，就知道报告字节 0~1 是 16 个按键位

        // ============================================================
        // ③ D-Pad 十字键（占用报告字节 2，共 1 字节）
        //    用 Hat Switch（帽子开关）表示 8 方向，取值 0~7
        // ============================================================
        0x05.toByte(), 0x01.toByte(),
        //  0x05 = 全局项 | Usage Page
        //  0x01 = 使用页回到 Generic Desktop（上一个字段用的是 Button 页）

        0x09.toByte(), 0x39.toByte(),
        //  0x09 = 局部项 | Usage
        //  0x39 = 用途 = Hat Switch（帽子开关/方向键）

        0x15.toByte(), 0x00.toByte(),
        //  0x15 = 全局项 | Logical Minimum
        //  0x00 = 最小值 0

        0x25.toByte(), 0x07.toByte(),
        //  0x25 = 全局项 | Logical Maximum
        //  0x07 = 最大值 7（8 个方向值：0上 1右上 2右 3右下 4下 5左下 6左 7左上）

        0x35.toByte(), 0x00.toByte(),
        //  0x35 = 全局项 | Physical Minimum（物理最小值），后跟 1 字节
        //  0x00 = 物理量最小 0 度

        0x46.toByte(), 0x3B.toByte(), 0x01.toByte(),
        //  0x46 = 全局项 | Physical Maximum（物理最大值），后跟 2 字节(小端)
        //  0x3B,0x01 = 315（度）→ 表示方向值对应 0°~315°（每 45° 一档）

        0x65.toByte(), 0x14.toByte(),
        //  0x65 = 全局项 | Unit（单位），后跟 1 字节
        //  0x14 = 单位 = 角度(Degrees)，让被控端知道物理量是"度"

        0x75.toByte(), 0x08.toByte(),
        //  0x75 = 全局项 | Report Size
        //  0x08 = 字段位宽 8 bit = 1 字节

        0x95.toByte(), 0x01.toByte(),
        //  0x95 = 全局项 | Report Count
        //  0x01 = 1 个字段 → 对应报告的字节 2

        0x81.toByte(), 0x42.toByte(),
        //  0x81 = 主项 | Input，后跟 1 字节属性
        //  0x42 = Data + Variable + Absolute + Null State(允许空值)
        //         Null State 表示可以发送"无方向"值(8~15)，被控端视为松开
        //         → 被控端读到这里，知道报告字节 2 是 D-Pad 方向

        // ============================================================
        // ④ 左摇杆（占用报告字节 3~4，共 2 字节）
        //    X=字节3，Y=字节4，均为 -127~127 的有符号值
        // ============================================================
        0x09.toByte(), 0x30.toByte(),
        //  0x09 = 局部项 | Usage
        //  0x30 = 用途 = X（第一个模拟轴，即左摇杆水平轴）
        //         ⚠️ 注意：这里没有重设 Usage Page，沿用上一步的
        //         Generic Desktop，因为 X/Y 轴本身就定义在这个页里

        0x09.toByte(), 0x31.toByte(),
        //  0x09 = 局部项 | Usage
        //  0x31 = 用途 = Y（第二个模拟轴，即左摇杆垂直轴）

        0x15.toByte(), 0x81.toByte(),
        //  0x15 = 全局项 | Logical Minimum
        //  0x81 = -127（有符号，用 0x81 表示 -127）

        0x25.toByte(), 0x7F.toByte(),
        //  0x25 = 全局项 | Logical Maximum
        //  0x7F = 127 → 摇杆量程 -127~127，0 为中心

        0x75.toByte(), 0x08.toByte(),
        //  0x75 = 全局项 | Report Size
        //  0x08 = 每个轴 8 bit

        0x95.toByte(), 0x02.toByte(),
        //  0x95 = 全局项 | Report Count
        //  0x02 = 2 个轴（X、Y）连续存放 → 对应报告的字节 3~4

        0x81.toByte(), 0x02.toByte(),
        //  0x81 = 主项 | Input
        //  0x02 = Data, Variable, Absolute
        //         → 被控端读到 X、Y 两个独立模拟轴（映射为 AXIS_X / AXIS_Y）

        // ============================================================
        // ⑤ 右摇杆（占用报告字节 5~6，共 2 字节）
        //    用 Z=字节5，Rz=字节6 表示。⚠️ 关键点：
        //    Android 被控端只把 Z/Rz 识别为右摇杆（AXIS_Z / AXIS_RZ），
        //    Rx/Ry 会被忽略，所以右摇杆必须用 Z/Rz 这两个 usage
        // ============================================================
        0x09.toByte(), 0x32.toByte(),
        //  0x09 = 局部项 | Usage
        //  0x32 = 用途 = Z（右摇杆水平轴）

        0x09.toByte(), 0x35.toByte(),
        //  0x09 = 局部项 | Usage
        //  0x35 = 用途 = Rz（右摇杆垂直轴）

        0x15.toByte(), 0x81.toByte(),
        //  0x15 = 全局项 | Logical Minimum
        //  0x81 = -127

        0x25.toByte(), 0x7F.toByte(),
        //  0x25 = 全局项 | Logical Maximum
        //  0x7F = 127 → 与左摇杆相同，量程 -127~127

        0x75.toByte(), 0x08.toByte(),
        //  0x75 = 全局项 | Report Size
        //  0x08 = 每个轴 8 bit

        0x95.toByte(), 0x02.toByte(),
        //  0x95 = 全局项 | Report Count
        //  0x02 = 2 个轴（Z、Rz）→ 对应报告的字节 5~6

        0x81.toByte(), 0x02.toByte(),
        //  0x81 = 主项 | Input
        //  0x02 = Data, Variable, Absolute
        //         → 被控端读为 AXIS_Z / AXIS_RZ（右摇杆）

        // ============================================================
        // ⑥ 左右扳机（占用报告字节 7~8，共 2 字节）
        //    Brake=字节7（左扳机），Accelerator=字节8（右扳机）
        //    范围 0~255（0=未按，255=满按），单向按压量。
        //    ⚠️ 关键点（这就是之前"按一个触发两个"的修复）：
        //    1. 右扳机必须用 Accelerator(0xC8) 而不是 Clutch(0xC5)，
        //       Android 把 Accelerator 映射为 AXIS_GAS/AXIS_RTRIGGER，
        //       Clutch 没有标准映射会被错误合并。
        //    2. 两个轴拆成两个独立的 Input 项（各自 Report Count=1），
        //       避免"合并字段"写法导致被控端把两个字节当一个轴解析。
        // ============================================================
        0x05.toByte(), 0x02.toByte(),
        //  0x05 = 全局项 | Usage Page
        //  0x02 = 使用页切换为 Simulation Controls（仿真控制页）
        //         ⚠️ 这里必须换页，因为 Brake/Accelerator 不在此前的
        //         Generic Desktop 页里，而是定义在仿真控制页

        0x09.toByte(), 0xC4.toByte(),
        //  0x09 = 局部项 | Usage
        //  0xC4 = 用途 = Brake（刹车）→ Android 映射为 AXIS_BRAKE/AXIS_LTRIGGER，
        //         即左扳机

        0x15.toByte(), 0x00.toByte(),
        //  0x15 = 全局项 | Logical Minimum
        //  0x00 = 最小值 0（未按下）

        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),
        //  0x26 = 全局项 | Logical Maximum（后跟 2 字节，小端）
        //  0xFF,0x00 = 255（满按下）
        //         ⚠️ 注意这里是 0x26 而不是 0x25：因为 255 超出 1 字节
        //         有符号范围(127)，所以用 2 字节形式扩展 Logical Maximum

        0x75.toByte(), 0x08.toByte(),
        //  0x75 = 全局项 | Report Size
        //  0x08 = 每个扳机 8 bit

        0x95.toByte(), 0x01.toByte(),
        //  0x95 = 全局项 | Report Count
        //  0x01 = 只有 1 个字段（只描述 Brake 这一个轴）
        //         → 对应报告的字节 7

        0x81.toByte(), 0x02.toByte(),
        //  0x81 = 主项 | Input
        //  0x02 = Data, Variable, Absolute
        //         → 被控端读为 AXIS_BRAKE（左扳机）
        //  —— 到这里，字节 7 的字段定义结束 ——

        0x09.toByte(), 0xC8.toByte(),
        //  0x09 = 局部项 | Usage
        //  0xC8 = 用途 = Accelerator（油门/加速）
        //         → Android 映射为 AXIS_GAS/AXIS_RTRIGGER，即右扳机
        //         ⚠️ 沿用上方已设好的 Logical 0~255 / Size 8 / 属性，
        //         只需声明新用途，无需重复声明逻辑范围

        0x95.toByte(), 0x01.toByte(),
        //  0x95 = 全局项 | Report Count
        //  0x01 = 1 个字段（右扳机独立成项）→ 对应报告的字节 8

        0x81.toByte(), 0x02.toByte(),
        //  0x81 = 主项 | Input
        //  0x02 = Data, Variable, Absolute
        //         → 被控端读为 AXIS_GAS（右扳机）
        //  —— 到这里，字节 8 的字段定义结束，左右扳机完全独立 ——

        // ============================================================
        // ⑦ 收尾
        // ============================================================
        0xC0.toByte()
        //  0xC0 = 主项 | End Collection（集合结束）
        //         → 结束第①步开启的 Application 集合，
        //           至此描述符完整结束
    )

    // 设备名称（将显示在其他设备的蓝牙列表中）
    const val DEVICE_NAME = "GameController"
    const val DESCRIPTION = "Android Gamepad"
    const val PROVIDER = "Example"
}
