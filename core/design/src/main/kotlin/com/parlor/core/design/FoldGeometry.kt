package com.parlor.core.design

import androidx.compose.ui.unit.IntRect

/** Window coordinates in physical pixels, independent of layout direction. */
data class WindowFold(val bounds: IntRect, val vertical: Boolean)

/** A separating crease is a line; a hinge can have area. Both partition content. */
fun foldPanes(window: IntRect, folds: List<WindowFold>, clearance: Int = 0, rtl: Boolean = false): List<IntRect> {
    var panes = listOf(window)
    for (fold in folds) {
        panes = panes.flatMap { pane ->
            val b = fold.bounds
            val crosses = if (fold.vertical) b.top < pane.bottom && b.bottom > pane.top && b.left < pane.right && b.right > pane.left
                else b.left < pane.right && b.right > pane.left && b.top < pane.bottom && b.bottom > pane.top
            if (!crosses) listOf(pane) else if (fold.vertical) listOf(
                IntRect(pane.left, pane.top, (b.left - clearance).coerceIn(pane.left, pane.right), pane.bottom),
                IntRect((b.right + clearance).coerceIn(pane.left, pane.right), pane.top, pane.right, pane.bottom),
            ) else listOf(
                IntRect(pane.left, pane.top, pane.right, (b.top - clearance).coerceIn(pane.top, pane.bottom)),
                IntRect(pane.left, (b.bottom + clearance).coerceIn(pane.top, pane.bottom), pane.right, pane.bottom),
            )
        }.filter { it.width > 0 && it.height > 0 }
    }
    return panes.sortedWith(compareBy<IntRect> { it.top }.thenBy { if (rtl) -it.right else it.left })
}

/** Ties prefer the top/reading-start pane; no preference depends on puzzle coordinates. */
fun largestPane(panes: List<IntRect>): IntRect? = panes.maxByOrNull { it.width.toLong() * it.height }
