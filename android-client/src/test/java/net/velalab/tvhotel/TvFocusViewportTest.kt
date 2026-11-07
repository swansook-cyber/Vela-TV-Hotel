package net.velalab.tvhotel

import org.junit.Assert.assertEquals
import org.junit.Test

class TvFocusViewportTest {
    @Test fun firstCardAlreadyInsideSafeAreaDoesNotMove() {
        assertEquals(0, TvFocusViewport.scrollDelta(250, 395, 40, 610))
    }

    @Test fun fourthCardScrollsFullyAboveBottomSafeArea() {
        assertEquals(155, TvFocusViewport.scrollDelta(620, 765, 40, 610))
    }

    @Test fun lastCardAlsoScrollsFullyAboveBottomSafeArea() {
        assertEquals(290, TvFocusViewport.scrollDelta(755, 900, 40, 610))
    }

    @Test fun returningToFirstCardScrollsBackUp() {
        assertEquals(-135, TvFocusViewport.scrollDelta(45, 190, 180, 610))
    }
}
