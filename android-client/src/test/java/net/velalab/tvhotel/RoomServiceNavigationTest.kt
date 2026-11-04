package net.velalab.tvhotel

import org.junit.Assert.*
import org.junit.Test

class RoomServiceNavigationTest {
    @Test fun printedDiningCategoryOpensTheSameDetailEntry() {
        assertTrue(RoomServiceNavigation.isDiningCategory("Dining"))
        assertTrue(RoomServiceNavigation.isDiningCategory("dining"))
        assertTrue(RoomServiceNavigation.isDiningCategory(" Dining "))
        assertFalse(RoomServiceNavigation.isDiningCategory("Rooms"))
        assertFalse(RoomServiceNavigation.isDiningCategory(null))
    }

    @Test fun backFromMenuReturnsToTheActualEntryPoint() {
        val navigation = RoomServiceNavigation()
        navigation.enter(RoomServiceOrigin.HOME)
        assertEquals(RoomServiceOrigin.HOME, navigation.exitDestination())
        navigation.enter(RoomServiceOrigin.DINING)
        assertEquals(RoomServiceOrigin.DINING, navigation.exitDestination())
        navigation.enter(RoomServiceOrigin.HOME)
        assertEquals(RoomServiceOrigin.HOME, navigation.exitDestination())
    }
}
