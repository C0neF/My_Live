package com.mylive.app.ui.screen.category

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategoryPlatformColorPolicyTest {

    @Test
    fun categoryPlatformAccentUsesSharedPlatformColors() {
        assertEquals(Color(0xFFFF5D23), categoryPlatformAccentColor("douyu"))
        assertEquals(Color(0xFFFFD736), categoryPlatformAccentColor("huya"))
        assertEquals(Color(0xFFF07775), categoryPlatformAccentColor("bilibili"))
        assertEquals(null, categoryPlatformAccentColor("douyin"))
    }

    @Test
    fun categoryPlatformChipUsesBrandBackgroundOnlyWhenSelected() {
        val selectedFallback = Color.Blue
        val unselectedFallback = Color.Gray

        assertEquals(
            Color(0xFFFF5D23),
            categoryPlatformChipContainerColor("douyu", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            unselectedFallback,
            categoryPlatformChipContainerColor("douyu", selectedFallback, unselectedFallback, isSelected = false)
        )
        assertEquals(
            selectedFallback,
            categoryPlatformChipContainerColor("douyin", selectedFallback, unselectedFallback, isSelected = true)
        )
    }

    @Test
    fun categoryPlatformChipUsesReadableContentColorOnBrandBackgrounds() {
        val selectedFallback = Color.White
        val unselectedFallback = Color.Gray

        assertEquals(
            Color.Black,
            categoryPlatformChipContentColor("douyu", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            Color.Black,
            categoryPlatformChipContentColor("bilibili", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            selectedFallback,
            categoryPlatformChipContentColor("douyin", selectedFallback, unselectedFallback, isSelected = true)
        )
        assertEquals(
            unselectedFallback,
            categoryPlatformChipContentColor("huya", selectedFallback, unselectedFallback, isSelected = false)
        )
    }

    @Test
    fun categoryScreenPropagatesPlatformAccentBeyondTopChips() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt").readText()

        assertTrue(source.contains("val activePlatformAccentColor ="))
        assertTrue(source.contains("color = titleColor"))
        assertTrue(source.contains("platformId = site.id"))
        assertTrue(source.contains("val pageAccentColor ="))
        assertTrue(source.contains("accentColor = pageAccentColor"))
        assertFalse(source.contains("color = MaterialTheme.colorScheme.primary,\n                                    shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)"))
    }

    @Test
    fun categoryScreenUsesViewModelSelectedSiteAsPlatformSelectionSource() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt").readText()

        assertTrue(source.contains("val selectedTab = selectedSiteIndex.coerceIn"))
        assertTrue(source.contains("pagerState.scrollToPage(selectedTab)"))
        assertFalse(source.contains("var selectedTab by rememberSaveable { mutableIntStateOf(0) }"))
    }
}
