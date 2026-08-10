package com.tools.gamecontroller

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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
/*
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.platform.LocalContext
*/
import kotlinx.coroutines.delay

@Composable
fun SliderPadScreen(
    manager: BluetoothHidManager,
    onBack: () -> Unit
) {
    /*
    val context = androidx.compose.ui.platform.LocalContext.current
    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    */

    val gamepad = manager.gamepad
    val TAG = "SliderPad"

    val REGION_SLIDER = 0
    val REGION_BUTTON_B = 1
    val REGION_BUTTON_MENU = 2
    val REGION_BUTTON_X = 3
    val REGION_BUTTON_A = 4

    var sliderValue by remember { mutableStateOf(0) }
    var isSliding by remember { mutableStateOf(false) }
    var visualRegions by remember { mutableStateOf(setOf<Int>()) }
    /*
    var wasInDeadZone by remember { mutableStateOf(false) }
    */

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        var hasSliderTouch = false
                        var newSliderValue = 0
                        val newVisualRegions = mutableSetOf<Int>()
                        val h = size.height
                        val w = size.width

                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val pos = change.position
                                val region = when {
                                    pos.y < h * 0.4f -> REGION_SLIDER
                                    pos.y < h * 0.6f -> {
                                        if (pos.x < w / 2f) REGION_BUTTON_B else REGION_BUTTON_MENU
                                    }
                                    else -> {
                                        if (pos.x < w / 2f) REGION_BUTTON_X else REGION_BUTTON_A
                                    }
                                }
                                newVisualRegions.add(region)

                                if (region == REGION_SLIDER) {
                                    hasSliderTouch = true
                                    val fraction = pos.y / (h * 0.4f) // 0~1
                                    /*
                                    // 判断是否在死区
                                    val inDeadZone = fraction in 0.45f..0.55f
                                    if (inDeadZone && !wasInDeadZone) {
                                        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                    }
                                    wasInDeadZone = inDeadZone
                                    */
                                    val value = when {
                                        fraction < 0.25f -> -127
                                        fraction < 0.45f -> {
                                            // 映射 [0.25, 0.45] → [-127, 0]
                                            ((fraction - 0.25f) / 0.2f * 127 - 127).toInt()
                                        }
                                        fraction < 0.55f -> 0   // 死区 [0.45, 0.55)
                                        fraction < 0.75f -> {
                                            // 映射 [0.55, 0.75] → [0, 127]
                                            ((fraction - 0.55f) / 0.2f * 127).toInt()
                                        }
                                        else -> 127
                                    }
                                    newSliderValue = value.coerceIn(-127, 127)
                                }
                            }
                            change.consume()
                        }

                        visualRegions = newVisualRegions
                        if (hasSliderTouch) {
                            isSliding = true
                            sliderValue = newSliderValue
                        } else {
                            isSliding = false
                            sliderValue = 0
                            /*
                            // 手指抬起时重置死区状态，避免下次进入时误判
                            wasInDeadZone = false
                            */
                        }
                    }
                }
            }
    ) {
        val hatHeight = maxHeight * 0.4f
        val bHeight = maxHeight * 0.2f
        val xaHeight = maxHeight * 0.4f

        // 上半：滑块
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hatHeight)
                .background(Color.DarkGray)
                .border(1.dp, Color.White)
        ) {
            val fraction = (sliderValue + 127) / 254f
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    "摇杆 X: $sliderValue",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(20.dp)
                        .background(Color.Gray)
                        .border(1.dp, Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(Color.Green)
                    )
                }
            }
        }

        // 中部：B / MENU
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
            ) { Text("B", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
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
            ) { Text("MENU", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
        }

        // 下部：X / A
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
            ) { Text("X", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
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
            ) { Text("A", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) { Text("返回") }
    }

    // 轮询循环
    LaunchedEffect(Unit) {
        Log.d(TAG, "启动轮询循环: 125Hz")
        while (true) {
            val gamepad = manager.gamepad
            if (gamepad != null && manager.isConnected()) {
                var btnState = 0
                if (visualRegions.contains(REGION_BUTTON_X)) btnState = btnState or BluetoothHidGamepad.BUTTON_X
                if (visualRegions.contains(REGION_BUTTON_A)) btnState = btnState or BluetoothHidGamepad.BUTTON_A
                if (visualRegions.contains(REGION_BUTTON_B)) btnState = btnState or BluetoothHidGamepad.BUTTON_B
                if (visualRegions.contains(REGION_BUTTON_MENU)) btnState = btnState or BluetoothHidGamepad.BUTTON_START

                gamepad.setState(btnState, BluetoothHidGamepad.HAT_CENTER)
                val stickX = if (isSliding) sliderValue else 0
                gamepad.setLeftStick(stickX, 0)
                gamepad.sendReport()
            }
            delay(8)
        }
    }
}