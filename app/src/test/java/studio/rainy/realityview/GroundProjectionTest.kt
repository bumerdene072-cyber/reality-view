package studio.rainy.realityview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroundProjectionTest {
    private val level = RoadCalibration(1.5, 0.0, 70.0, 60.0)

    @Test fun centeredObjectProjectsStraightAhead() {
        val point = requireNotNull(level.project(FrameBox(0.4f, 0.4f, 0.6f, 0.75f)))
        assertEquals(0.0, point.right, 0.0001)
        assertEquals(5.196152, point.ahead, 0.0001)
    }

    @Test fun rejectsSkyHorizonAndTruncatedObjects() {
        assertNull(level.project(FrameBox(0.4f, 0.1f, 0.6f, 0.3f)))
        assertNull(level.project(FrameBox(0.4f, 0.2f, 0.6f, 0.5f)))
        assertNull(level.project(FrameBox(0.4f, 0.2f, 0.6f, 1.0f)))
    }

    @Test fun pitchAndCalibrationChangeProjection() {
        val box = FrameBox(0.4f, 0.25f, 0.6f, 0.5f)
        assertNotNull(level.copy(pitchDegrees = 10.0).project(box))
        assertNull(level.copy(height = Double.NaN).project(box))
    }
}
