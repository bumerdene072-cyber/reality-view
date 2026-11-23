package studio.rainy.realityview

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SurroundCalibrationTest {
    @Test fun restoresClockwiseNormalizedCorners() {
        assertEquals(
            listOf(Offset(.2f, .1f), Offset(.8f, .1f), Offset(.9f, .9f), Offset(.1f, .9f)),
            parseCalibration("0.2,0.1,0.8,0.1,0.9,0.9,0.1,0.9")
        )
    }

    @Test fun rejectsCorruptAndCrossedCorners() {
        listOf(null, "", "NaN,0,1,0,1,1,0,1", "-1,0,1,0,1,1,0,1", "0,0,1,1,1,0,0,1")
            .forEach { assertNull(parseCalibration(it)) }
    }
}
