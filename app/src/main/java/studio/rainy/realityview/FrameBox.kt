package studio.rainy.realityview

data class Detection(val kind: String, val score: Float, val box: FrameBox, val trackId: Long? = null)
data class FrameBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        fun normalize(left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int): FrameBox? {
            if (width <= 0 || height <= 0 || !listOf(left, top, right, bottom).all(Float::isFinite)) return null
            val box = FrameBox(
                (left / width).coerceIn(0f, 1f), (top / height).coerceIn(0f, 1f),
                (right / width).coerceIn(0f, 1f), (bottom / height).coerceIn(0f, 1f)
            )
            return box.takeIf { it.right > it.left && it.bottom > it.top }
        }
    }
}
