package studio.rainy.realityview

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class GroundPoint(val right: Double, val ahead: Double)

data class RoadCalibration(
    val height: Double,
    val pitchDegrees: Double,
    val horizontalFov: Double,
    val verticalFov: Double
) {
    fun isValid(): Boolean = height in 0.3..5.0 && pitchDegrees in -20.0..45.0 &&
        horizontalFov in 25.0..140.0 && verticalFov in 20.0..130.0

    fun project(box: FrameBox): GroundPoint? {
        if (!isValid() || box.bottom >= 0.995f) return null
        val pitch = pitchDegrees * PI / 180
        val x = (box.left + box.right - 1) * tan(horizontalFov * PI / 360)
        val y = (box.bottom * 2 - 1) * tan(verticalFov * PI / 360)
        val down = sin(pitch) + y * cos(pitch)
        val forward = cos(pitch) - y * sin(pitch)
        if (down < 0.03 || forward <= 0) return null
        val scale = height / down
        return GroundPoint(x * scale, forward * scale)
            .takeIf { it.ahead in 1.0..70.0 && it.right in -15.0..15.0 }
    }
}
