package com.tools.gamecontroller

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

// ============================== 控件定义 ==============================

private data class PadButtonDef(
    val id: String,
    val label: String,
    val mask: Int,      // 按键掩码（Hat 方向键为 0）
    val hatDir: Int?,   // 非 null 表示 D-Pad 方向
    val defaultX: Float,
    val defaultY: Float,
    val color: Color
)

private data class PadStickDef(
    val id: String,
    val defaultX: Float,
    val defaultY: Float
)

private val PAD_BUTTONS = listOf(
    PadButtonDef("A", "A", BluetoothHidGamepad.BUTTON_A, null, 0.79f, 0.50f, Color(0xFF4CAF50)),
    PadButtonDef("B", "B", BluetoothHidGamepad.BUTTON_B, null, 0.88f, 0.42f, Color(0xFFF44336)),
    PadButtonDef("X", "X", BluetoothHidGamepad.BUTTON_X, null, 0.70f, 0.42f, Color(0xFF2196F3)),
    PadButtonDef("Y", "Y", BluetoothHidGamepad.BUTTON_Y, null, 0.79f, 0.34f, Color(0xFFFFC107)),
    PadButtonDef("UP", "↑", 0, BluetoothHidGamepad.HAT_UP, 0.22f, 0.72f, Color(0xFF9C27B0)),
    PadButtonDef("DOWN", "↓", 0, BluetoothHidGamepad.HAT_DOWN, 0.22f, 0.90f, Color(0xFF9C27B0)),
    PadButtonDef("LEFT", "←", 0, BluetoothHidGamepad.HAT_LEFT, 0.12f, 0.81f, Color(0xFF9C27B0)),
    PadButtonDef("RIGHT", "→", 0, BluetoothHidGamepad.HAT_RIGHT, 0.32f, 0.81f, Color(0xFF9C27B0)),
    PadButtonDef("LB", "LB", BluetoothHidGamepad.BUTTON_LB, null, 0.12f, 0.25f, Color(0xFFFF9800)),
    PadButtonDef("LT", "LT", BluetoothHidGamepad.BUTTON_LT, null, 0.12f, 0.12f, Color(0xFFFF5722)),
    PadButtonDef("RB", "RB", BluetoothHidGamepad.BUTTON_RB, null, 0.88f, 0.25f, Color(0xFFFF9800)),
    PadButtonDef("RT", "RT", BluetoothHidGamepad.BUTTON_RT, null, 0.88f, 0.12f, Color(0xFFFF5722)),
    PadButtonDef("START", "START", BluetoothHidGamepad.BUTTON_START, null, 0.58f, 0.10f, Color(0xFF607D8B)),
    PadButtonDef("BACK", "BACK", BluetoothHidGamepad.BUTTON_BACK, null, 0.42f, 0.10f, Color(0xFF607D8B)),
    // 左右摇杆按下键（L3 / R3）：位于对应摇杆正下方
    PadButtonDef("L3", "L3", BluetoothHidGamepad.BUTTON_L3, null, 0.26f, 0.66f, Color(0xFF9E9E9E)),
    PadButtonDef("R3", "R3", BluetoothHidGamepad.BUTTON_R3, null, 0.76f, 0.88f, Color(0xFF9E9E9E)),
)

private val PAD_STICKS = listOf(
    PadStickDef("STICK_L", 0.26f, 0.44f),
    PadStickDef("STICK_R", 0.76f, 0.68f),
)

// 工具栏按钮统一高对比配色：深绿底 + 白字
private val toolbarButtonColor = Color(0xFF1B5E20)

private const val PREFS_NAME = "fullpad_layout"
private const val KEY_POSITIONS = "positions"
private const val KEY_BUTTON_SCALE = "button_scale"
private const val TAG = "FullPad"

// ============================== 布局持久化 ==============================

private fun defaultPositions(): Map<String, Offset> = buildMap {
    PAD_BUTTONS.forEach { put(it.id, Offset(it.defaultX, it.defaultY)) }
    PAD_STICKS.forEach { put(it.id, Offset(it.defaultX, it.defaultY)) }
}

private fun loadPositions(context: Context): Map<String, Offset> {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_POSITIONS, null) ?: return defaultPositions()
    return try {
        val obj = JSONObject(json)
        val map = defaultPositions().toMutableMap()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (map.containsKey(key)) {
                val arr = obj.getJSONArray(key)
                if (arr.length() >= 2) {
                    map[key] = Offset(
                        arr.getDouble(0).toFloat().coerceIn(0f, 1f),
                        arr.getDouble(1).toFloat().coerceIn(0f, 1f)
                    )
                }
            }
        }
        map
    } catch (e: Exception) {
        Log.w(TAG, "解析布局失败，使用默认布局", e)
        defaultPositions()
    }
}

private fun savePositions(context: Context, map: Map<String, Offset>) {
    val obj = JSONObject()
    map.forEach { (key, value) ->
        obj.put(key, JSONArray().put(value.x.toDouble()).put(value.y.toDouble()))
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_POSITIONS, obj.toString()).apply()
}

private fun loadButtonScale(context: Context): Float {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getFloat(KEY_BUTTON_SCALE, 1f)
        .coerceIn(1f, 2f)
}

private fun saveButtonScale(context: Context, scale: Float) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putFloat(KEY_BUTTON_SCALE, scale.coerceIn(1f, 2f))
        .apply()
}

// ============================== 命中检测 ==============================

private fun resolveHit(
    p: Offset,
    buttonRadiusPx: Float,
    stickRadiusPx: Float,
    centerOf: (String) -> Offset
): String? {
    var bestId: String? = null
    var bestScore = Float.MAX_VALUE

    // 按键的命中半径略大于视觉半径，便于手指滑动触发
    val buttonHitRadius = buttonRadiusPx * 1.15f

    // 使用“相对距离”（实际距离 / 控件命中半径）而不是绝对距离比较。
    // 这样当按键命中圈与摇杆外圈重叠时，更靠近哪个控件中心就命中哪个，
    // 避免 A/B 按键的命中圈在横屏/小屏下把右摇杆边缘的触摸“抢走”。
    PAD_BUTTONS.forEach { def ->
        val c = centerOf(def.id)
        val d = (p - c).getDistance()
        if (d <= buttonHitRadius) {
            val score = d / buttonHitRadius
            if (score < bestScore) {
                bestScore = score
                bestId = def.id
            }
        }
    }
    PAD_STICKS.forEach { stick ->
        val c = centerOf(stick.id)
        val d = (p - c).getDistance()
        if (d <= stickRadiusPx) {
            val score = d / stickRadiusPx
            if (score < bestScore) {
                bestScore = score
                bestId = stick.id
            }
        }
    }
    return bestId
}

// ============================== 完整手柄界面 ==============================

@Composable
fun FullPadScreen(
    manager: BluetoothHidManager
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val baseButtonRadiusPx = with(density) { 32.dp.toPx() }
    // 按键整体缩放：1.0 为当前代码里的基准大小，最大 2.0（增大 100%），最小 1.0
    var buttonScale by remember { mutableStateOf(loadButtonScale(context)) }
    val buttonRadiusPx = baseButtonRadiusPx * buttonScale
    val stickOuterRadiusPx = with(density) { 64.dp.toPx() }
    val stickKnobRadiusPx = with(density) { 26.dp.toPx() }
    val toolbarHeightPx = with(density) { 48.dp.toPx() }

    var toolbarVisible by remember { mutableStateOf(true) }
    val showToolbarButtonWidthPx = with(density) { 105.dp.toPx() }
    val showToolbarButtonHeightPx = with(density) { 36.dp.toPx() }

    var positions = remember { mutableStateMapOf<String, Offset>().apply { putAll(loadPositions(context)) } }
    var editMode by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeControls by remember { mutableStateOf(mapOf<PointerId, String>()) }
    var stickOffsets by remember { mutableStateOf(mapOf<String, Offset>()) }
    var isConnected by remember { mutableStateOf(manager.isConnected()) }
    // 配对弹窗状态
    var showConnectDialog by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf(listOf<BluetoothDevice>()) }

    fun centerOf(id: String): Offset {
        val p = positions[id] ?: return Offset.Zero
        return Offset(p.x * canvasSize.width, p.y * canvasSize.height)
    }

    // 摇杆偏移 -> 摇杆值（带死区，死区后重新归一化）
    fun stickValue(id: String): Pair<Int, Int> {
        val grabbed = activeControls.values.any { it == id }
        val o = if (grabbed) stickOffsets[id] ?: Offset.Zero else Offset.Zero
        val len = o.getDistance()
        if (len < 0.5f) return 0 to 0
        val frac = (len / stickOuterRadiusPx).coerceIn(0f, 1f)
        val deadZone = 0.12f
        if (frac <= deadZone) return 0 to 0
        val scaled = (frac - deadZone) / (1f - deadZone) * 127f
        val ux = o.x / len
        val uy = o.y / len
        return (ux * scaled).roundToInt() to (uy * scaled).roundToInt()
    }

    // 125Hz 轮询上报
    LaunchedEffect(Unit) {
        Log.d(TAG, "启动轮询循环: 125Hz")
        var lastLx = 0
        var lastLy = 0
        var lastRx = 0
        var lastRy = 0
        while (true) {
            val gp = manager.gamepad
            if (gp != null && manager.isConnected()) {
                val ids = activeControls.values
                var btn = 0
                PAD_BUTTONS.forEach { def ->
                    if (def.hatDir == null && ids.any { it == def.id }) btn = btn or def.mask
                }

                val up = ids.any { it == "UP" }
                val down = ids.any { it == "DOWN" }
                val left = ids.any { it == "LEFT" }
                val right = ids.any { it == "RIGHT" }
                val hat = when {
                    up && right -> BluetoothHidGamepad.HAT_UP_RIGHT
                    down && right -> BluetoothHidGamepad.HAT_DOWN_RIGHT
                    down && left -> BluetoothHidGamepad.HAT_DOWN_LEFT
                    up && left -> BluetoothHidGamepad.HAT_UP_LEFT
                    up -> BluetoothHidGamepad.HAT_UP
                    down -> BluetoothHidGamepad.HAT_DOWN
                    left -> BluetoothHidGamepad.HAT_LEFT
                    right -> BluetoothHidGamepad.HAT_RIGHT
                    else -> BluetoothHidGamepad.HAT_CENTER
                }

                gp.setState(btn, hat)
                val (lx, ly) = stickValue("STICK_L")
                val (rx, ry) = stickValue("STICK_R")
                gp.setLeftStick(lx, ly)
                gp.setRightStick(rx, ry)
                // 线性扳机：按下 LT / RT 按键时，对应扳机轴直接满量程 255 发送，松开归零
                gp.setTriggers(
                    if (ids.any { it == "LT" }) 255 else 0,
                    if (ids.any { it == "RT" }) 255 else 0
                )
                if (lx != lastLx || ly != lastLy || rx != lastRx || ry != lastRy) {
                    Log.d(TAG, "Stick report: L=($lx,$ly), R=($rx,$ry)")
                    lastLx = lx
                    lastLy = ly
                    lastRx = rx
                    lastRy = ry
                }
                gp.sendReport()
            }
            delay(8)
        }
    }

    // 连接状态轮询
    LaunchedEffect(Unit) {
        while (true) {
            isConnected = manager.isConnected()
            delay(500)
        }
    }

    // 离开界面时释放所有按键，避免目标设备上按键卡住
    DisposableEffect(Unit) {
        onDispose { manager.gamepad?.reset() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121))
            .onSizeChanged { canvasSize = it }
            .pointerInput(editMode, canvasSize, toolbarVisible, buttonScale) {
                if (!editMode) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val next = activeControls.toMutableMap()
                            val newOffsets = stickOffsets.toMutableMap()

                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    // 工具栏隐藏时，顶部中间的“显示顶部”按钮区域只负责恢复工具栏，
                                    // 不触发任何游戏控件。
                                    val inShowToolbarButton = !toolbarVisible &&
                                        change.position.x >= (size.width.toFloat() - showToolbarButtonWidthPx) / 2f &&
                                        change.position.x <= (size.width.toFloat() + showToolbarButtonWidthPx) / 2f &&
                                        change.position.y <= showToolbarButtonHeightPx

                                    if (inShowToolbarButton) {
                                        toolbarVisible = true
                                        next.remove(change.id)
                                    } else {
                                        val prevId = next[change.id]
                                        // 工具栏可见时排除顶部工具栏区域；隐藏后整屏都可操作
                                        val inGameArea = !toolbarVisible || change.position.y >= toolbarHeightPx
                                        val hit = if (inGameArea) {
                                            resolveHit(change.position, buttonRadiusPx, stickOuterRadiusPx) { centerOf(it) }
                                        } else null

                                        when {
                                            // 已经抓住摇杆的指针优先保持为摇杆，直到抬起。
                                            // 否则手指拖动右摇杆经过 A/B 按键命中圈时会被错误地切换成按键，
                                            // 表现为“右摇杆突然失效”。
                                            prevId == "STICK_L" || prevId == "STICK_R" -> {
                                                val stickId = prevId!!
                                                next[change.id] = stickId
                                                val delta = change.position - centerOf(stickId)
                                                val d = delta.getDistance()
                                                newOffsets[stickId] =
                                                    if (d > stickOuterRadiusPx) delta / d * stickOuterRadiusPx else delta
                                            }
                                            hit != null -> {
                                                next[change.id] = hit
                                                if (hit == "STICK_L" || hit == "STICK_R") {
                                                    val delta = change.position - centerOf(hit)
                                                    val d = delta.getDistance()
                                                    newOffsets[hit] =
                                                        if (d > stickOuterRadiusPx) delta / d * stickOuterRadiusPx else delta
                                                }
                                            }
                                            else -> next.remove(change.id)
                                        }
                                    }
                                } else {
                                    next.remove(change.id)
                                }
                                change.consume()
                            }

                            // 清理已经没有手指控制的摇杆偏移
                            val heldSticks = next.values.filter { it.startsWith("STICK_") }.toSet()
                            stickOffsets = newOffsets.filterKeys { it in heldSticks }
                            activeControls = next
                        }
                    }
                }
            }
    ) {
        // ---------- 摇杆 ----------
        PAD_STICKS.forEach { stick ->
            val center = centerOf(stick.id)
            val knobOffset = stickOffsets[stick.id] ?: Offset.Zero
            StickView(
                id = stick.id,
                centerPx = center,
                outerRadiusPx = stickOuterRadiusPx,
                knobRadiusPx = stickKnobRadiusPx,
                knobOffsetPx = knobOffset,
                editMode = editMode,
                onDrag = { drag ->
                    val cur = positions[stick.id] ?: Offset.Zero
                    positions[stick.id] = clampPosition(cur, drag, canvasSize, toolbarHeightPx, density)
                },
                onDragEnd = { savePositions(context, positions.toMap()) }
            )
        }

        // ---------- 按键 ----------
        PAD_BUTTONS.forEach { def ->
            val center = centerOf(def.id)
            val pressed = activeControls.values.any { it == def.id }
            PadButtonView(
                def = def,
                centerPx = center,
                radiusPx = buttonRadiusPx,
                pressed = pressed,
                editMode = editMode,
                onDrag = { drag ->
                    val cur = positions[def.id] ?: Offset.Zero
                    positions[def.id] = clampPosition(cur, drag, canvasSize, toolbarHeightPx, density)
                },
                onDragEnd = { savePositions(context, positions.toMap()) }
            )
        }

        // ---------- 顶部工具栏（后声明，绘制在最上层；可通过“隐藏”按钮收起） ----------
        if (toolbarVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = toolbarButtonColor),
                    onClick = {
                        val devices = manager.getPairedDevices()
                        if (devices.isEmpty()) {
                            Toast.makeText(context, "没有已配对的蓝牙设备，请先在系统设置中配对", Toast.LENGTH_LONG).show()
                        } else {
                            pairedDevices = devices
                            showConnectDialog = true
                        }
                    }
                ) { Text("连接", fontSize = 14.sp) }
                Spacer(Modifier.size(8.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = toolbarButtonColor),
                    onClick = {
                        if (manager.isConnected()) {
                            manager.disconnect()
                            Toast.makeText(context, "正在断开连接", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "当前未连接设备", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("断开", fontSize = 14.sp) }
                Spacer(Modifier.size(8.dp))
                Button(colors = ButtonDefaults.buttonColors(containerColor = toolbarButtonColor), onClick = { toolbarVisible = false }) { Text("隐藏", fontSize = 14.sp) }
                Spacer(Modifier.weight(1f))
                Text(
                    if (isConnected) "已连接" else "未连接",
                    color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (editMode) {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = toolbarButtonColor),
                        onClick = {
                            positions.clear()
                            positions.putAll(defaultPositions())
                            savePositions(context, positions.toMap())
                        }
                    ) { Text("恢复默认", fontSize = 14.sp) }
                }
                Spacer(Modifier.size(4.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = toolbarButtonColor),
                    enabled = buttonScale > 1f,
                    onClick = {
                        val newScale = (buttonScale - 0.1f).coerceAtLeast(1f)
                        buttonScale = newScale
                        saveButtonScale(context, newScale)
                    }
                ) { Text("缩小", fontSize = 14.sp) }
                Spacer(Modifier.size(4.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = toolbarButtonColor),
                    enabled = buttonScale < 2f,
                    onClick = {
                        val newScale = (buttonScale + 0.1f).coerceAtMost(2f)
                        buttonScale = newScale
                        saveButtonScale(context, newScale)
                    }
                ) { Text("放大", fontSize = 14.sp) }
                Spacer(Modifier.size(4.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = toolbarButtonColor),
                    onClick = {
                        editMode = !editMode
                        if (editMode) {
                            // 进入编辑模式时释放全部按键
                            activeControls = emptyMap()
                            stickOffsets = emptyMap()
                        }
                    }
                ) { Text(if (editMode) "完成编辑" else "编辑布局", fontSize = 14.sp) }
            }
        }

        // ---------- 设备选择弹窗 ----------
        if (showConnectDialog) {
            AlertDialog(
                onDismissRequest = { showConnectDialog = false },
                title = { Text("选择要连接的设备") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        pairedDevices.forEach { device ->
                            TextButton(
                                onClick = {
                                    showConnectDialog = false
                                    manager.connect(device.address)
                                    Toast.makeText(
                                        context,
                                        "正在连接 ${device.name ?: device.address}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(device.name ?: device.address)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showConnectDialog = false }) { Text("取消") }
                }
            )
        }

        // ---------- 工具栏隐藏时，顶部中间显示“显示顶部” ----------
        if (!toolbarVisible) {
            Button(
                onClick = { toolbarVisible = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(width = 105.dp, height = 36.dp)
            ) {
                Text("显示顶部", fontSize = 14.sp, color = Color.White)
            }
        }

        // ---------- 编辑模式提示 ----------
        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(Color(0xCC000000), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "编辑模式：拖动按键 / 摇杆调整位置，完成后点击「完成编辑」",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// 拖拽时更新归一化坐标并限制在屏幕内（上边界避开工具栏）
private fun clampPosition(
    cur: Offset,
    drag: Offset,
    canvasSize: IntSize,
    toolbarHeightPx: Float,
    density: androidx.compose.ui.unit.Density
): Offset {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return cur
    val minY = with(density) { (toolbarHeightPx + 8.dp.toPx()) / canvasSize.height }
    return Offset(
        (cur.x + drag.x / canvasSize.width).coerceIn(0.03f, 0.97f),
        (cur.y + drag.y / canvasSize.height).coerceIn(minY, 0.97f)
    )
}

@Composable
private fun PadButtonView(
    def: PadButtonDef,
    centerPx: Offset,
    radiusPx: Float,
    pressed: Boolean,
    editMode: Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current
    val diameter = with(density) { (radiusPx * 2).toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset((centerPx.x - radiusPx).roundToInt(), (centerPx.y - radiusPx).roundToInt()) }
            .size(diameter)
            .then(
                if (editMode) {
                    Modifier.pointerInput(def.id) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                onDrag(drag)
                            },
                            onDragEnd = onDragEnd
                        )
                    }
                } else Modifier
            )
            .clip(CircleShape)
            .background(if (pressed) def.color else def.color.copy(alpha = 0.40f))
            .border(
                if (editMode) 2.dp else 1.dp,
                if (pressed) Color.White else Color.White.copy(alpha = 0.85f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            def.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (def.label.length > 2) 12.sp else 20.sp
        )
    }
}

@Composable
private fun StickView(
    id: String,
    centerPx: Offset,
    outerRadiusPx: Float,
    knobRadiusPx: Float,
    knobOffsetPx: Offset,
    editMode: Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current
    val outerDp = with(density) { (outerRadiusPx * 2).toDp() }
    val knobDp = with(density) { (knobRadiusPx * 2).toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset((centerPx.x - outerRadiusPx).roundToInt(), (centerPx.y - outerRadiusPx).roundToInt()) }
            .size(outerDp)
            .then(
                if (editMode) {
                    Modifier.pointerInput(id) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                onDrag(drag)
                            },
                            onDragEnd = onDragEnd
                        )
                    }
                } else Modifier
            )
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .border(
                if (editMode) 2.dp else 1.dp,
                Color.White.copy(alpha = 0.8f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffsetPx.x.roundToInt(), knobOffsetPx.y.roundToInt()) }
                .size(knobDp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.75f))
        )
    }
}
