package studio.rainy.realityview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

internal data class VisionFrame(val image: Bitmap, val detections: List<Detection>, val timestampNanos: Long, val lanes: List<LaneMark>)

internal class Vision(context: Context) : AutoCloseable {
    private val tracker = DetectionTracker()
    private val detector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
            .setScoreThreshold(0.45f)
            .setMaxResults(20)
            .setCategoryAllowlist(listOf("person", "car", "truck", "bus", "motorcycle", "bicycle", "traffic light", "stop sign"))
            .build()
    )

    fun analyze(frame: ImageProxy): VisionFrame {
        val original = frame.toBitmap()
        val rotation = frame.imageInfo.rotationDegrees
        val image = if (rotation == 0) original else Bitmap.createBitmap(
            original, 0, 0, original.width, original.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, false
        )
        val input = BitmapImageBuilder(image).build()
        val detections = try {
            detector.detect(input).detections().mapNotNull { item ->
                val category = item.categories().firstOrNull() ?: return@mapNotNull null
                val bounds = item.boundingBox()
                val box = FrameBox.normalize(
                    bounds.left, bounds.top, bounds.right, bounds.bottom, image.width, image.height
                ) ?: return@mapNotNull null
                Detection(category.categoryName(), category.score(), box)
            }
        } finally {
            input.close()
        }
        val timestamp = frame.imageInfo.timestamp
        val sample = Bitmap.createScaledBitmap(image, 128, 72, true)
        val pixels = IntArray(128 * 72)
        sample.getPixels(pixels, 0, 128, 0, 0, 128, 72)
        if (sample !== image) sample.recycle()
        for (index in pixels.indices) {
            val rgb = pixels[index]
            pixels[index] = ((rgb shr 16 and 255) * 77 + (rgb shr 8 and 255) * 150 +
                (rgb and 255) * 29) shr 8
        }
        return VisionFrame(image, tracker.update(detections, timestamp), timestamp,
            LaneMarks.find(pixels, 128, 72))
    }

    override fun close() = detector.close()
}
