# GameController — 蓝牙手柄模拟器

一个基于 **Android 蓝牙 HID（Human Interface Device）协议** 的安卓应用，可以将手机模拟成无线游戏手柄，通过蓝牙连接到 PC、智能电视、游戏主机等设备并控制游戏。

应用启动即进入**横屏全屏**的完整手柄界面，数据以 125Hz 频率实时上报。

## 功能特性

- **蓝牙 HID 手柄模拟**：将手机注册为 Bluetooth HID 设备（子类：Gamepad），其他设备可像识别普通手柄一样识别它
- **手柄键位**：
  - HID 描述符支持 16 个按键（Button 1–16），`BluetoothHidGamepad` 中定义了 12 个按键位：A、B、X、Y、LB、RB、LT、RT、BACK、START、L3（左摇杆按下）、R3（右摇杆按下）
  - D-Pad 十字方向键（Hat Switch，0–7 八个方向 + Null 空态，支持斜方向组合）
  - 左右双摇杆（左 X/Y，右 Z/Rz，范围 -127 ~ +127）
  - **线性扳机**：左右扳机（LT/RT）按压时同步上报 Brake / Accelerator 线性轴（0–255），支持压感识别
- **完整手柄界面**：ABXY + D-Pad + LB/LT/RB/RT + START/BACK + L3/R3 + 左右双摇杆；按键位置可拖拽自定义并持久保存；支持滑动触发按下、滑出抬起、多指同时操作
- **蓝牙配对集成**：工具栏内置"连接/断开"按钮与已配对设备选择弹窗，无需离开手柄界面即可管理连接
- **125Hz 高频轮询上报**：轮询循环（`delay(8)`）持续上报，确保手柄操作响应流畅
- **前台服务**：连接成功后自动启动 `GameControllerService`，显示常驻通知，Activity 退到后台仍保持蓝牙连接
- **启动即横屏全屏**：强制横屏 + 沉浸式全屏（隐藏状态栏/导航栏），刘海屏自适应
- **Material 3 现代 UI**：基于 Jetpack Compose 构建

## 工作原理

```
┌─────────────────────────────┐
│       完整手柄界面            │
│  (FullPadScreen + 配对连接)  │
└─────────────┬───────────────┘
              │ 写入内部状态寄存器
              ▼
┌─────────────────────────────┐
│  BluetoothHidGamepad        │
│  按键 / Hat / 摇杆 / 扳机    │
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
5. 手柄界面将触摸输入写入 `BluetoothHidGamepad` 的状态寄存器，由 125Hz 轮询循环调用 `sendReport()` 持续上报

HID 输入报告（Report ID 1）共 9 字节：

| 字节 | 字段 | 范围 |
| --- | --- | --- |
| 0–1 | 16 个按键（Button 1–16） | 每位 0/1 |
| 2 | D-Pad 十字键（Hat Switch） | 0–7 / 空态 |
| 3–4 | 左摇杆 X/Y | -127 ~ +127 |
| 5–6 | 右摇杆 Z/Rz | -127 ~ +127 |
| 7 | 左扳机 Brake | 0–255 |
| 8 | 右扳机 Accelerator | 0–255 |

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose（Material 3） |
| 架构 | 单 Activity + 单 Composable 界面 + 前台服务 |
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
2. 点击工具栏的 **“连接”** 按钮，在弹出的已配对设备列表中选择目标设备
3. 连接成功后状态显示为 **“已连接”**，此时目标设备应能识别到名为 `GameController` 的手柄，同时应用会启动前台服务并显示常驻通知

> 提示：HID 连接方向是从手机主动发起连接（Outgoing），因此在目标设备上可能需要关闭“作为主机主动连接手机手柄”的选项，避免连接冲突。

### 第三步：使用完整手柄界面

应用启动后直接进入**完整手柄界面**，包含 ABXY、D-Pad 上下左右、LB/LT/RB/RT、START/BACK、L3/R3 与左右双摇杆。操作方式：

- **按键**：手指滑入按键即按下、滑出/抬起即释放
- **摇杆**：抓住后可在任意方向拖动，滑出外圈仍持续控制，直到抬起归零
- **扳机**：按下 LT/RT 时同步上报 Brake / Accelerator 线性轴（0–255 满压感），支持线性压感的游戏/模拟器
- **自定义布局**：点击 **“编辑布局”** 进入编辑模式，可拖拽任意按键/摇杆调整位置（自动保存，可“恢复默认”）；也可用“缩小/放大”整体调整控件尺寸
- **隐藏工具栏**：点击“隐藏”后工具栏收起，屏幕顶部中间的小按钮可随时唤回

## 项目结构

```
GameController/
├── app/
│   ├── release/app-release.apk            # 已构建的 Release 安装包（1.34）
│   └── src/main/
│       ├── java/com/tools/gamecontroller/
│       │   ├── MainActivity.kt            # 入口 Activity：权限申请、连接监听、横屏全屏
│       │   ├── BluetoothHidManager.kt     # HID 设备代理获取、SDP 注册、连接/断开管理
│       │   ├── BluetoothHidGamepad.kt     # 手柄状态寄存器与 HID 报告发送
│       │   ├── GamepadReportDescriptor.kt # HID Report Descriptor（16按键+Hat+双摇杆+扳机轴）
│       │   ├── GameControllerService.kt   # 前台服务：常驻通知，保持后台连接
│       │   ├── FullPadScreen.kt           # 完整手柄界面（含配对连接弹窗、自定义布局）
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
- `HAT_CENTER`（值 8）用于 D-Pad 复位，部分游戏的 Hat 输入逻辑对复位值敏感，属预期行为
- 当前版本未实现摇杆归中平滑过渡，快速释放触控时摇杆会立即回到 0 位
- Android 13+ 的通知权限 `POST_NOTIFICATIONS` 已在清单声明但尚未在运行时请求，常驻通知可能不显示（不影响前台服务运行与蓝牙连接）
- 完整手柄界面的自定义布局保存在本地 `SharedPreferences`（归一化坐标，自动适配屏幕尺寸）；HID 描述符版本变化后应用会自动断开并重连一次；若目标设备仍缓存旧版描述符，请删除配对后重新配对
- L3/R3 按键掩码（0x2000/0x4000）与被控端映射相关，个别设备可能需要调整

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
