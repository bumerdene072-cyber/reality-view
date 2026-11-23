package studio.rainy.realityview

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
fun GpsStatus() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<Location?>(null) }
    var status by remember { mutableIntStateOf(R.string.gps_waiting) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        enabled = it[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!enabled) status = R.string.gps_permission
    }

    if (enabled) DisposableEffect(owner) {
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { reading = location }
            override fun onProviderDisabled(provider: String) {
                reading = null
                status = R.string.gps_unavailable
            }
            override fun onProviderEnabled(provider: String) { status = R.string.gps_waiting }
            @Deprecated("Required on older Android versions")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        }
        val lifecycle = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    status = R.string.gps_waiting
                    if (manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        status = R.string.gps_unavailable
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
                        try {
                            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, listener, Looper.getMainLooper())
                        } catch (_: RuntimeException) {
                            status = R.string.gps_unavailable
                        }
                    } else status = R.string.gps_permission
                }
                Lifecycle.Event.ON_STOP -> {
                    manager?.removeUpdates(listener)
                    reading = null
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(lifecycle)
        onDispose {
            owner.lifecycle.removeObserver(lifecycle)
            manager?.removeUpdates(listener)
            reading = null
        }
    }

    LaunchedEffect(reading) {
        val current = reading ?: return@LaunchedEffect
        val milliseconds = (SystemClock.elapsedRealtimeNanos() - current.elapsedRealtimeNanos) / 1_000_000
        delay((5_000 - milliseconds).coerceIn(0, 5_000))
        reading = null
    }

    Column {
        Toggle(R.string.gps, enabled) { on ->
            if (!on) {
                enabled = false
                status = R.string.gps_waiting
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
                enabled = true
            } else {
                ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        val current = reading
        if (enabled && current?.hasSpeed() == true) {
            Text(stringResource(R.string.gps_speed, current.speed * 3.6f))
        } else if (enabled || status == R.string.gps_permission) Text(stringResource(status))
    }
}
