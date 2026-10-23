package com.parlor.core.model

import kotlin.test.*

class SerializationTest {
    @Test fun progressEnvelopeRoundTripsPortableDataAndNoEntitlement() {
        val board = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL)))
        val attempt = Attempt("attempt", "level", 1, AttemptMode.CAMPAIGN, board, hintsUsed = true,
            viewport = Viewport(2, 1, 2, "A"))
        val envelope = ProgressEnvelope(attempts = listOf(attempt), ledger = RewardLedger(earnedHints = 4, claims = setOf("r1")))
        val json = ParlorJson.encodeToString(envelope)
        assertEquals(envelope, ParlorJson.decodeFromString<ProgressEnvelope>(json))
        assertFalse(json.contains("entitlement", ignoreCase = true))
        assertFalse(json.contains("purchaseToken", ignoreCase = true))
        assertFalse(json.contains("consent", ignoreCase = true))
    }

    @Test fun unknownFutureFieldsAreRejected() {
        assertFails { ParlorJson.decodeFromString<ProgressEnvelope>("{\"schemaVersion\":1,\"unknown\":true}") }
    }
}
