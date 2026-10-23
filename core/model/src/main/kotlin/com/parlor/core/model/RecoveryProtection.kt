package com.parlor.core.model

import kotlinx.serialization.Serializable

/** Independent recovery safeguards, never a replacement for the player's progress or purchase state. */
@Serializable
data class RecoveryProtection(
    val version: Int,
    val ledger: RewardLedger,
    val completionDates: Map<String, String>,
    val achievements: Set<String>,
)
