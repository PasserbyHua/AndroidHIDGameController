package com.tools.gamecontroller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DecimalFormat


@Composable
fun GravityPadScreen(
    manager: BluetoothHidManager,
    onBack: () -> Unit
) {
    val gamepad = manager.gamepad
    val TAG = "GravityPad"

    // ---------- 重力传感器 ----------
    val context = androidx.compose.ui.platform.LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gravitySensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    var gravityValues by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }
    val isSensorAvailable = remember { gravitySensor != null }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let { gravityValues = it.values.clone() }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        gravitySensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // ---------- 区域常量 ----------
    val REGION_GRAVITY_ENABLE = 0
    val REGION_BUTTON_B = 1
    val REGION_BUTTON_MENU = 2
    val REGION_BUTTON_X = 3
    val REGION_BUTTON_A = 4

    // ---------- 状态 ----------
    var visualRegions by remember { mutableStateOf(setOf<Int>()) }
    var gravityEnabled by remember { mutableStateOf(false) }

    // 计算当前摇杆 X 值（映射到 ±5.0）
    val stickXValue by remember {
        derivedStateOf {
            if (!gravityEnabled) 0
            else {
                val rawY = gravityValues[1]
                val clamped = rawY.coerceIn(-5.0f, 5.0f)
                (clamped / 5.0f * 127).toInt()
            }
        }
    }

    // 显示格式化小数
    val df = remember { DecimalFormat("0.00") }

    // ---------- 触摸事件处理 ----------
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val activePointers = mutableMapOf<PointerId, Int>()

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val currentTouches = mutableMapOf<PointerId, Int>()
                        val h = size.height
                        val w = size.width

                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val region = when {
                                    // 上半部分 (0 ~ 40% 高度)
                                    change.position.y < h * 0.4f -> {
                                        // 如果在上半部分的下半（即 20%~40% 高度），则视为启用区域
                                        if (change.position.y >= h * 0.2f) {
                                            REGION_GRAVITY_ENABLE
                                        } else {
                                            // 上半部分的上半（显示区域）不触发任何动作
                                            -1
                                        }
                                    }
                                    // 中部 (40% ~ 60%)：B / MENU
                                    change.position.y < h * 0.6f -> {
                                        if (change.position.x < w / 2f) REGION_BUTTON_B
                                        else REGION_BUTTON_MENU
                                    }
                                    // 下部 (60% ~ 100%)：X / A
                                    else -> {
                                        if (change.position.x < w / 2f) REGION_BUTTON_X
                                        else REGION_BUTTON_A
                                    }
                                }
                                if (region != -1) {
                                    currentTouches[change.id] = region
                                }
                            }
                            change.consume()
                        }

                        // 更新 visualRegions（用于 UI 高亮）
                        val newRegions = currentTouches.values.toSet()
                        if (newRegions != activePointers.values.toSet()) {
                            activePointers.clear()
                            activePointers.putAll(currentTouches)
                            visualRegions = newRegions
                        }

                        // 判断是否有触摸在启用区域
                        val hasEnable = activePointers.values.any { it == REGION_GRAVITY_ENABLE }
                        gravityEnabled = hasEnable
                    }
                }
            }
    ) {
        // ---------- 布局尺寸 ----------
        val hatHeight = maxHeight * 0.4f
        val bHeight = maxHeight * 0.2f
        val xaHeight = maxHeight * 0.4f

        // ---------- 上半部分：显示值（上） + 启用按钮（下） ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(hatHeight)
        ) {
            // 上半部分的上半：显示摇杆X值
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF757575))
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "摇杆 X",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (gravityEnabled) "$stickXValue" else "0",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 上半部分的下半：启用/禁用按钮（触摸区域）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        if (visualRegions.contains(REGION_GRAVITY_ENABLE) || gravityEnabled)
                            Color(0xFF4CAF50) else Color(0xFF757575)
                    )
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (gravityEnabled) "启用中" else "按下启用",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ---------- 中部：B / MENU ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(bHeight)
                .offset(y = hatHeight)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (visualRegions.contains(REGION_BUTTON_B)) Color(0xFF4CAF50)
                        else Color(0xFF757575)
                    )
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("B", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (visualRegions.contains(REGION_BUTTON_MENU)) Color(0xFF4CAF50)
                        else Color(0xFF757575)
                    )
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("MENU", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ---------- 下部：X / A ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(xaHeight)
                .offset(y = hatHeight + bHeight)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (visualRegions.contains(REGION_BUTTON_X)) Color(0xFF4CAF50)
                        else Color(0xFF757575)
                    )
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("X", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (visualRegions.contains(REGION_BUTTON_A)) Color(0xFF4CAF50)
                        else Color(0xFF757575)
                    )
                    .border(1.dp, Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ---------- 轮询发送循环 (125Hz) ----------
        LaunchedEffect(Unit) {
            Log.d(TAG, "启动轮询循环: 125Hz")
            while (true) {
                val gamepad = manager.gamepad
                if (gamepad != null && manager.isConnected()) {
                    // 1. 计算按钮状态
                    var btnState = 0
                    if (visualRegions.contains(REGION_BUTTON_X)) btnState = btnState or BluetoothHidGamepad.BUTTON_X
                    if (visualRegions.contains(REGION_BUTTON_A)) btnState = btnState or BluetoothHidGamepad.BUTTON_A
                    if (visualRegions.contains(REGION_BUTTON_B)) btnState = btnState or BluetoothHidGamepad.BUTTON_B
                    if (visualRegions.contains(REGION_BUTTON_MENU)) btnState = btnState or BluetoothHidGamepad.BUTTON_START

                    // 2. 设置状态（按钮 + 帽子复位）
                    gamepad.setState(btnState, BluetoothHidGamepad.HAT_CENTER)

                    // 3. 设置摇杆 X（根据重力启用状态）
                    val stickX = if (gravityEnabled) stickXValue else 0
                    gamepad.setLeftStick(stickX, 0)

                    // 4. 发送报告
                    gamepad.sendReport()
                }
                delay(8) // 125Hz
            }
        }

        // ---------- 返回按钮 ----------
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Text("返回")
        }
    }
}
