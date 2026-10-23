package com.parlor.puzzles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

/** Keep NavHost lifecycle work queued when Room completes on a transaction thread. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun navigationComposeRule(): ComposeTestRule =
    createEmptyComposeRule(effectContext = StandardTestDispatcher())
