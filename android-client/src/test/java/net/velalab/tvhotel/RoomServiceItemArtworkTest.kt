package net.velalab.tvhotel

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomServiceItemArtworkTest {
    @Test fun firstMiddleAndLastPublishedIdsUseDistinctFileKeys() {
        assertEquals("item_001", RoomServiceItemArtwork.itemKey(1))
        assertEquals("item_043", RoomServiceItemArtwork.itemKey(43))
        assertEquals("item_083", RoomServiceItemArtwork.itemKey(83))
    }
}
