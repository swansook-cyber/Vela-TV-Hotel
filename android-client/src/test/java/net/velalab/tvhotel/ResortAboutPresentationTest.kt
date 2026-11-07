package net.velalab.tvhotel

import org.junit.Assert.assertEquals
import org.junit.Test

class ResortAboutPresentationTest {
    @Test fun previewKeepsWholeApprovedSentence() {
        assertEquals("A peaceful resort.", ResortAboutPresentation.preview("A peaceful resort. More approved detail."))
    }

    @Test fun textWithoutSentenceBreakRemainsWhole() {
        assertEquals("ข้อมูลรีสอร์ต", ResortAboutPresentation.preview("ข้อมูลรีสอร์ต"))
    }
}
