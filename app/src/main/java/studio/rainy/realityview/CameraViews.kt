package studio.rainy.realityview

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
@OptIn(androidx.camera.core.ExperimentalLensFacing::class)
fun DeviceCamera(
    front: Boolean,
    inference: Boolean,
    intervalMs: Long,
    roadCalibration: RoadCalibration? = null,
    external: Boolean = false
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    if (!permission) {
        Text(stringResource(R.string.camera_permission))
        Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant_camera)) }
        return
    }
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember(front, inference, attempt, external) { mutableIntStateOf(R.string.connecting) }
    var aiError by remember(front, inference, attempt, external) { mutableStateOf(false) }
    var frame by remember(front, inference, attempt, external) { mutableStateOf<VisionFrame?>(null) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }

    DisposableEffect(front, inference, intervalMs, attempt, owner, external) {
        val active = AtomicBoolean(true)
        val main = ContextCompat.getMainExecutor(context)
        val worker = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        var provider: ProcessCameraProvider? = null
        var cameraState: androidx.lifecycle.LiveData<CameraState>? = null
        var vision: Vision? = null
        var failed = false
        var last = -intervalMs
        val observer = Observer<CameraState> { state ->
            status = when {
                state.error != null -> R.string.camera_error
                state.type == CameraState.Type.OPEN -> R.string.connected
                else -> R.string.connecting
            }
            if (status != R.string.connected) frame = null
        }
        if (inference) analysis.setAnalyzer(worker) { image ->
            try {
                val now = SystemClock.elapsedRealtime()
                if (active.get() && !failed && now - last >= intervalMs) {
                    last = now
                    val engine = vision ?: Vision(context.applicationContext).also { vision = it }
                    val result = engine.analyze(image)
                    main.execute { if (active.get() && status == R.string.connected) frame = result }
                }
            } catch (_: RuntimeException) {
                failed = true
                main.execute { if (active.get()) { aiError = true; frame = null } }
            } finally {
                image.close()
            }
        }
        future.addListener({
            if (active.get()) {
                try {
                    val selector = when {
                        external -> CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL).build()
                        front -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    val ready = future.get().also { provider = it }
                    require(ready.hasCamera(selector))
                    val camera = if (inference) ready.bindToLifecycle(owner, selector, preview, analysis)
                    else ready.bindToLifecycle(owner, selector, preview)
                    cameraState = camera.cameraInfo.cameraState.also { it.observe(owner, observer) }
                } catch (_: Exception) {
                    status = R.string.camera_error
                }
            }
        }, main)
        onDispose {
            active.set(false)
            cameraState?.removeObserver(observer)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            worker.execute { vision?.close() }
            worker.shutdown()
        }
    }

    Text(stringResource(status), color = MaterialTheme.colorScheme.primary)
    if (status == R.string.camera_error || aiError) {
        if (aiError) Text(stringResource(R.string.ai_error))
        OutlinedButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
    }
    val current = frame
    // The preview remains attached while analysis frames are shown.
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
        if (inference && current != null) {
            if (roadCalibration == null || front) DetectionFrame(current, Modifier.matchParentSize())
            else RoadScene(current, roadCalibration, Modifier.matchParentSize())
        }
    }
    if (inference && current != null) {
        Text(stringResource(R.string.detections, current.detections.size))
        current.detections.groupBy { it.kind }.forEach { (kind, items) ->
            val label = when (kind) {
                "person" -> R.string.person
                "car" -> R.string.car
                "truck" -> R.string.truck
                "bus" -> R.string.bus
                "motorcycle" -> R.string.motorcycle
                "bicycle" -> R.string.bicycle
                "traffic light" -> R.string.traffic_light
                else -> R.string.stop_sign
            }
            Text(stringResource(R.string.detection_summary, stringResource(label), items.size,
                (items.maxOf { it.score } * 100).toInt()))
        }
    }
}

@Composable
private fun DetectionFrame(frame: VisionFrame, modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) { drawRect(Color.Black) }
        Image(frame.image.asImageBitmap(), stringResource(R.string.ai_frame), Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.matchParentSize()) {
            val scale = minOf(size.width / frame.image.width, size.height / frame.image.height)
            val width = frame.image.width * scale
            val height = frame.image.height * scale
            val x = (size.width - width) / 2
            val y = (size.height - height) / 2
            frame.detections.forEach { detection ->
                val box = detection.box
                drawRect(Color(0xff57dbf3),
                    Offset(x + box.left * width, y + box.top * height),
                    Size((box.right - box.left) * width, (box.bottom - box.top) * height),
                    style = Stroke(2.dp.toPx()))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun NetworkCamera(address: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var player by remember(address) { mutableStateOf<ExoPlayer?>(null) }
    var attempt by remember(address) { mutableIntStateOf(0) }
    var status by remember(address, attempt) { mutableIntStateOf(R.string.connecting) }
    var remaining by remember(address) { mutableIntStateOf(3) }
    var foreground by remember(owner) { mutableStateOf(false) }
    LaunchedEffect(address, foreground, status, attempt) {
        if (foreground && status == R.string.network_error && remaining > 0) {
            delay(1_000L shl (3 - remaining))
            remaining--
            attempt++
        }
    }
    DisposableEffect(address, attempt, owner) {
        fun stop() {
            player?.release()
            player = null
        }
        val lifecycle = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foreground = true
                    status = R.string.connecting
                    player = ExoPlayer.Builder(context).build().apply {
                        volume = 0f
                        addListener(object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                status = R.string.connected
                                remaining = 3
                            }
                            override fun onPlayerError(error: PlaybackException) { status = R.string.network_error }
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_BUFFERING) status = R.string.connecting
                                if (playbackState == Player.STATE_ENDED) status = R.string.network_error
                            }
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                if (isPlaying && status == R.string.connecting) status = R.string.connected
                            }
                        })
                        val source = RtspMediaSource.Factory().setForceUseRtpTcp(true)
                            .createMediaSource(MediaItem.fromUri(Uri.parse(address).normalizeScheme()))
                        setMediaSource(source)
                        prepare()
                        playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    stop()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(lifecycle)
        onDispose {
            owner.lifecycle.removeObserver(lifecycle)
            stop()
        }
    }
    Column(modifier) {
        Text(stringResource(status))
        if (status == R.string.network_error) {
            OutlinedButton(onClick = { remaining = 3; attempt++ }) { Text(stringResource(R.string.retry)) }
        }
        AndroidView(factory = { PlayerView(it).apply { useController = false } },
            update = { it.player = player },
            onReset = null, onRelease = { it.player = null },
            modifier = Modifier.fillMaxWidth().height(185.dp))
    }
}
