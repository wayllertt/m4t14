package com.example.m4t14

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.m4t14.ui.theme.M4t14Theme
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            M4t14Theme {
                CompassApp()
            }
        }
    }
}

@Composable
private fun CompassApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    var azimuth by rememberSaveable { mutableFloatStateOf(0f) }
    var accumulatedRotation by rememberSaveable { mutableFloatStateOf(0f) }
    var sensorError by remember { mutableStateOf(rotationSensor == null) }

    DisposableEffect(lifecycleOwner, rotationSensor) {
        if (rotationSensor == null) {
            sensorError = true
            onDispose { }
        } else {
            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return

                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val newAzimuth = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()

                    val target = -newAzimuth
                    val delta = normalizeDelta(target - accumulatedRotation)
                    accumulatedRotation += delta
                    azimuth = newAzimuth
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        sensorManager.registerListener(
                            listener,
                            rotationSensor,
                            SensorManager.SENSOR_DELAY_GAME
                        )
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        sensorManager.unregisterListener(listener)
                    }

                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                sensorManager.unregisterListener(listener)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D12))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (sensorError) {
            Text(
                text = "Устройство не поддерживает датчик ориентации",
                color = Color(0xFFFF4D4D),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            CompassContent(
                azimuth = azimuth,
                needleRotation = accumulatedRotation
            )
        }
    }
}

@Composable
private fun CompassContent(azimuth: Float, needleRotation: Float) {
    val animatedNeedleRotation by animateFloatAsState(
        targetValue = needleRotation,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "needle_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Компас",
            color = Color(0xFFE6EAF2),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(Color(0xFF151924))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CompassDial(animatedNeedleRotation)
        }

        Text(
            text = "Азимут: ${azimuth.roundToInt()}°",
            color = Color(0xFFD5DBE8),
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 28.dp)
        )
    }
}

@Composable
private fun CompassDial(needleRotation: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val diameter = min(size.width, size.height)
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = Color(0xFF2A3244),
            radius = radius,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )

        drawCircle(
            color = Color(0xFF121722),
            radius = radius * 0.98f,
            center = center
        )

        rotate(degrees = needleRotation, pivot = center) {
            val arrowLength = radius * 0.8f
            val northTip = Offset(center.x, center.y - arrowLength / 2f)
            val southTip = Offset(center.x, center.y + arrowLength / 2f)

            drawLine(
                color = Color(0xFFE94545),
                start = center,
                end = northTip,
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF2F3138),
                start = center,
                end = southTip,
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawCircle(color = Color(0xFFE6EAF2), radius = 7.dp.toPx(), center = center)

        val nOffset = Offset(
            x = center.x,
            y = center.y - radius + 28.dp.toPx()
        )

        drawContext.canvas.nativeCanvas.drawText(
            "N",
            nOffset.x,
            nOffset.y,
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#F4F7FF")
                textSize = 58f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
        )
    }
}

private fun normalizeDelta(value: Float): Float {
    var delta = value
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D12)
@Composable
private fun CompassPreview() {
    M4t14Theme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0D12))
        ) {
            CompassContent(
                azimuth = 142f,
                needleRotation = -142f
            )
        }
    }
}
