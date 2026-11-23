package studio.rainy.realityview

import org.junit.Assert.*
import org.junit.Test

class SourceAndGeometryTest {
    @Test fun acceptsLocalCameraAddresses() {
        assertTrue(validRtspAddress(" rtsp://192.168.1.20:554/live "))
        assertTrue(validRtspAddress("RTSP://camera.local/main"))
        assertTrue(validRtspAddress("rtsp://[::1]:8554/live"))
    }

    @Test fun rejectsMalformedAddressesAndEmbeddedCredentials() {
        listOf("", "file:///etc/passwd", "http://camera.local/live", "rtsp:///live",
            "rtsp://user:pass@camera.local", "rtsp://camera.local:70000",
            "rtsp://camera.local/live#fragment").forEach { assertFalse(it, validRtspAddress(it)) }
    }

    @Test fun mapsAndClipsPixelBounds() {
        assertEquals(FrameBox(0f, 0.25f, 1f, 0.75f),
            FrameBox.normalize(-50f, 50f, 400f, 150f, 320, 200))
    }

    @Test fun rejectsInvalidBoxes() {
        assertNull(FrameBox.normalize(50f, 0f, 10f, 20f, 100, 100))
        assertNull(FrameBox.normalize(Float.NaN, 0f, 10f, 20f, 100, 100))
        assertNull(FrameBox.normalize(0f, 0f, 10f, 20f, 0, 100))
    }
}
