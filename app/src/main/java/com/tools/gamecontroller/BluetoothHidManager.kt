package com.tools.gamecontroller

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothHidManager(val context: Context) {

    // 连接状态回调接口
    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
    }

    private var connectionListener: ConnectionListener? = null
    fun setConnectionListener(listener: ConnectionListener) {
        connectionListener = listener
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    var gamepad: BluetoothHidGamepad? = null
        private set

    // HID App 是否已完成注册（registerApp 成功后由系统回调更新）
    private var appRegistered = false
    // 是否正在执行“先 unregisterApp 再 registerApp”的旧 SDP 刷新流程
    private var reRegisterRequested = false
    // HID 服务/注册尚未就绪时的连接请求，就绪后自动执行
    private var pendingConnectAddress: String? = null
    // 描述符版本变化后的一次性“断开-重连”刷新流程
    private var descriptorRefreshPending = false
    private var descriptorRefreshDevice: BluetoothDevice? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val statePrefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
                appRegistered = false
                Log.d(TAG, "HID Device service connected")
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                appRegistered = false
                reRegisterRequested = false
                Log.d(TAG, "HID Device service disconnected")
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            appRegistered = registered
            Log.d(
                TAG,
                "App status: registered=$registered, device=${pluggedDevice?.name}, " +
                    "descriptorVersion=${GamepadReportDescriptor.DESCRIPTOR_VERSION}"
            )
            if (registered) {
                // HID App 已用当前描述符完成注册，执行等待中的连接请求
                pendingConnectAddress?.let { address ->
                    Log.d(TAG, "HID App 已就绪，执行等待中的连接: $address")
                    pendingConnectAddress = null
                    connect(address)
                }
            } else if (reRegisterRequested) {
                // 旧 SDP 记录已注销，用新描述符重新注册
                reRegisterRequested = false
                Log.d(TAG, "旧 HID App 已注销，使用新描述符重新注册")
                registerApp()
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "Connection state: ${device.name} -> $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    gamepad = BluetoothHidGamepad(hidDevice, device)
                    handleDescriptorRefreshIfNeeded(device)
                    // 通知 UI 更新
                    connectionListener?.onConnected()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val refreshDevice = descriptorRefreshDevice
                    connectedDevice = null
                    gamepad = null

                    if (descriptorRefreshPending && refreshDevice != null) {
                        // 这是为刷新目标设备缓存的旧描述符而主动发起的断开，
                        // 不通知 UI“已断开”，稍后自动重连。
                        val targetAddress = refreshDevice.address
                        Log.d(TAG, "描述符刷新：设备已断开，准备自动重连 $targetAddress")
                        mainHandler.postDelayed({
                            connect(targetAddress)
                        }, RECONNECT_DELAY_MS)
                    } else {
                        // 通知 UI 更新
                        connectionListener?.onDisconnected()
                    }
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

        if (hidDevice == null) {
            // 获取 HID 代理
            bluetoothAdapter!!.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } else if (!appRegistered) {
            // 已有代理但尚未注册时，确保使用当前描述符注册，防止旧 SDP 记录残留
            registerApp()
        }
    }

    private fun registerApp() {
        if (hidDevice == null) {
            Log.w(TAG, "registerApp: hidDevice 为 null，尚未初始化")
            return
        }
        if (!checkPermissions()) {
            Log.w(TAG, "registerApp: 权限不足")
            return
        }

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

        Log.d(
            TAG,
            "Register app result: $result, descriptorVersion=${GamepadReportDescriptor.DESCRIPTOR_VERSION}"
        )

        if (!result && !reRegisterRequested) {
            // registerApp 返回 false 通常表示系统里还残留着旧的 HID App 注册，
            // 此时 SDP 记录可能仍是旧版描述符（例如升级前的 5 字节报告）。
            // 必须先注销旧注册，等 onAppStatusChanged(registered=false) 后再重新注册。
            Log.w(TAG, "registerApp 返回 false，尝试先注销旧 HID App 再重新注册")
            reRegisterRequested = true
            val unregisterResult = hidDevice!!.unregisterApp()
            Log.d(TAG, "unregisterApp result: $unregisterResult")
            if (!unregisterResult) {
                reRegisterRequested = false
                Log.e(TAG, "unregisterApp 失败，旧 HID 描述符可能仍然生效")
            }
        }
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
            Log.w(TAG, "connect: HID 服务尚未就绪，已加入等待队列")
            pendingConnectAddress = deviceAddress
            init()
            return
        }

        if (!appRegistered) {
            Log.w(TAG, "connect: HID App 尚未注册，已加入等待队列")
            pendingConnectAddress = deviceAddress
            registerApp()
            return
        }

        pendingConnectAddress = null
        val result = hidDevice!!.connect(device)
        Log.d(TAG, "connect: 连接结果 = $result")
    }

    /**
     * 如果目标设备缓存了旧版 HID 报告描述符（例如升级前是 5 字节报告），
     * 即使手机侧已经按 9 字节发送，右摇杆对应的字节也会被目标设备丢弃。
     *
     * 这里在连接建立后检测“上次成功连接时使用的描述符版本”，
     * 版本不一致时自动执行一次“断开 -> 重连”，强制目标设备重新读取 SDP 中的描述符。
     */
    private fun handleDescriptorRefreshIfNeeded(device: BluetoothDevice) {
        if (descriptorRefreshPending && descriptorRefreshDevice?.address == device.address) {
            // 自动刷新后的重连成功，记录当前版本，避免重复刷新
            descriptorRefreshPending = false
            descriptorRefreshDevice = null
            statePrefs.edit()
                .putInt(KEY_CONNECTED_DESCRIPTOR_VERSION, GamepadReportDescriptor.DESCRIPTOR_VERSION)
                .apply()
            Log.d(TAG, "描述符刷新完成，右摇杆报告应已生效")
            return
        }

        val lastConnectedVersion = statePrefs.getInt(KEY_CONNECTED_DESCRIPTOR_VERSION, -1)
        if (lastConnectedVersion == GamepadReportDescriptor.DESCRIPTOR_VERSION) {
            return
        }

        val hid = hidDevice
        if (hid == null || !checkPermissions()) {
            Log.w(TAG, "需要刷新描述符，但 HID 服务/权限不可用")
            return
        }

        Log.w(
            TAG,
            "检测到 HID 描述符版本变化($lastConnectedVersion -> " +
                "${GamepadReportDescriptor.DESCRIPTOR_VERSION})，自动断开并重连一次"
        )
        descriptorRefreshPending = true
        descriptorRefreshDevice = device
        val result = hid.disconnect(device)
        Log.d(TAG, "描述符刷新断开结果: $result")
        if (!result) {
            descriptorRefreshPending = false
            descriptorRefreshDevice = null
        }
    }

    fun disconnect(): Boolean {
        val device = connectedDevice ?: run {
            Log.d(TAG, "disconnect: 当前没有已连接的设备")
            return false
        }
        // 权限检查：与 connect() 保持一致，Android 11 及以下无 BLUETOOTH_CONNECT 运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "disconnect: BLUETOOTH_CONNECT 权限未授予")
                return false
            }
        }
        val hid = hidDevice
        if (hid == null) {
            Log.e(TAG, "disconnect: hidDevice 为 null，尚未初始化")
            return false
        }
        val result = hid.disconnect(device)
        Log.d(TAG, "disconnect: 断开结果 = $result")
        if (result) {
            // 立即清理内部状态，UI 轮询会随之更新
            connectedDevice = null
            gamepad = null
            pendingConnectAddress = null
        }
        return result
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
        private const val PREFS_NAME = "hid_manager_state"
        private const val KEY_CONNECTED_DESCRIPTOR_VERSION = "connected_descriptor_version"
        private const val RECONNECT_DELAY_MS = 800L
    }
}
