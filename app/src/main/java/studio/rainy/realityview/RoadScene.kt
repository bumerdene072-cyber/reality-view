package studio.rainy.realityview

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private fun settings(context: Context) =
    context.getSharedPreferences("road_calibration", Context.MODE_PRIVATE)

private val calibrationKeys = listOf("height", "pitch", "hfov", "vfov")

fun readRoadCalibration(context: Context): RoadCalibration? {
    val values = calibrationKeys.map { settings(context).getString(it, "")?.toDoubleOrNull() }
    val (height, pitch, horizontal, vertical) = values
    return RoadCalibration(height ?: return null, pitch ?: return null,
        horizontal ?: return null, vertical ?: return null).takeIf { it.isValid() }
}

@Composable
fun RoadCalibrationSettings() {
    val context = LocalContext.current
    val storage = remember { settings(context) }
    var height by rememberSaveable { mutableStateOf(storage.getString("height", "").orEmpty()) }
    var pitch by rememberSaveable { mutableStateOf(storage.getString("pitch", "").orEmpty()) }
    var horizontal by rememberSaveable { mutableStateOf(storage.getString("hfov", "").orEmpty()) }
    var vertical by rememberSaveable { mutableStateOf(storage.getString("vfov", "").orEmpty()) }
    var message by rememberSaveable { mutableIntStateOf(0) }
    Text(stringResource(R.string.calibration), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.calibration_help))
    CalibrationField(height, { height = it; message = 0 }, R.string.height)
    CalibrationField(pitch, { pitch = it; message = 0 }, R.string.pitch)
    CalibrationField(horizontal, { horizontal = it; message = 0 }, R.string.hfov)
    CalibrationField(vertical, { vertical = it; message = 0 }, R.string.vfov)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val readings = listOf(height, pitch, horizontal, vertical)
                .map { it.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN }
            val calibration = RoadCalibration(readings[0], readings[1], readings[2], readings[3])
            if (calibration.isValid()) {
                val edit = storage.edit()
                calibrationKeys.zip(readings).forEach { (key, value) -> edit.putString(key, value.toString()) }
                edit.apply()
                message = R.string.calibration_saved
            } else message = R.string.calibration_invalid
        }) { Text(stringResource(R.string.save)) }
        OutlinedButton(onClick = {
            storage.edit().clear().apply()
            height = ""
            pitch = ""
            horizontal = ""
            vertical = ""
            message = R.string.calibration_cleared
        }) { Text(stringResource(R.string.clear)) }
    }
    if (message != 0) Text(stringResource(message))
}

@Composable
private fun CalibrationField(value: String, change: (String) -> Unit, label: Int) {
    OutlinedTextField(value, change, label = { Text(stringResource(label)) },
        singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun RoadScene(frame: VisionFrame, calibration: RoadCalibration, modifier: Modifier) {
    val traffic = frame.detections.mapNotNull { detection ->
        if (detection.kind == "traffic light" || detection.kind == "stop sign") null
        else calibration.project(detection.box)?.let { detection.kind to it }
    }.sortedByDescending { it.second.ahead }

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xff09121d), Color(0xff162f43))))
        fun scene(x: Double, y: Double, z: Double): Offset {
            val zoom = (1 / (1 + z * 0.045)).toFloat()
            return Offset(size.width / 2 + x.toFloat() * size.width * 0.075f * zoom,
                size.height * 0.22f + zoom * size.height * (0.67f - y.toFloat() * 0.075f))
        }
        fun polygon(tint: Color, a: Offset, b: Offset, c: Offset, d: Offset) {
            drawPath(Path().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(c.x, c.y)
                lineTo(d.x, d.y)
                close()
            }, tint)
        }
        polygon(Color(0xff1b3545), scene(-11.0, 0.0, 0.0), scene(11.0, 0.0, 0.0),
            scene(11.0, 0.0, 70.0), scene(-11.0, 0.0, 70.0))
        for (depth in 0..70 step 5) {
            val z = depth.toDouble()
            drawLine(Color(0xff2d5366), scene(-11.0, 0.0, z), scene(11.0, 0.0, z), 1.dp.toPx())
        }
        for (lateral in -10..10 step 2) {
            val x = lateral.toDouble()
            drawLine(Color(0xff2d5366), scene(x, 0.0, 0.0), scene(x, 0.0, 70.0), 1.dp.toPx())
        }
        fun symbol(x: Double, z: Double, width: Double, length: Double, height: Double, color: Color) {
            val left = x - width / 2
            val right = x + width / 2
            val near = (z - length / 2).coerceAtLeast(0.0)
            val far = z + length / 2
            val a = scene(left, height, near)
            val b = scene(right, height, near)
            val c = scene(right, height, far)
            val d = scene(left, height, far)
            polygon(color.copy(alpha = 0.55f), a, b, scene(right, 0.0, near), scene(left, 0.0, near))
            polygon(color.copy(alpha = 0.75f), b, c, scene(right, 0.0, far), scene(right, 0.0, near))
            polygon(color, a, b, c, d)
        }
        traffic.forEach { (kind, point) ->
            val vulnerable = kind in setOf("person", "bicycle", "motorcycle")
            symbol(point.right, point.ahead, if (vulnerable) 0.55 else 1.7,
                if (vulnerable) 0.8 else 3.1, if (vulnerable) 1.3 else 0.95,
                if (vulnerable) Color(0xffffc289) else Color(0xff79c7de))
        }
        symbol(0.0, 1.4, 1.7, 2.8, 0.8, Color(0xffd7eaff))
    }
}
