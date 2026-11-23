package studio.rainy.realityview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

data class VisionFrame(val image: Bitmap, val detections: List<Detection>, val timestampNanos: Long)

class Vision(context: Context) : AutoCloseable {
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
        return VisionFrame(image, detections, frame.imageInfo.timestamp)
    }

    override fun close() = detector.close()
}
