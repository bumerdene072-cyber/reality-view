package studio.rainy.realityview

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SurroundWorkbench(directions: List<Int>, columns: Int) {
    val frames = remember { mutableStateMapOf<Int, Bitmap>() }
    val sources = remember { mutableMapOf<Int, String>() }
    val calibration = remember { mutableStateMapOf<Int, List<Offset>>() }
    var selected by remember { mutableIntStateOf(-1) }
    var corners by remember { mutableStateOf(emptyList<Offset>()) }
    var stitch by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!stitch, { stitch = false }, { Text(stringResource(R.string.surround_feeds)) })
        FilterChip(stitch, { stitch = true; selected = -1 }, { Text(stringResource(R.string.surround_stitch)) })
    }
    if (stitch) {
        if (frames.size == 4 && calibration.size == 4) StitchCanvas(frames, calibration)
        else Text(stringResource(R.string.surround_need_calibration, frames.size, calibration.size))
    }
    directions.indices.chunked(columns).forEach { group ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            group.forEach { index ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NetworkInput("surround_$index", directions[index], onAddressChanged = { address ->
                        if (sources.put(index, address) != address) {
                            calibration.remove(index)
                            frames.remove(index)
                            if (selected == index) {
                                selected = -1
                                corners = emptyList()
                            }
                        }
                    }) { frame ->
                        if (frame == null) frames.remove(index) else frames[index] = frame
                    }
                    OutlinedButton(enabled = frames[index] != null, onClick = {
                        selected = index
                        corners = calibration[index].orEmpty()
                        invalid = false
                        stitch = false
                    }) { Text(stringResource(R.string.surround_calibrate)) }
                }
            }
        }
    }

    val frame = frames[selected]
    if (selected >= 0 && frame != null) {
        val index = selected
        Text(stringResource(R.string.surround_tap, corners.size))
        Box(Modifier.fillMaxWidth().aspectRatio(frame.width.toFloat() / frame.height)
            .pointerInput(index, corners.size) {
                detectTapGestures { point ->
                    if (corners.size < 4) {
                        corners = corners + Offset((point.x / size.width).coerceIn(0f, 1f),
                            (point.y / size.height).coerceIn(0f, 1f))
                        invalid = false
                    }
                }
            }) {
            Image(frame.asImageBitmap(), stringResource(directions[index]), Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds)
            Canvas(Modifier.matchParentSize()) {
                corners.forEach { p -> drawCircle(Color(0xffffca71), 6.dp.toPx(), Offset(p.x * size.width, p.y * size.height)) }
            }
        }
        if (invalid) Text(stringResource(R.string.surround_invalid))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = corners.size == 4, onClick = {
                if (isClockwiseQuad(corners)) {
                    calibration[index] = corners
                    selected = -1
                } else invalid = true
            }) { Text(stringResource(R.string.save)) }
            OutlinedButton(onClick = { corners = emptyList(); invalid = false }) {
                Text(stringResource(R.string.clear))
            }
        }
    }
}

private fun isClockwiseQuad(points: List<Offset>): Boolean {
    if (points.size != 4) return false
    return points.indices.all { i ->
        val a = points[i]
        val b = points[(i + 1) % 4]
        val c = points[(i + 2) % 4]
        (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > 0.003f
    }
}

@Composable
private fun StitchCanvas(frames: Map<Int, Bitmap>, calibration: Map<Int, List<Offset>>) {
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xff0c1118))) {
        val sectors = arrayOf(
            floatArrayOf(0f, 0f, 1f, 0f, .64f, .36f, .36f, .36f),
            floatArrayOf(1f, 1f, 0f, 1f, .36f, .64f, .64f, .64f),
            floatArrayOf(0f, 1f, 0f, 0f, .36f, .36f, .36f, .64f),
            floatArrayOf(1f, 0f, 1f, 1f, .64f, .64f, .64f, .36f)
        )
        sectors.forEachIndexed { index, sector ->
            val frame = frames[index] ?: return@forEachIndexed
            val points = calibration[index] ?: return@forEachIndexed
            val source = FloatArray(8)
            val destination = FloatArray(8)
            for (i in 0..3) {
                source[i * 2] = points[i].x * frame.width
                source[i * 2 + 1] = points[i].y * frame.height
                destination[i * 2] = sector[i * 2] * size.width
                destination[i * 2 + 1] = sector[i * 2 + 1] * size.height
            }
            val matrix = Matrix()
            if (matrix.setPolyToPoly(source, 0, destination, 0, 4)) {
                val clip = Path().apply {
                    moveTo(destination[0], destination[1])
                    for (i in 1..3) lineTo(destination[i * 2], destination[i * 2 + 1])
                    close()
                }
                val canvas = drawContext.canvas.nativeCanvas
                val saved = canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(frame, matrix, paint)
                canvas.restoreToCount(saved)
            }
        }
        drawRect(Color(0xff1c3548), Offset(size.width * .36f, size.height * .36f),
            Size(size.width * .28f, size.height * .28f))
    }
}
