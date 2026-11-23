package studio.rainy.realityview

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.net.URI
import kotlinx.coroutines.delay

@Suppress("DEPRECATION")
@Composable
internal fun RtspDiscovery(onAddress: (String) -> Unit) {
    val manager = LocalContext.current.getSystemService(NsdManager::class.java)
    val main = remember { Handler(Looper.getMainLooper()) }
    val deliver by rememberUpdatedState(onAddress)
    val services = remember { mutableStateMapOf<String, NsdServiceInfo>() }
    var scanning by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var result by remember { mutableIntStateOf(0) }

    DisposableEffect(scanning, manager) {
        var active = scanning
        if (scanning && manager != null) {
            fun update(block: () -> Unit) { main.post { if (active) block() } }
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) = Unit
                override fun onDiscoveryStopped(type: String) = Unit
                override fun onStartDiscoveryFailed(type: String, code: Int) = update {
                    result = R.string.rtsp_scan_error
                    scanning = false
                }
                override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
                override fun onServiceFound(info: NsdServiceInfo) = update {
                    services[info.serviceName] = info
                }
                override fun onServiceLost(info: NsdServiceInfo) = update {
                    services.remove(info.serviceName)
                }
            }
            runCatching { manager.discoverServices("_rtsp._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
                .onFailure { result = R.string.rtsp_scan_error; scanning = false }
            onDispose {
                active = false
                runCatching { manager.stopServiceDiscovery(listener) }
            }
        } else onDispose { active = false }
    }

    LaunchedEffect(scanning) {
        if (scanning) {
            delay(7_000)
            result = if (services.isEmpty()) R.string.rtsp_scan_empty else 0
            scanning = false
        }
    }

    Column {
        OutlinedButton(enabled = manager != null && !scanning && !resolving, onClick = {
            services.clear()
            result = 0
            scanning = true
        }) { Text(stringResource(if (scanning) R.string.rtsp_scanning else R.string.rtsp_scan)) }
        if (result != 0) Text(stringResource(result))
        services.toSortedMap().forEach { (name, info) ->
            OutlinedButton(enabled = !resolving, onClick = {
                val nsd = manager ?: return@OutlinedButton
                resolving = true
                result = 0
                fun finish(address: String?) {
                    main.post {
                        resolving = false
                        if (address == null) result = R.string.rtsp_scan_error
                        else {
                            deliver(address)
                            result = R.string.rtsp_path_hint
                        }
                    }
                }
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(service: NsdServiceInfo, code: Int) = finish(null)
                        override fun onServiceResolved(service: NsdServiceInfo) {
                            val host = service.host?.hostAddress
                            val address = if (host != null && service.port in 1..65535) runCatching {
                                URI("rtsp", null, host, service.port, "/", null, null).toASCIIString()
                            }.getOrNull() else null
                            finish(address)
                        }
                    })
                }.onFailure { finish(null) }
            }) { Text(name) }
        }
    }
}
