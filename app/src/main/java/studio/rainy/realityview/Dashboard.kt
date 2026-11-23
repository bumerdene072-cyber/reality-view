package studio.rainy.realityview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun RainyNavigation(page: String, select: (String) -> Unit, wide: Boolean) {
    if (wide) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(236.dp).fillMaxHeight()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Brand()
                Spacer(Modifier.height(14.dp))
                Screen.entries.forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(screen.label)) },
                        selected = page == screen.name,
                        onClick = { select(screen.name) }
                    )
                }
            }
        }
    } else {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(start = 16.dp, top = 10.dp)) { Brand() }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Screen.entries.forEach { screen ->
                        FilterChip(selected = page == screen.name, onClick = { select(screen.name) },
                            label = { Text(stringResource(screen.label)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.ic_rainy), contentDescription = null, modifier = Modifier.size(38.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun RealityDashboard(openCamera: () -> Unit, openReality: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xff152536), Color(0xff101721))))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().heightIn(min = 105.dp, max = 145.dp)) {
            val horizon = size.height * 0.12f
            val middle = size.width / 2
            val left = size.width * 0.15f
            val right = size.width * 0.85f
            val surface = Path().apply {
                moveTo(middle - size.width * 0.08f, horizon)
                lineTo(middle + size.width * 0.08f, horizon)
                lineTo(right, size.height)
                lineTo(left, size.height)
                close()
            }
            drawPath(surface, Brush.verticalGradient(listOf(Color(0xff254859), Color(0xff192a3c))))
            for (index in -2..2) {
                drawLine(Color(0xff426475), Offset(middle + index * size.width * 0.035f, horizon),
                    Offset(middle + index * size.width * 0.16f, size.height), 1.dp.toPx())
            }
            for (index in 1..5) {
                val t = index / 5f
                val depth = t * t
                val span = size.width * (0.08f + depth * 0.27f)
                val y = horizon + depth * (size.height - horizon)
                drawLine(Color(0xff426475), Offset(middle - span, y), Offset(middle + span, y), 1.dp.toPx())
            }
        }
        Button(onClick = openReality, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_ai)) }
        OutlinedButton(onClick = openCamera, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_camera)) }
        Text(stringResource(R.string.parked), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
