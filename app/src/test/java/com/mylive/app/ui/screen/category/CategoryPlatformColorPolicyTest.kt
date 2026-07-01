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
    fun categoryScreenKeepsHeaderTitleNeutralWhilePropagatingPlatformAccent() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt").readText()

        assertTrue(source.contains("color = MaterialTheme.colorScheme.onSurface"))
        assertFalse(source.contains("label = \"categoryTitleColor\""))
        assertFalse(source.contains("color = titleColor"))
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

    @Test
    fun categoryPagerSelectionUsesLatestSelectedTabInsideLongLivedCollector() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt").readText()
        val selectionSource = source.substringAfter("fun selectCategorySite(index: Int)")
            .substringBefore("LaunchedEffect(selectedTab, siteTabs.size)")

        assertTrue(source.contains("val latestSelectedTab by rememberUpdatedState(selectedTab)"))
        assertTrue(selectionSource.contains("if (latestSelectedTab == boundedIndex) return"))
        assertFalse(selectionSource.contains("if (selectedTab == boundedIndex) return"))
    }

    @Test
    fun categoryParentTabBackgroundSwitchesImmediatelyToAvoidDualActiveState() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt").readText()

        assertFalse(source.contains("label = \"parentTabBgColor\""))
        assertTrue(source.contains("categoryParentTabBackgroundColor("))
    }

    @Test
    fun categoryListsUseStableKeysAndContentTypes() {
        val source = File("src/main/java/com/mylive/app/ui/screen/category/CategoryScreen.kt").readText()

        assertTrue(source.contains("itemsIndexed(\n                items = categories,"))
        assertTrue(source.contains("key = { _, category -> category.id }"))
        assertTrue(source.contains("contentType = { _, _ -> \"category_parent\" }"))
        assertTrue(source.contains("items(\n                    items = subCategories,"))
        assertTrue(source.contains("key = { it.id }"))
        assertTrue(source.contains("contentType = { \"category_subcategory\" }"))
    }
}
