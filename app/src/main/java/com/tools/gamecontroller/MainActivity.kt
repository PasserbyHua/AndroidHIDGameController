package com.tools.gamecontroller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tools.gamecontroller.ui.theme.GameControllerTheme

class MainActivity : ComponentActivity() {
    // 显式指定类型，避免 by lazy 推断失败
    private val bluetoothManager: BluetoothHidManager by lazy { BluetoothHidManager(this) }
    // 在类中添加一个请求权限的状态，避免重复弹窗
    private var isRequestingPermission = false
    // 服务是否运行的标志
    private var isServiceRunning = false
    private var gameControllerService: GameControllerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GameControllerService.ServiceBinder
            gameControllerService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    // 蓝牙 HID 连接状态监听：连接成功时启动前台服务保持连接，断开时停止
    private val connectionListener = object : BluetoothHidManager.ConnectionListener {
        override fun onConnected() {
            startGameControllerService()
            Toast.makeText(this@MainActivity, "已连接设备", Toast.LENGTH_SHORT).show()
        }

        override fun onDisconnected() {
            if (isServiceRunning) {
                stopGameControllerService()
            }
            Toast.makeText(this@MainActivity, "设备已断开", Toast.LENGTH_SHORT).show()
        }
    }

    // 处理权限请求结果
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            isRequestingPermission = false
            // 检查蓝牙相关权限是否都已授权
            val allGranted = result.values.all { it }
            if (!allGranted) {
                Toast.makeText(this, "需要蓝牙权限才能使用游戏手柄功能", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            initBluetooth()
        }

    // 蓝牙开启结果
    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (bluetoothAdapter()?.isEnabled != true) {
                Toast.makeText(this, "蓝牙未开启，无法使用手柄功能", Toast.LENGTH_LONG).show()
            } else {
                bluetoothManager.init()
            }
        }

    // 获取蓝牙适配器（避免使用已废弃的 getDefaultAdapter）
    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查所有必要的权限
        checkAndRequestPermissions()
        initBluetooth()
        bluetoothManager.setConnectionListener(connectionListener)

        setContent {
            GameControllerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FullPadScreen(manager = bluetoothManager)
                }
            }
        }
    }

    // 检查权限状态：当所有权限都已被授予时返回 true
    private fun checkPermissionStatus(): Boolean {
        // 基本的蓝牙权限
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
        // Android 12 以下还需要位置权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // 检查所有权限
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 检查并请求所需权限
    private fun checkAndRequestPermissions() {
        if (isRequestingPermission) return
        if (checkPermissionStatus()) return

        val permissionsToRequest = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // 过滤已授权的权限
        val needed = permissionsToRequest.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) {
            isRequestingPermission = true
            permissionLauncher.launch(needed)
        } else {
            isRequestingPermission = false
        }
    }

    // 初始化蓝牙
    private fun initBluetooth() {
        val adapter = bluetoothAdapter()
        if (adapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }
        if (!adapter.isEnabled) {
            // 请求开启蓝牙
            try {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(enableBtIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "开启蓝牙失败: ${e.message}")
            }
            return
        }
        // 蓝牙已开启，初始化并注册 HID App
        bluetoothManager.init()
    }

    // 启动前台服务保持连接
    private fun startGameControllerService() {
        val serviceIntent = Intent(this, GameControllerService::class.java)
        startForegroundServiceCompat(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        isServiceRunning = true
    }

    // 停止前台服务
    private fun stopGameControllerService() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        val serviceIntent = Intent(this, GameControllerService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
    }

    // 兼容 Android 8.0+ 的前台服务启动
    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 配置变化导致 Activity 销毁时不停止前台服务，避免旋转屏幕断开蓝牙连接
        if (!isChangingConfigurations) {
            stopGameControllerService()
        }
    }

    // 横竖屏切换时应用全屏
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyFullscreenForOrientation()
    }

    // 窗口焦点变化时应用全屏
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        applyFullscreenForOrientation()
    }

    // 根据屏幕方向应用全屏模式
    private fun applyFullscreenForOrientation() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
