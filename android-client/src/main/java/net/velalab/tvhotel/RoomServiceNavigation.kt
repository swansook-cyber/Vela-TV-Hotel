package net.velalab.tvhotel

enum class RoomServiceOrigin { HOME, DINING }

/** Keeps the menu's exit destination independent of the content source. */
class RoomServiceNavigation {
    private var origin = RoomServiceOrigin.HOME

    fun enter(from: RoomServiceOrigin) { origin = from }

    fun exitDestination(): RoomServiceOrigin = origin

    companion object {
        fun isDiningCategory(category: String?): Boolean =
            category?.trim()?.equals("dining", ignoreCase = true) == true
    }
}
