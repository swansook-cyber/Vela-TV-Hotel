package net.velalab.tvhotel

import org.junit.Assert.*
import org.junit.Test

class RoomServicePresentationTest {
    @Test fun everyApprovedMenuCategoryHasAThaiLabelAndAssetPath() {
        val ids = setOf("appetizer", "pasta", "pizza", "burger_sandwich", "spicy_thai_salad",
            "thai_soup", "spicy_thai_curry", "noodles", "rice", "thai_a_la_carte",
            "southern_thai_traditional_food", "drink", "dessert")
        assertEquals(ids, RoomServicePresentation.thaiCategoryNames.keys)
        ids.forEach { id ->
            assertTrue(RoomServicePresentation.thaiCategoryNames[id]!!.isNotBlank())
            assertEquals("room_service_art/$id.webp", RoomServicePresentation.artPath(id))
        }
        assertEquals("room_service_art/hero.webp", RoomServicePresentation.artPath("hero"))
        assertNull(RoomServicePresentation.artPath("unknown"))
    }

    @Test fun promotionalDisclaimerHasBothLanguages() {
        assertEquals("ภาพใช้เพื่อการโฆษณาเท่านั้น", RoomServicePresentation.DISCLAIMER_TH)
        assertEquals("Images for promotional purposes only", RoomServicePresentation.DISCLAIMER_EN)
    }
}
