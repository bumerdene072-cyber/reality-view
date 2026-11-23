package studio.rainy.realityview

import android.hardware.camera2.CameraManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
internal fun CameraInputs(front: Boolean, change: (Boolean) -> Unit) {
    val context = LocalContext.current
    var ids by remember { mutableStateOf<List<String>?>(null) }
    var cameraId by remember { mutableStateOf<String?>(null) }
    var external by remember { mutableStateOf(false) }
    Toggle(R.string.external_camera, external) {
        external = it
        cameraId = null
    }
    if (external) Text(stringResource(R.string.external_help))
    else Toggle(R.string.selfie, front) {
        cameraId = null
        change(it)
    }
    OutlinedButton(onClick = {
        ids = runCatching {
            context.getSystemService(CameraManager::class.java)?.cameraIdList?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }) { Text(stringResource(R.string.scan_android_inputs)) }
    ids?.let { inputs ->
        Column {
            if (inputs.isEmpty()) Text(stringResource(R.string.camera_scan_empty))
            inputs.forEach { id ->
                FilterChip(cameraId == id, { cameraId = if (cameraId == id) null else id },
                    label = { Text(stringResource(R.string.select_camera_id, id)) })
            }
        }
    }
    DeviceCamera(front, false, 200, external = external, cameraId = cameraId)
}
