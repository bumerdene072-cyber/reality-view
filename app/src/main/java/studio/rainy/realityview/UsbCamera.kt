package studio.rainy.realityview

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikimn.droiduvc.compose.UvcCameraPreview
import com.mikimn.droiduvc.compose.UvcCameraState
import com.mikimn.droiduvc.compose.rememberIsStarted
import com.mikimn.droiduvc.compose.rememberUsbPermission
import com.mikimn.droiduvc.compose.rememberUvcCamera
import com.mikimn.droiduvc.compose.rememberUvcDevices

@Composable
internal fun UsbCamera() {
    if (!Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a") || !android.os.Process.is64Bit()) {
        Text(stringResource(R.string.uvc_arm64))
        return
    }
    val devices by rememberUvcDevices()
    var selected by remember { mutableStateOf<String?>(null) }
    if (devices.isEmpty()) {
        Text(stringResource(R.string.uvc_empty))
        return
    }
    val device = devices.firstOrNull { it.deviceName == selected } ?: devices.first()
    devices.forEach { item ->
        FilterChip(selected = item.deviceName == device.deviceName, onClick = { selected = item.deviceName },
            label = { Text(item.productName?.takeIf { it.isNotBlank() } ?: item.deviceName) })
    }
    key(device.deviceName) {
        val permission = rememberUsbPermission(device)
        if (!permission.granted) {
            Button(onClick = permission.request) { Text(stringResource(R.string.grant_camera)) }
        } else if (rememberIsStarted()) {
            when (val state = rememberUvcCamera(device)) {
                UvcCameraState.Opening -> Text(stringResource(R.string.connecting))
                is UvcCameraState.Failed -> Text(stringResource(R.string.camera_error))
                is UvcCameraState.Ready -> UvcCameraPreview(state.camera, Modifier.fillMaxWidth())
            }
        }
    }
}
