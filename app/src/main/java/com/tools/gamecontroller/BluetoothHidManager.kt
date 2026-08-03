package com.tools.gamecontroller

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tools.gamecontroller.GamepadReportDescriptor

class BluetoothHidManager(val context: Context) {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    var gamepad: BluetoothHidGamepad? = null
        private set

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "蓝牙适配器获取失败")
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                Log.d(TAG, "HID Device service connected")
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                Log.d(TAG, "HID Device service disconnected")
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "App status: registered=$registered, device=${pluggedDevice?.name}")
            if (registered) {
                // 应用已注册，可以开始连接
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "Connection state: ${device.name} -> $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    gamepad = BluetoothHidGamepad(hidDevice, device)
                    // 通知 UI 更新（可以通过回调或 LiveData）
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    gamepad = null
                }
            }
        }
    }

    fun init() {
        if (!checkPermissions()) {
            // 权限不足，等待用户授予，UI 层面会处理
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            // 需要请求开启蓝牙，由 Activity 处理
            return
        }

        // 获取 HID 代理
        bluetoothAdapter!!.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private fun registerApp() {
        if (hidDevice == null) return
        if (!checkPermissions()) return

        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            GamepadReportDescriptor.DEVICE_NAME,
            GamepadReportDescriptor.DESCRIPTION,
            GamepadReportDescriptor.PROVIDER,
            BluetoothHidDevice.SUBCLASS2_GAMEPAD, // 注意：手柄子类
            GamepadReportDescriptor.DESCRIPTOR
        )

        val result = hidDevice!!.registerApp(
            sdpSettings,
            null,
            null,
            context.mainExecutor,
            callback
        )

        Log.d(TAG, "Register app result: $result")
    }

    fun connect(deviceAddress: String) {
        Log.d(TAG, "connect: 尝试连接设备 $deviceAddress")
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "connect: BLUETOOTH_CONNECT 权限未授予")
                return
            }
        } else {
            // Android 11 及以下：需要位置权限（但 connect 本身可能需要 BLUETOOTH 权限，已默认授予）
            // 但为了保险，检查位置权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "connect: ACCESS_FINE_LOCATION 权限未授予")
                return
            }
        }
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.e(TAG, "connect: 无法获取远程设备")
            return
        }
        if (hidDevice == null) {
            Log.e(TAG, "connect: hidDevice 为 null，尚未初始化")
            return
        }
        val result = hidDevice!!.connect(device)
        Log.d(TAG, "connect: 连接结果 = $result")
    }

    fun disconnect() {
        connectedDevice?.let {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                hidDevice?.disconnect(it)
            }
        }
    }

    fun isConnected(): Boolean = connectedDevice != null && gamepad != null

    private fun checkPermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_ADMIN)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.isEmpty()
    }

    // BluetoothHidManager.kt 中添加
    fun getPairedDevices(): List<BluetoothDevice> {
        Log.d(TAG, "getPairedDevices: 开始获取已配对设备")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "getPairedDevices: BLUETOOTH_CONNECT 权限未授予")
                return emptyList()
            }
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "getPairedDevices: ACCESS_FINE_LOCATION 权限未授予")
                return emptyList()
            }
        }
        if (bluetoothAdapter == null) {
            Log.e(TAG, "getPairedDevices: bluetoothAdapter 为 null")
            return emptyList()
        }
        val bonded = bluetoothAdapter!!.bondedDevices
        Log.d(TAG, "getPairedDevices: 已配对设备数量 = ${bonded.size}")
        return bonded.toList()
    }

    companion object {
        private const val TAG = "BluetoothHidManager"
    }
}