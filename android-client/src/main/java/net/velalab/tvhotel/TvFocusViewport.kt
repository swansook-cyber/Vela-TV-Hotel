package net.velalab.tvhotel

/** Pixel-space scroll adjustment after Android's own D-pad focus scroll. */
object TvFocusViewport {
    fun scrollDelta(cardTop: Int, cardBottom: Int, safeTop: Int, safeBottom: Int): Int {
        if (safeBottom <= safeTop) return 0
        return when {
            cardBottom > safeBottom -> cardBottom - safeBottom
            cardTop < safeTop -> cardTop - safeTop
            else -> 0
        }
    }
}
