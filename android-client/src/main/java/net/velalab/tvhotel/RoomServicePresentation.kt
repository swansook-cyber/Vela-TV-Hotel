package net.velalab.tvhotel

/** Presentation copy and illustrations are separate from the approved menu data. */
object RoomServicePresentation {
    val thaiCategoryNames = mapOf(
        "appetizer" to "อาหารเรียกน้ำย่อย",
        "pasta" to "พาสต้า",
        "pizza" to "พิซซ่า",
        "burger_sandwich" to "เบอร์เกอร์และแซนด์วิช",
        "spicy_thai_salad" to "สลัดไทยรสจัด",
        "thai_soup" to "ซุปไทย",
        "spicy_thai_curry" to "แกงไทยรสจัด",
        "noodles" to "ก๋วยเตี๋ยว",
        "rice" to "ข้าว",
        "thai_a_la_carte" to "อาหารไทยจานเดียว",
        "southern_thai_traditional_food" to "อาหารใต้สูตรดั้งเดิม",
        "drink" to "เครื่องดื่ม",
        "dessert" to "ของหวาน"
    )

    const val DISCLAIMER_TH = "ภาพใช้เพื่อการโฆษณาเท่านั้น"
    const val DISCLAIMER_EN = "Images for promotional purposes only"

    fun artPath(id: String): String? =
        if (id == "hero" || thaiCategoryNames.containsKey(id)) "room_service_art/$id.webp" else null
}
