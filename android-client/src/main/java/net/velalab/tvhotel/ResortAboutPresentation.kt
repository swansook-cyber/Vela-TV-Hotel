package net.velalab.tvhotel

/** Keep the approved first sentence intact in the Home card; the full copy opens on OK. */
object ResortAboutPresentation {
    fun preview(description: String): String {
        val sentenceEnd = description.indexOf(". ")
        return if (sentenceEnd >= 0) description.substring(0, sentenceEnd + 1) else description
    }
}
