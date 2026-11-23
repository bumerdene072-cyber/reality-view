package studio.rainy.realityview

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val language = getSharedPreferences("settings", MODE_PRIVATE).getString("language", "mn") ?: "mn"
        val locales = LocaleListCompat.forLanguageTags(language)
        if (AppCompatDelegate.getApplicationLocales() != locales) AppCompatDelegate.setApplicationLocales(locales)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RainyApp() }
    }
}

internal enum class Screen(val label: Int) {
    Home(R.string.home), Camera(R.string.camera), Reality(R.string.reality),
    Surround(R.string.surround), Compatibility(R.string.compatibility), Settings(R.string.settings)
}

private val cameraDirections = listOf(R.string.front, R.string.rear, R.string.left, R.string.right)
private val colors = darkColorScheme(
    primary = Color(0xff85dbf4),
    onPrimary = Color(0xff00232c),
    secondaryContainer = Color(0xff203646),
    surface = Color(0xff111a24),
    background = Color(0xff0c1118)
)

@Composable
private fun RainyApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var page by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var front by rememberSaveable { mutableStateOf(prefs.getBoolean("front", false)) }
    var fast by rememberSaveable { mutableStateOf(prefs.getBoolean("fast", false)) }

    MaterialTheme(colorScheme = colors) {
        Scaffold { insets ->
            BoxWithConstraints(Modifier.padding(insets).fillMaxSize()) {
                val wide = maxWidth >= 720.dp
                val surroundColumns = if (maxWidth >= 520.dp) 2 else 1
                Row(Modifier.fillMaxSize()) {
                    if (wide) RainyNavigation(page, { page = it }, true)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (!wide) RainyNavigation(page, { page = it }, false)
                        key(page) {
                            Column(
                                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                when (Screen.valueOf(page)) {
                                    Screen.Home -> {
                                        RealityDashboard(
                                            openCamera = { page = Screen.Camera.name },
                                            openReality = { page = Screen.Reality.name }
                                        )
                                        ElevatedCard(Modifier.fillMaxWidth()) {
                                            Box(Modifier.padding(16.dp)) { GpsStatus() }
                                        }
                                    }
                                    Screen.Camera -> {
                                        Heading(R.string.camera)
                                        var network by rememberSaveable { mutableStateOf(false) }
                                        var external by rememberSaveable { mutableStateOf(false) }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(!network, { network = false }, { Text(stringResource(R.string.device)) })
                                            FilterChip(network, { network = true }, { Text("RTSP") })
                                        }
                                        if (network) NetworkInput("single", R.string.network)
                                        else {
                                            Toggle(R.string.external_camera, external) { external = it }
                                            if (external) Text(stringResource(R.string.external_help))
                                            else Toggle(R.string.selfie, front) {
                                                front = it
                                                prefs.edit().putBoolean("front", it).apply()
                                            }
                                            DeviceCamera(front, false, 200, external = external)
                                        }
                                    }
                                    Screen.Reality -> {
                                        Heading(R.string.reality)
                                        val calibration = remember { readRoadCalibration(context) }
                                        var road by rememberSaveable { mutableStateOf(false) }
                                        val projectionAvailable = calibration != null && !front
                                        if (projectionAvailable) Toggle(R.string.road_view, road) { road = it }
                                        else OutlinedButton(onClick = { page = Screen.Settings.name }) {
                                            Text(stringResource(R.string.calibration))
                                        }
                                        Text(stringResource(if (road && projectionAvailable) R.string.road_estimate else R.string.ai_description))
                                        DeviceCamera(front, true, if (fast) 130 else 300,
                                            roadCalibration = calibration.takeIf { road && projectionAvailable })
                                    }
                                    Screen.Surround -> {
                                        Heading(R.string.surround)
                                        Text(stringResource(R.string.surround_description))
                                        val columns = surroundColumns
                                        val groups = cameraDirections.chunked(columns)
                                        groups.forEachIndexed { row, directions ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                directions.forEachIndexed { col, direction ->
                                                    Box(Modifier.weight(1f)) { NetworkInput("surround_${row * columns + col}", direction) }
                                                }
                                            }
                                        }
                                    }
                                    Screen.Compatibility -> Compatibility()
                                    Screen.Settings -> {
                                        Heading(R.string.settings)
                                        Text(stringResource(R.string.language))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("mn" to "Монгол", "en" to "English").forEach { (tag, title) ->
                                                val selected = prefs.getString("language", "mn") == tag
                                                FilterChip(selected = selected, onClick = {
                                                    prefs.edit().putString("language", tag).apply()
                                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                                                }, label = { Text(title) })
                                            }
                                        }
                                        Toggle(R.string.selfie, front) {
                                            front = it
                                            prefs.edit().putBoolean("front", it).apply()
                                        }
                                        Toggle(R.string.fast, fast) {
                                            fast = it
                                            prefs.edit().putBoolean("fast", it).apply()
                                        }
                                        RoadCalibrationSettings()
                                        Text(stringResource(R.string.privacy))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.headlineSmall)
}

@Composable
internal fun Toggle(label: Int, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change)
    }
}

@Composable
private fun NetworkInput(key: String, title: Int) {
    var input by remember(key) { mutableStateOf("") }
    var connectedAddress by remember(key) { mutableStateOf<String?>(null) }
    var invalid by remember(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        val address = connectedAddress
        if (address == null) {
            OutlinedTextField(input, onValueChange = { input = it; invalid = false },
                label = { Text(stringResource(R.string.rtsp_address)) }, singleLine = true,
                isError = invalid, modifier = Modifier.fillMaxWidth())
            if (invalid) Text(stringResource(R.string.invalid_address), color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                if (validRtspAddress(input)) connectedAddress = input.trim()
                else invalid = true
            }) { Text(stringResource(R.string.connect)) }
        } else {
            NetworkCamera(address, Modifier.fillMaxWidth())
            OutlinedButton(onClick = { connectedAddress = null }) { Text(stringResource(R.string.disconnect)) }
        }
    }
}

@Composable
private fun Compatibility() {
    val context = LocalContext.current
    val usb = context.getSystemService(UsbManager::class.java)
    var count by remember { mutableIntStateOf(-1) }
    var make by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    Heading(R.string.compatibility)
    OutlinedTextField(make, { make = it }, label = { Text(stringResource(R.string.make)) }, singleLine = true)
    OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.model)) }, singleLine = true)
    OutlinedTextField(year, { year = it }, label = { Text(stringResource(R.string.year)) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
    if (make.isNotBlank() && model.isNotBlank() && year.toIntOrNull()?.let { it in 1886..2100 } == true) {
        Text(stringResource(R.string.no_vehicle_records))
    }
    OutlinedButton(onClick = {
        count = usb?.deviceList?.values?.count { device ->
            device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
                (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }
        } ?: 0
    }) { Text(stringResource(R.string.scan_usb)) }
    if (count >= 0) Text(stringResource(R.string.usb_results, count))
    var reportFailed by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        reportFailed = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bumerdene072-cyber/reality-view/issues")))
        }.isFailure
    }) { Text(stringResource(R.string.report)) }
    if (reportFailed) Text(stringResource(R.string.no_browser))
}
