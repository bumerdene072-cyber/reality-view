package studio.rainy.realityview

/**
 * Associates same-class detections using box overlap. IDs are local to this
 * camera session, and missed detections are never returned as observations.
 */
internal class DetectionTracker {
    private data class Track(val detection: Detection, val lastSeen: Long)
    private val active = mutableMapOf<Long, Track>()
    private var nextId = 1L
    private var previousTimestamp = Long.MIN_VALUE

    fun update(detections: List<Detection>, timestampNanos: Long): List<Detection> {
        if (timestampNanos < previousTimestamp) active.clear()
        previousTimestamp = timestampNanos
        active.entries.removeAll { timestampNanos - it.value.lastSeen > 900_000_000L }

        val assigned = mutableSetOf<Long>()
        return detections.sortedByDescending(Detection::score).map { detection ->
            val match = active.asSequence()
                .filter { (id, track) -> id !in assigned && track.detection.kind == detection.kind }
                .map { (id, track) -> id to detection.box.overlap(track.detection.box) }
                .filter { (_, overlap) -> overlap >= 0.25f }
                .maxByOrNull { (_, overlap) -> overlap }
            val id = match?.first ?: nextId++
            val result = detection.copy(trackId = id)
            active[id] = Track(result, timestampNanos)
            assigned.add(id)
            result
        }
    }

    private fun FrameBox.overlap(other: FrameBox): Float {
        val intersection = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f) *
            (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        val union = (right - left) * (bottom - top) +
            (other.right - other.left) * (other.bottom - other.top) - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
