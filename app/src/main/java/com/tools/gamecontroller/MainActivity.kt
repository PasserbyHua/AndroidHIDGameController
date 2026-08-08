package com.tools.gamecontroller

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tools.gamecontroller.BluetoothHidManager
import com.tools.gamecontroller.ui.theme.GameControllerTheme
import kotlinx.coroutines.delay
import android.provider.Settings
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.TextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.PointerId
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf


class MainActivity : ComponentActivity() {

    private var showSwipePad by mutableStateOf(false)
    // 显式指定类型，避免 by lazy 推断失败
    private val bluetoothManager: BluetoothHidManager by lazy { BluetoothHidManager(this) }
    // 在类中添加一个请求权限的状态，避免重复弹窗
    private var isRequestingPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        isRequestingPermission = false
        Log.d("Permission", "Results: $results")
        if (results.values.all { it }) {
            initBluetooth()
        } else {
            Toast.makeText(this, "需要必要的权限才能运行", Toast.LENGTH_LONG).show()
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            initBluetooth()
        } else {
            Toast.makeText(this, "需要开启蓝牙", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            GameControllerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSwipePad) {
                        SwipePadScreen(
                            manager = bluetoothManager,
                            onBack = { showSwipePad = false }
                        )
                    } else {
                        GamepadTestScreen(
                            manager = bluetoothManager,
                            context = this@MainActivity,
                            onRequestPermission = { checkAndRequestPermissions() },
                            onSwitchToSwipePad = { showSwipePad = true }
                        )
                    }
                }
            }
        }
        checkPermissionStatus()

        Log.d("DeviceInfo", "SDK_INT = ${Build.VERSION.SDK_INT}")
        checkAndRequestPermissions()
    }

    private fun checkPermissionStatus() {
        val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        Log.d("Permission", "BLUETOOTH_CONNECT granted: $hasConnect")
    }

    fun checkAndRequestPermissions() {
        if (isRequestingPermission) return
        val needed = mutableListOf<String>()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.BLUETOOTH_SCAN)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.BLUETOOTH_CONNECT)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            else -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (needed.isNotEmpty()) {
            // 检查是否被永久拒绝（对于所有请求的权限）
            val shouldShow = needed.map { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
            if (shouldShow.all { !it }) {
                // 所有权限都被永久拒绝，引导去设置
                Toast.makeText(this, "请在设置中手动开启必要权限", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                isRequestingPermission = true
                permissionLauncher.launch(needed.toTypedArray())
            }
        } else {
            initBluetooth()
        }
    }

    private fun initBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }
        if (!adapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        bluetoothManager.init()
    }
}

@Composable
fun GamepadTestScreen(
    manager: BluetoothHidManager,
    context: Context,
    onRequestPermission: () -> Unit,
    onSwitchToSwipePad: () -> Unit   // 新增
) {
    var isConnected by remember { mutableStateOf(manager.isConnected()) }
    var statusText by remember { mutableStateOf(if (isConnected) "已连接" else "未连接") }

    LaunchedEffect(Unit) {
        while (true) {
            isConnected = manager.isConnected()
            statusText = if (isConnected) "已连接" else "未连接"
            delay(500)
        }
    }

    var inputMask by remember { mutableStateOf("0x01") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("蓝牙手柄模拟器", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onSwitchToSwipePad) {
            Text("切换到滑动按键界面")
        }
        // 连接按钮
        Button(
            onClick = {
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                }
                if (!hasPermission) {
                    onRequestPermission()
                    Toast.makeText(context, "请授予必要权限", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val devices = manager.getPairedDevices()
                if (devices.isEmpty()) {
                    Toast.makeText(context, "没有已配对的蓝牙设备，请先在系统设置中配对", Toast.LENGTH_LONG).show()
                    return@Button
                }
                val deviceNames = devices.map { it.name ?: "未知设备" }.toTypedArray()
                AlertDialog.Builder(context)
                    .setTitle("选择要连接的设备")
                    .setItems(deviceNames) { _, which ->
                        val selectedDevice = devices[which]
                        manager.connect(selectedDevice.address)
                        Toast.makeText(context, "正在连接 ${selectedDevice.name}...", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        ) {
            Text("连接已配对设备")
        }
        // 断开连接按钮
        Button(
            onClick = {
                if (!manager.isConnected()) {
                    Toast.makeText(context, "当前没有已连接的设备", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val success = manager.disconnect()
                if (success) {
                    Toast.makeText(context, "已断开连接", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "断开失败，请查看日志", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Text("断开已连接设备")
        }

        Text("状态: $statusText", style = MaterialTheme.typography.bodyLarge)

        Divider()

        var hatValue by remember { mutableStateOf("0") }

        Text("帽子开关值 (0-15, 8或15可能为复位)", style = MaterialTheme.typography.bodySmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = hatValue,
                onValueChange = { hatValue = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("方向值 (0-15)") }
            )
            Button(
                onClick = {
                    val gamepad = manager.gamepad
                    if (gamepad == null) {
                        Toast.makeText(context, "请先连接设备", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val value = hatValue.toIntOrNull()
                    if (value == null || value !in 0..15) {
                        Toast.makeText(context, "请输入 0-15 之间的整数", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    // 直接调用 setHatSwitch，但需要修改该方法允许 0-15
                    gamepad.setHatSwitch(value)
                    Toast.makeText(context, "发送方向值: $value", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("发送方向")
            }
        }

        // 输入框和发送按钮
        Text("输入按键掩码 (十六进制, 如 0x01, 0x02)", style = MaterialTheme.typography.bodySmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputMask,
                onValueChange = { inputMask = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("掩码 (十六进制)") }
            )
            Button(
                onClick = {
                    val gamepad = manager.gamepad
                    if (gamepad == null) {
                        Toast.makeText(context, "请先连接设备", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val mask = try {
                        val trimmed = inputMask.trim()
                        if (trimmed.startsWith("0x", ignoreCase = true)) {
                            trimmed.substring(2).toInt(16)
                        } else {
                            trimmed.toInt(16) // 默认按十六进制解析
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "无效的掩码值，请使用十六进制", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    // 发送按下
                    gamepad.setButton(mask, true)
                    // 延迟 100ms 后释放
                    Handler(Looper.getMainLooper()).postDelayed({
                        gamepad.setButton(mask, false)
                    }, 1000)
                    Toast.makeText(context, "发送掩码 0x${mask.toString(16)}", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("发送按键")
            }
        }

        // 显示当前按钮状态（方便对照）
        val currentState = manager.gamepad?.getButtonState() ?: 0
        Text("当前按钮状态: 0x${currentState.toString(16)}", style = MaterialTheme.typography.bodySmall)

        // 重置按钮
        Button(onClick = {
            manager.gamepad?.reset()
            Toast.makeText(context, "已重置所有按键", Toast.LENGTH_SHORT).show()
        }) {
            Text("重置")
        }
    }
}
@Composable
fun SwipePadScreen(
    manager: BluetoothHidManager,
    onBack: () -> Unit
) {
    val gamepad = manager.gamepad
    val TAG = "HID_DEBUG"

    // --- 1. 区域定义 ---
    val REGION_HAT_LEFT = 0
    val REGION_HAT_RIGHT = 1
    val REGION_BUTTON_X = 2
    val REGION_BUTTON_A = 3
    val REGION_BUTTON_B = 4 // B 键
    val REGION_BUTTON_MENU = 5 // MENU 键

    // --- 2. 输入寄存器 ---
    var visualRegions by remember { mutableStateOf(setOf<Int>()) }

    // --- 3. 轮询主循环 (125Hz) ---
    LaunchedEffect(Unit) {
        Log.d(TAG, "启动轮询循环: 125Hz")
        while (true) {
            val currentRegions = visualRegions

            // [B. 逻辑计算]
            var btnState = 0
            if (currentRegions.contains(REGION_BUTTON_X)) btnState = btnState or BluetoothHidGamepad.BUTTON_X
            if (currentRegions.contains(REGION_BUTTON_A)) btnState = btnState or BluetoothHidGamepad.BUTTON_A
            if (currentRegions.contains(REGION_BUTTON_B)) btnState = btnState or BluetoothHidGamepad.BUTTON_B
            if (currentRegions.contains(REGION_BUTTON_MENU)) btnState = btnState or BluetoothHidGamepad.BUTTON_START

            val hasLeft = currentRegions.contains(REGION_HAT_LEFT)
            val hasRight = currentRegions.contains(REGION_HAT_RIGHT)

            val hatValue = when {
                hasLeft && hasRight -> 8
                hasLeft -> 6
                hasRight -> 2
                else -> 8
            }

            gamepad?.setState(btnState, hatValue)
            gamepad?.sendReport()
            delay(8)
        }
    }

    // --- 4. 触摸事件处理 ---
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val activePointers = mutableMapOf<PointerId, Int>()

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val currentTouches = mutableMapOf<PointerId, Int>()

                        event.changes.forEach { change ->
                            if (change.pressed) {
                                // 根据新的布局计算区域
                                // 布局比例：上部Hat占0.4，中部B占0.2，下部X/A占0.4
                                val h = size.height
                                val w = size.width

                                val region = when {
                                    // 上部 2/5 (0.0 - 0.4)：Hat区域
                                    change.position.y < h * 0.2f -> REGION_HAT_LEFT      // 上半部分
                                    change.position.y < h * 0.4f -> REGION_HAT_RIGHT     // 下半部分

                                    // 中部 1/5 (0.4 - 0.6)：B / MENU 键区域
                                    change.position.y < h * 0.6f -> {
                                        if (change.position.x < w / 2f) REGION_BUTTON_B else REGION_BUTTON_MENU
                                    }

                                    // 下部 2/5 (0.6 - 1.0)：X/A 键区域
                                    change.position.x < w / 2f -> REGION_BUTTON_X
                                    else -> REGION_BUTTON_A
                                }
                                currentTouches[change.id] = region
                            }
                            change.consume()
                        }

                        val newRegions = currentTouches.values.toSet()
                        if (newRegions != activePointers.values.toSet()) {
                            activePointers.clear()
                            activePointers.putAll(currentTouches)
                            visualRegions = newRegions
                        }
                    }
                }
            }
    ) {
        // --- UI 绘制 ---
        // 计算各部分高度
        val hatHeight = maxHeight * 0.4f  // 上部 2/5
        val bHeight = maxHeight * 0.2f    // 中部 1/5
        val xaHeight = maxHeight * 0.4f   // 下部 2/5

        // 1. 上部：帽子区域 (Left 和 Right 平分高度)
        Box(modifier = Modifier.fillMaxWidth().height(hatHeight)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(hatHeight / 2)
                    .background(if (visualRegions.contains(REGION_HAT_LEFT)) Color(0xFF4CAF50) else Color(0xFF757575))
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) { Text("Hat Left", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }

            Box(
                modifier = Modifier.fillMaxWidth().height(hatHeight / 2).offset(y = hatHeight / 2)
                    .background(if (visualRegions.contains(REGION_HAT_RIGHT)) Color(0xFF4CAF50) else Color(0xFF757575))
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) { Text("Hat Right", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }

        // 2. 中部：B 和 MENU 键区域
        Row(
            modifier = Modifier.fillMaxWidth().height(bHeight).offset(y = hatHeight)
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(if (visualRegions.contains(REGION_BUTTON_B)) Color(0xFF4CAF50) else Color(0xFF757575))
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) { Text("B", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold) }

            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(if (visualRegions.contains(REGION_BUTTON_MENU)) Color(0xFF4CAF50) else Color(0xFF757575))
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) { Text("MENU", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
        }

        // 3. 下部：X 和 A 键区域
        Row(
            modifier = Modifier.fillMaxWidth().height(xaHeight).offset(y = hatHeight + bHeight)
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(if (visualRegions.contains(REGION_BUTTON_X)) Color(0xFF4CAF50) else Color(0xFF757575))
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) { Text("X", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold) }

            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(if (visualRegions.contains(REGION_BUTTON_A)) Color(0xFF4CAF50) else Color(0xFF757575))
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) { Text("A", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
        }

        Button(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) { Text("返回") }
    }
}

@Composable
fun GamepadButton(label: String, gamepad: BluetoothHidGamepad?, buttonMask: Int) {
    var pressed by remember { mutableStateOf(false) }
    Button(
        onClick = {
            pressed = !pressed
            gamepad?.setButton(buttonMask, pressed)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(label)
    }
}
