package com.parlor.core.design

import androidx.compose.ui.unit.IntRect
import kotlin.test.*

class FoldGeometryTest {
    private val window = IntRect(10, 20, 1010, 620)
    @Test fun noFoldAndOutOfWindowFeaturesPreserveTheWholeRegion() {
        assertEquals(listOf(window), foldPanes(window, emptyList()))
        assertEquals(listOf(window), foldPanes(window, listOf(WindowFold(IntRect(1200, 0, 1220, 900), true))))
    }
    @Test fun zeroWidthBookFoldAndHingeLeaveClearanceAndStableReadingOrder() {
        val line = WindowFold(IntRect(510, 0, 510, 900), true)
        val expected = listOf(IntRect(10, 20, 502, 620), IntRect(518, 20, 1010, 620))
        assertEquals(expected, foldPanes(window, listOf(line), 8))
        assertEquals(expected.reversed(), foldPanes(window, listOf(line), 8, rtl = true))
        assertEquals(expected[0], largestPane(expected))
        assertEquals(expected[1], largestPane(expected.reversed()))
        val hinge = line.copy(bounds = IntRect(500, 0, 520, 900))
        assertEquals(listOf(IntRect(10, 20, 492, 620), IntRect(528, 20, 1010, 620)), foldPanes(window, listOf(hinge), 8))
    }
    @Test fun horizontalFoldPrefersTopUnlessBottomIsLarger() {
        val panes = foldPanes(window, listOf(WindowFold(IntRect(0, 200, 1500, 220), false)))
        assertEquals(listOf(IntRect(10, 20, 1010, 200), IntRect(10, 220, 1010, 620)), panes)
        assertEquals(panes.last(), largestPane(panes))
    }
    @Test fun multipleFoldsPartitionEveryIntersectedRegionAndDropCoveredPanes() {
        val panes = foldPanes(window, listOf(WindowFold(IntRect(510, 0, 510, 900), true),
            WindowFold(IntRect(0, 320, 1500, 320), false)))
        assertEquals(4, panes.size)
        assertEquals(600_000L, panes.sumOf { it.width.toLong() * it.height })
        assertEquals(emptyList(), foldPanes(window, listOf(WindowFold(IntRect(0, 0, 1200, 900), true))))
    }
}
