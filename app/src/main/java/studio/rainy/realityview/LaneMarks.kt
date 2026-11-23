package studio.rainy.realityview


internal data class LaneMark(val farX: Float, val nearX: Float, val farY: Float, val nearY: Float)

/** Looks for bright narrow stripes converging toward the center of the image. */
internal object LaneMarks {
    fun find(luma: IntArray, width: Int, height: Int): List<LaneMark> {
        if (width < 32 || height < 24 || luma.size / width != height) return emptyList()
        val farY = height * 55 / 100
        val nearY = height * 89 / 100
        return listOfNotNull(
            strongest(luma, width, height, farY, nearY, false),
            strongest(luma, width, height, farY, nearY, true)
        )
    }

    private fun strongest(
        luma: IntArray, width: Int, height: Int, farY: Int, nearY: Int, right: Boolean
    ): LaneMark? {
        var best = 0
        var mark: LaneMark? = null
        val step = (width / 64).coerceAtLeast(1)
        for (far in width * 31 / 100..width * 49 / 100 step step) {
            for (near in width * 9 / 100..width * 41 / 100 step step) {
                if (far - near < width / 12) continue
                val start = if (right) width - 1 - far else far
                val end = if (right) width - 1 - near else near
                var hits = 0
                var strength = 0
                for (sample in 0..10) {
                    val y = farY + (nearY - farY) * sample / 10
                    val x = start + (end - start) * sample / 10
                    val index = y * width + x
                    val contrast = luma[index] - maxOf(luma[index - 2], luma[index + 2])
                    if (luma[index] >= 155 && contrast >= 32) {
                        hits++
                        strength += contrast
                    }
                }
                if (hits >= 7 && strength > best) {
                    best = strength
                    mark = LaneMark(start.toFloat() / width, end.toFloat() / width,
                        farY.toFloat() / height, nearY.toFloat() / height)
                }
            }
        }
        return mark
    }
}
