package studio.rainy.realityview

import org.junit.Assert.*
import org.junit.Test

class LaneMarksTest {
    @Test fun flatSurfaceHasNoLaneMarks() {
        assertTrue(LaneMarks.find(IntArray(128 * 72) { 60 }, 128, 72).isEmpty())
    }

    @Test fun findsTwoConvergingBrightStripes() {
        val width = 128
        val height = 72
        val pixels = IntArray(width * height) { 65 }
        val start = height * 55 / 100
        val end = height * 89 / 100
        for (y in start..end) {
            val left = 49 - 26 * (y - start) / (end - start)
            pixels[y * width + left] = 235
            pixels[y * width + width - 1 - left] = 235
        }
        assertEquals(2, LaneMarks.find(pixels, width, height).size)
    }
}
