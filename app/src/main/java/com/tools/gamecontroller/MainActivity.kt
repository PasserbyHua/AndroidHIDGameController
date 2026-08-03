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

class MainActivity : ComponentActivity() {

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
                    GamepadTestScreen(
                        manager = bluetoothManager,
                        context = this@MainActivity,
                        onRequestPermission = { checkAndRequestPermissions() }   // ← 改为单数
                    )
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
    onRequestPermission: () -> Unit   // 新增参数
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

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("蓝牙手柄模拟器", style = MaterialTheme.typography.titleLarge)

        // 连接按钮（使用新的逻辑）
        Button(
            onClick = {
                // 根据系统版本检查对应权限
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
                // 权限已授予，获取设备列表
                val devices = manager.getPairedDevices()
                if (devices.isEmpty()) {
                    Toast.makeText(context, "没有已配对的蓝牙设备，请先在系统设置中配对", Toast.LENGTH_LONG).show()
                    return@Button
                }
                // 显示设备列表对话框
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
        ) { Text("连接已配对设备") }

        Text("状态: $statusText", style = MaterialTheme.typography.bodyLarge)

        Divider()

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GamepadButton("A", manager.gamepad, BluetoothHidGamepad.BUTTON_A)
            GamepadButton("B", manager.gamepad, BluetoothHidGamepad.BUTTON_B)
            GamepadButton("X", manager.gamepad, BluetoothHidGamepad.BUTTON_X)
            GamepadButton("Y", manager.gamepad, BluetoothHidGamepad.BUTTON_Y)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GamepadButton("LB", manager.gamepad, BluetoothHidGamepad.BUTTON_LB)
            GamepadButton("RB", manager.gamepad, BluetoothHidGamepad.BUTTON_RB)
            GamepadButton("Start", manager.gamepad, BluetoothHidGamepad.BUTTON_START)
            GamepadButton("Select", manager.gamepad, BluetoothHidGamepad.BUTTON_SELECT)
        }

        var leftX by remember { mutableStateOf(0f) }
        var leftY by remember { mutableStateOf(0f) }
        var rightX by remember { mutableStateOf(0f) }

        Text("左摇杆 X")
        Slider(
            value = leftX,
            onValueChange = {
                leftX = it
                manager.gamepad?.setLeftStick((it * 127).toInt(), (leftY * 127).toInt())
            },
            valueRange = -1f..1f
        )
        Text("左摇杆 Y")
        Slider(
            value = leftY,
            onValueChange = {
                leftY = it
                manager.gamepad?.setLeftStick((leftX * 127).toInt(), (it * 127).toInt())
            },
            valueRange = -1f..1f
        )
        Text("右摇杆 X")
        Slider(
            value = rightX,
            onValueChange = {
                rightX = it
                manager.gamepad?.setRightStick((it * 127).toInt())
            },
            valueRange = -1f..1f
        )

        Button(onClick = {
            manager.gamepad?.reset()
            leftX = 0f
            leftY = 0f
            rightX = 0f
        }) {
            Text("重置")
        }
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
