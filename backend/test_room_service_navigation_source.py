"""Source wiring checks complement JVM route tests until a TV emulator is available."""
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).parent.parent


class RoomServiceNavigationSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = (ROOT / "android-client/src/main/java/net/velalab/tvhotel/MainActivity.kt").read_text(encoding="utf-8")

    def test_home_and_dining_each_open_room_service(self):
        home = self.activity.split("private fun showHome()", 1)[1].split("private fun showSection(", 1)[0]
        detail = self.activity.split("private fun showDetail(", 1)[1].split("private fun roomServiceMenu(", 1)[0]
        self.assertIn('action("ROOM SERVICE  ·  MENU")', home)
        self.assertIn("roomServiceNavigation.enter(RoomServiceOrigin.HOME)", home)
        self.assertIn("RoomServiceNavigation.isDiningCategory(mediaCategory)", detail)
        self.assertIn("roomServiceNavigation.enter(RoomServiceOrigin.DINING)", detail)
        self.assertIn("showRoomServiceCategories()", home)
        self.assertIn("showRoomServiceCategories()", detail)

    def test_back_uses_entry_origin_and_approved_seed_is_unchanged(self):
        menu = self.activity.split("private fun showRoomServiceCategories()", 1)[1].split("private fun showRoomServiceItems(", 1)[0]
        self.assertIn("returnFromRoomService()", menu)
        self.assertIn('page == "room_service") returnFromRoomService()', self.activity)
        content = json.loads((ROOT / "android-client/src/main/assets/hotel_content.json").read_text(encoding="utf-8"))
        numbers = [item["number"] for category in content["room_service"]["categories"] for item in category["items"]]
        self.assertEqual(len(content["room_service"]["categories"]), 13)
        self.assertEqual(len(numbers), 81)
        self.assertEqual(set(range(1, 84)) - set(numbers), {37, 38})
        self.assertEqual(content["quick_apps"], ["Netflix", "Disney+", "YouTube", "Prime Video", "iQIYI"])
        manifest = (ROOT / "android-client/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('android.intent.category.HOME', manifest)
        self.assertIn('android.intent.category.LEANBACK_LAUNCHER', manifest)

    def test_premium_menu_is_native_and_counts_come_from_content(self):
        categories = self.activity.split("private fun showRoomServiceCategories()", 1)[1].split("private fun showRoomServiceItems(", 1)[0]
        items = self.activity.split("private fun showRoomServiceItems(", 1)[1].split("private fun showRoomServiceItem(", 1)[0]
        self.assertIn('category.getJSONArray("items").length()', self.activity)
        self.assertIn("roomServiceCategoryCard(", categories)
        self.assertIn("roomServiceItemCard(", items)
        self.assertIn("roomServiceDisclaimer(root)", categories)
        self.assertIn("roomServiceDisclaimer(root)", items)
        self.assertIn("roomServiceArt.loadWhenVisible", categories)
        self.assertIn("roomServiceArt.loadWhenVisible", items)
        self.assertIn('item.getInt("price_thb")', self.activity)


if __name__ == "__main__":
    unittest.main()
