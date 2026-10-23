package com.parlor.puzzles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.game.TrafficJamEngine
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.TrafficBoard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BoardAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactBoardAtDoubleFontSizeProvidesLargeTargetsAndAccessibleMoves() {
        var board by mutableStateOf(TrafficJamBoard(pieces = listOf(
            TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL), TrafficPiece("B", 2, 0, 3, Axis.VERTICAL),
        )))
        var viewport by mutableStateOf(Viewport())
        compose.setContent {
            ParlorTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    Box(Modifier.width(280.dp)) {
                        TrafficBoard(board, viewport, true, { viewport = it }, { board = TrafficJamEngine.apply(board, it) as TrafficJamBoard })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Board overview. Tap to move the magnified view.").assertExists()
        compose.onNodeWithTag("piece-B").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("piece-A").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val actions = compose.onNodeWithTag("piece-B").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == "Move piece B to row 4" }.action() }
        compose.runOnIdle { assertEquals(3, board.pieces.single { it.id == "B" }.y) }
    }
}
