# GameController — 蓝牙手柄模拟器

一个基于 **Android 蓝牙 HID（Human Interface Device）协议** 的安卓应用，可以将手机模拟成无线游戏手柄，通过蓝牙连接到 PC、智能电视、游戏主机等设备并控制游戏。

应用内置多种操作模式，支持实体按键触控区域、重力感应摇杆、滑块摇杆等多种操控方式，数据以 125Hz 频率实时上报。

## 功能特性

- **蓝牙 HID 手柄模拟**：将手机注册为 Bluetooth HID 设备（子类：Gamepad），其他设备可像识别普通手柄一样识别它
- **手柄键位**：
  - HID 描述符支持 16 个按键（Button 1–16），`BluetoothHidGamepad` 中定义了 10 个按键位：A、B、X、Y、LB、RB、LT、RT、BACK、START
  - 操控界面当前使用 X、A、B、MENU（映射为 START）四个按键，其余按键位已定义但未在 UI 暴露
  - D-Pad 十字方向键（Hat Switch，0–7 八个方向 + Null 空态）
  - 双轴摇杆（X / Y，范围 -127 ~ +127）
- **多种操控界面**：
  - **测试界面**：查看连接状态、实时重力传感器数据，连接/断开已配对设备、重置按键
  - **滑动按键界面**：通过手指滑动区域触发 Hat 左/右、B、MENU、X、A 按键
  - **重力摇杆界面**：按住“按下启用”区域后，利用手机重力传感器控制摇杆 XY 轴（带死区与角度映射），支持触摸区域按键
  - **滑块摇杆界面**：屏幕滑动控制左摇杆 X 轴（带死区），支持触摸区域按键
- **125Hz 高频轮询上报**：每个操控界面有独立的轮询循环（`delay(8)`），确保手柄操作响应流畅
- **前台服务**：连接成功后自动启动 `GameControllerService`，显示常驻通知，Activity 退到后台仍保持蓝牙连接
- **多设备连接**：支持从已配对蓝牙设备列表中选择目标设备连接、断开
- **Material 3 现代 UI**：基于 Jetpack Compose 构建

## 工作原理

```
┌─────────────────────────────┐
│         操作界面              │
│  (滑动按键 / 重力摇杆 / 滑块)  │
└─────────────┬───────────────┘
              │ 写入内部状态寄存器
              ▼
┌─────────────────────────────┐
│  BluetoothHidGamepad        │
│  按键状态 / Hat / 摇杆 XY    │
└─────────────┬───────────────┘
              │ sendReport() @ 125Hz
              ▼
┌─────────────────────────────┐
│  BluetoothHidManager        │
│  HID Device 注册与连接管理   │
└─────────────┬───────────────┘
              │ BluetoothHidDevice.sendReport()
              ▼
┌─────────────────────────────┐
│      目标设备 (PC/电视等)     │
│     HID 手柄协议解析          │
└─────────────────────────────┘
```

核心流程：

1. 应用启动后申请必要权限，通过 `BluetoothAdapter.getProfileProxy()` 获取 `BluetoothHidDevice` 代理
2. 以 `GamepadReportDescriptor`（HID Report Descriptor）注册 SDP 服务，使手机在蓝牙列表中以手柄形态出现
3. 从已配对设备中选择目标并建立 HID 连接
4. 连接成功后自动启动前台服务 `GameControllerService`，显示常驻通知，保证后台持续运行
5. 各操作界面将触摸/传感器输入写入 `BluetoothHidGamepad` 的状态寄存器，由各自 125Hz 轮询循环调用 `sendReport()` 持续上报

HID 输入报告（Report ID 1）共 5 字节：按键状态 2 字节 + Hat Switch 1 字节 + 左摇杆 X/Y 各 1 字节。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose（Material 3） |
| 架构 | 单 Activity + 多 Composable 界面 + 前台服务 |
| 蓝牙 | Android `BluetoothHidDevice` API（HID Profile） |
| 传感器 | `Sensor.TYPE_GRAVITY`（回退 `TYPE_ACCELEROMETER`） |
| 构建 | Gradle + AGP |

## 环境要求

- **Android Studio**（建议最新稳定版）
- **JDK 17+**
- **构建配置**：
  - `minSdk = 30`（Android 11）
  - `targetSdk / compileSdk = 37`
  - AGP `9.3.0`，Kotlin `2.2.10`，Compose BOM `2026.02.01`
  - 当前版本：`versionName 1.34`（`versionCode 35`）

## 构建与安装

```bash
# 使用 Gradle Wrapper 构建 Debug 包
./gradlew assembleDebug

# 或直接安装到已连接设备
./gradlew installDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。仓库中另附有已构建好的 Release 安装包 `app/release/app-release.apk`（1.34）。

也可以直接用 Android Studio 打开项目根目录，点击 Run 运行。

## 使用方法

### 第一步：蓝牙配对

先在手机系统设置中，将手机与目标设备（PC、智能电视等支持蓝牙 HID 手柄的设备）完成蓝牙配对。

### 第二步：授予权限并连接

1. 打开应用，首次运行会请求蓝牙相关权限，请全部允许（Android 12+ 需要 `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE`）
2. 点击 **“连接已配对设备”**，在弹出的列表中选择目标设备
3. 连接成功后状态显示为 **“已连接”**，此时目标设备应能识别到名为 `GameController` 的手柄，同时应用会启动前台服务并显示常驻通知

> 提示：HID 连接方向是从手机主动发起连接（Outgoing），因此在目标设备上可能需要关闭“作为主机主动连接手机手柄”的选项，避免连接冲突。

### 第三步：选择操控模式

- **滑动按键界面**：手指在对应色块区域滑动即触发按键（Hat Left/Right、B、MENU、X、A）
- **重力摇杆界面**：先按下顶部 **“按下启用”** 区域开启重力控制，倾斜手机即可控制摇杆 XY 轴；中部/下部区域为 B、MENU、X、A 按键
- **滑块摇杆界面**：在上方滑块区域上下滑动控制左摇杆 X 轴（中部为死区），下方为按键区
- **测试界面**：查看实时重力传感器数值、连接状态，可进行连接/断开与重置操作

## 项目结构

```
GameController/
├── app/
│   ├── release/app-release.apk            # 已构建的 Release 安装包（1.34）
│   └── src/main/
│       ├── java/com/tools/gamecontroller/
│       │   ├── MainActivity.kt            # 入口 Activity：权限申请、界面切换、测试界面
│       │   │                              #  + SwipePadScreen（滑动按键界面）
│       │   ├── BluetoothHidManager.kt     # HID 设备代理获取、SDP 注册、连接/断开管理
│       │   ├── BluetoothHidGamepad.kt     # 手柄状态寄存器与 HID 报告发送
│       │   ├── GamepadReportDescriptor.kt # HID Report Descriptor（16按键+Hat+双轴摇杆）
│       │   ├── GameControllerService.kt   # 前台服务：常驻通知，保持后台连接
│       │   ├── GravityPadScreen.kt        # 重力摇杆界面
│       │   ├── SliderPadScreen.kt         # 滑块摇杆界面
│       │   └── ui/theme/                  # Compose 主题
│       ├── AndroidManifest.xml            # 权限与组件声明
│       └── res/                           # 图标、字符串、主题等资源
├── build.gradle.kts                       # 顶层构建脚本
├── gradle/libs.versions.toml              # 依赖版本目录
└── settings.gradle.kts
```

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `BLUETOOTH_SCAN` | Android 12+ 扫描蓝牙设备 |
| `BLUETOOTH_CONNECT` | Android 12+ 连接已配对设备 |
| `BLUETOOTH_ADVERTISE` | 注册 HID 设备广播 |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | 旧版本系统基础蓝牙权限 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Android 11 及以下蓝牙扫描必需（`maxSdkVersion=30`） |
| `FOREGROUND_SERVICE` | 启动前台服务 |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | 前台服务类型声明（Android 12+） |
| `POST_NOTIFICATIONS` | Android 13+ 显示常驻通知所需 |
| `VIBRATE` | 预留的震动反馈能力（当前未启用） |

## 已知限制与注意事项

- 手机 HID 连接为 **Outgoing 模式**（手机作为 HID Host 发起连接），部分设备默认只接受 Incoming 连接，需在目标设备侧关闭相关选项
- 重力摇杆使用 `asin` 角度映射（死区 ±1°，满量程 ±20°），倾斜角度超出范围会被截断；启用区域必须持续按住，松手立即停用
- `HAT_CENTER`（值 8）用于 D-Pad 复位，部分游戏的 Hat 输入逻辑对复位值敏感，属预期行为
- 当前版本未实现摇杆归中平滑过渡，快速释放触控时摇杆会立即回到 0 位
- Android 13+ 的通知权限 `POST_NOTIFICATIONS` 已在清单声明但尚未在运行时请求，常驻通知可能不显示（不影响前台服务运行与蓝牙连接）
- 操控界面仅使用 X、A、B、MENU（START）四个按键位，Y、LB、RB、LT、RT、BACK 等其余按键位已在 `BluetoothHidGamepad` 中定义但未在 UI 暴露

## License

本项目基于 [MIT License](LICENSE) 开源。

```
MIT License

Copyright (c) 2026 PasserbyHua

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
