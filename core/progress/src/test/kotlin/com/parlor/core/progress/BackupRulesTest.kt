package com.parlor.core.progress

import com.parlor.core.model.*
import kotlin.test.*

class BackupRulesTest {
    private val day = "2026-09-28"
    private val level = LevelDefinition("backup-traffic", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "backup fixture", 0, "original"))
    private val catalog = Catalog(levels = listOf(level))

    @Test fun allSixThemeChoicesRoundTripWithoutOwnershipAndUnknownIdsAreRejected() {
        for (theme in RoomTheme.entries) for (appearance in Appearance.entries) {
            val file = ProgressEnvelope(snapshotId = "theme-file", settings = PlayerSettings(theme = theme.id, appearance = appearance))
            BackupRules.validate(file, catalog)
            val restored = ParlorJson.decodeFromString<ProgressEnvelope>(ParlorJson.encodeToString(file))
            assertEquals(file, restored)
            assertEquals(RoomTheme.LINEN, RoomTheme.displayed(restored.settings.theme, false))
        }
        assertFails { BackupRules.validate(ProgressEnvelope(snapshotId = "unknown", settings = PlayerSettings(theme = "future-room")), catalog) }
        assertFails { ParlorJson.decodeFromString<PlayerSettings>("""{"appearance":"FUTURE"}""") }
        for (version in 2..3) {
            val legacy = ProgressEnvelope(schemaVersion = version, snapshotId = "legacy-theme")
            val raw = ParlorJson.encodeToString(legacy).replace(",\"appearance\":\"LIGHT\"", "")
            val imported = ParlorJson.decodeFromString<ProgressEnvelope>(raw)
            BackupRules.validate(imported, catalog)
            assertEquals(Appearance.LIGHT, imported.settings.appearance)
            assertFails { BackupRules.validate(imported.copy(settings = PlayerSettings(theme = "sage")), catalog) }
        }
    }

    @Test fun oldFilesCannotRefillSpentHintsViewsRetriesOrDatedClaims() {
        val backup = RewardLedger(latestResetDate = day, dailyHints = 3, earnedHints = 4, rewardedViews = 2,
            claims = setOf("earned-one", "earned-two"), dailyClaims = mapOf(day to DailyClaim(true, 1, 0)))
        var local = backup.copy(rewardedViews = 10, claims = backup.claims + "local-view", dailyClaims = mapOf(day to DailyClaim(true, 1, 1)), hintedAttemptIds = setOf("hinted"))
        repeat(7) { local = DailyRules.spendHint(local, day, false)!! }
        assertEquals(4L, local.earnedHintsSpent)
        repeat(3) { local = BackupRules.protect(local, backup, day) }
        assertEquals(0, local.dailyHints); assertEquals(0, local.earnedHints); assertEquals(10, local.rewardedViews)
        assertEquals(1, local.dailyClaims.getValue(day).consumedRetries)
        assertTrue("hinted" in local.hintedAttemptIds && "local-view" in local.claims)
        assertFalse(RewardRules.canOffer(local, day, Reward.Hints))
    }

    @Test fun aNewDeviceRestoresOneBalanceAndEarlierDatesCannotResetLocalAllowances() {
        val imported = RewardLedger(latestResetDate = day, dailyHints = 2, earnedHints = 7, earnedHintsSpent = 9, rewardedViews = 8)
        val restored = BackupRules.protect(RewardLedger(), imported, day)
        assertEquals(7, restored.earnedHints); assertEquals(2, restored.dailyHints)
        val next = DailyRules.spendHint(DailyRules.rollover(restored, "2026-09-29"), "2026-09-29", false)!!
        val oldFile = BackupRules.protect(next, imported, day)
        assertEquals("2026-09-29", oldFile.latestResetDate); assertEquals(2, oldFile.dailyHints)
        assertEquals(8, oldFile.rewardedViewsByDate[day]); assertEquals(0, oldFile.rewardedViews)
        assertEquals(7, oldFile.earnedHints)
    }

    @Test fun supportedEnvelopeIsStrictAndNeverContainsOwnershipOrDeviceConsent() {
        val a = Attempt.start("attempt", level)
        val good = ProgressEnvelope(snapshotId = "snapshot", createdAtMs = 1, attempts = listOf(a), attemptUpdatedAt = mapOf(a.id to 1L))
        BackupRules.validate(good, catalog)
        assertEquals(good, ParlorJson.decodeFromString<ProgressEnvelope>(ParlorJson.encodeToString(good)))
        for (bad in listOf(good.copy(schemaVersion = 5), good.copy(format = "other"), good.copy(contentVersion = 2),
            good.copy(attempts = listOf(a, a)), good.copy(attemptUpdatedAt = emptyMap()),
            good.copy(settings = PlayerSettings(sfxVolume = Float.NaN)), good.copy(ledger = RewardLedger(latestResetDate = "2026-02-30")),
            good.copy(ledger = RewardLedger(dailyHints = 4)), good.copy(profile = PlayerProfile(achievements = setOf("made-up"))),
            good.copy(profile = PlayerProfile(dailyCompletedDates = setOf(day), dailyTotals = mapOf(
                Puzzle.TRAFFIC_JAM to ScoreTotals(points = Long.MAX_VALUE), Puzzle.HANOI to ScoreTotals(points = Long.MAX_VALUE)))),
            good.copy(attempts = listOf(a.copy(id = "missing-timestamp"))))) assertFails { BackupRules.validate(bad, catalog) }
        val json = ParlorJson.encodeToString(good)
        for (field in listOf("purchaseToken", "entitlement", "consent")) assertFalse(json.contains(field, true))
    }

    @Test fun photoReferencesAreCompleteUniqueAndOnlyBelongToCampaignTiles() {
        val tileLevel = level.copy(id = "tiles", puzzle = Puzzle.SLIDING_TILES,
            initial = TilesBoard(3, listOf(1,2,3,4,5,6,7,0,8)), reference = listOf(SlideTile(8)))
        val image = PuzzlePhoto("a".repeat(64), listOf("AAAA"))
        val attempt = Attempt.start("photo-attempt", tileLevel).copy(photoId = image.id)
        val file = ProgressEnvelope(snapshotId = "photo-backup", attempts = listOf(attempt),
            attemptUpdatedAt = mapOf(attempt.id to 0L), photos = listOf(image))
        val content = Catalog(levels = listOf(tileLevel, level))
        BackupRules.validate(file, content) // Pixel/encoding validation is the Android codec's boundary.
        for (bad in listOf(file.copy(schemaVersion = 2), file.copy(photos = emptyList()),
            file.copy(photos = listOf(image, image)), file.copy(photos = listOf(image.copy(id = "../image"))),
            file.copy(photos = listOf(image.copy(jpeg = listOf("AA A")))),
            file.copy(photos = listOf(image.copy(jpeg = listOf("A".repeat(PhotoLimits.CHUNK + 4))))),
            file.copy(attempts = listOf(Attempt.start(attempt.id, level).copy(photoId = image.id)))))
            assertFails { BackupRules.validate(bad, content) }
    }

    @Test fun legacySchemaTwoWithoutPhotosStillValidates() {
        val old = ProgressEnvelope(schemaVersion = 2, snapshotId = "old-backup")
        BackupRules.validate(old, catalog)
        val json = ParlorJson.encodeToString(old).replace(",\"photos\":[]", "")
        val decoded = ParlorJson.decodeFromString<ProgressEnvelope>(json)
        assertEquals(2, decoded.schemaVersion); assertTrue(decoded.photos.isEmpty())
        BackupRules.validate(decoded, catalog)
    }
}
