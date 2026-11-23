package studio.rainy.realityview

import org.junit.Assert.*
import org.junit.Test

class DetectionTrackerTest {
    private val car = Detection("car", 0.9f, FrameBox(0.1f, 0.3f, 0.4f, 0.6f))

    @Test fun retainsIdForOverlappingFrames() {
        val tracker = DetectionTracker()
        val first = tracker.update(listOf(car), 0L).single()
        val shifted = car.copy(box = FrameBox(0.15f, 0.3f, 0.45f, 0.6f))
        assertEquals(first.trackId, tracker.update(listOf(shifted), 300_000_000L).single().trackId)
    }

    @Test fun expiresIdsAndReturnsOnlyCurrentObservations() {
        val tracker = DetectionTracker()
        val first = tracker.update(listOf(car), 0L).single()
        assertTrue(tracker.update(emptyList(), 400_000_000L).isEmpty())
        assertNotEquals(first.trackId, tracker.update(listOf(car), 1_000_000_000L).single().trackId)
    }

    @Test fun neverAssignsOneIdToTwoObjectsOrAcrossClasses() {
        val tracker = DetectionTracker()
        val first = tracker.update(listOf(car), 0L).single()
        val both = tracker.update(listOf(car, car.copy(score = 0.8f), car.copy(kind = "person")), 100_000_000L)
        assertEquals(3, both.map { it.trackId }.distinct().size)
        assertEquals(first.trackId, both.first().trackId)
        assertNotEquals(first.trackId, both.last().trackId)
    }
}
