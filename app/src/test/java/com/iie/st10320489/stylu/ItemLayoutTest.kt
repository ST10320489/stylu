package com.iie.st10320489.stylu;


import com.iie.st10320489.stylu.ui.wardrobe.ItemLayout
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import org.junit.Test
import org.junit.Assert.*


class ItemLayoutTest {

    @Test
    fun testItemLayoutCreation() {
        val item = WardrobeItem(
            itemId = 1,
            name = "Blue Shirt",
            imageUrl = "https://example.com/image.jpg",
            colour = "Blue",
            size = "M",
            category = "Tops",
            subcategory = "Shirt",
            weatherTag = null,
            timesWorn = 0
        )

        val layout = ItemLayout(
            item = item,
            x = 100f,
            y = 200f,
            scale = 1.0f,
            width = 150,
            height = 150
        )

        assertEquals("X position should be 100", 100f, layout.x)
        assertEquals("Y position should be 200", 200f, layout.y)
        assertEquals("Scale should be 1.0", 1.0f, layout.scale)
        assertEquals("Item should be Blue Shirt", "Blue Shirt", layout.item.name)
    }

    @Test
    fun testScaleConstraints() {
        val minScale = 0.5f
        val maxScale = 3.0f
        val testScale = 2.5f

        val isValid = testScale in minScale..maxScale
        assertTrue("Scale of 2.5 should be within valid range", isValid)
    }

    @Test
    fun testInvalidScaleTooSmall() {
        val minScale = 0.5f
        val testScale = 0.3f

        val isValid = testScale >= minScale
        assertFalse("Scale below minimum should be invalid", isValid)
    }

    @Test
    fun testInvalidScaleTooLarge() {
        val maxScale = 3.0f
        val testScale = 4.0f

        val isValid = testScale <= maxScale
        assertFalse("Scale above maximum should be invalid", isValid)
    }
}