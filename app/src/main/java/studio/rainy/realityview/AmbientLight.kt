package studio.rainy.realityview

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect

internal enum class AmbientLight { Unavailable, Waiting, Dim, Bright }

@Composable
internal fun rememberAmbientLight(enabled: Boolean): AmbientLight {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    var reading by remember(sensor, enabled) { mutableStateOf(AmbientLight.Unavailable) }
    if (enabled && manager != null && sensor != null) {
        LifecycleStartEffect(sensor) {
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values.firstOrNull() ?: return
                    if (!lux.isFinite() || lux < 0) return
                    reading = when {
                        lux <= 18f -> AmbientLight.Dim
                        lux >= 35f -> AmbientLight.Bright
                        reading == AmbientLight.Waiting -> AmbientLight.Bright
                        else -> reading
                    }
                }
            }
            reading = if (manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                AmbientLight.Waiting
            } else {
                AmbientLight.Unavailable
            }
            onStopOrDispose {
                manager.unregisterListener(listener)
            }
        }
    }
    return reading
}
