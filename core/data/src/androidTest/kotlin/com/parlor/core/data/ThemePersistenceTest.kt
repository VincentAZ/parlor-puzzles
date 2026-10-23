package com.parlor.core.data

import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ThemePersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "theme-test.preferences_pb")
    private var job = SupervisorJob()
    private lateinit var store: androidx.datastore.core.DataStore<Preferences>
    private lateinit var settings: SettingsRepository
    private fun open() {
        job = SupervisorJob()
        store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        settings = SettingsRepository(store)
    }
    @Before fun setup() { file.delete(); open() }
    @After fun cleanup() = runBlocking { job.cancelAndJoin(); file.delete(); Unit }

    @Test fun legacyLightDefaultAndAllSavedChoicesSurviveStoreReopen() = runBlocking {
        store.edit { it[stringPreferencesKey("settings_v1")] = """{"theme":"linen","showTimer":true}""" }
        assertEquals(Appearance.LIGHT, settings.settings.first().appearance)
        for (theme in RoomTheme.entries) {
            settings.selectTheme(theme) { true }
            settings.update { it.copy(appearance = Appearance.DARK) }
            job.cancelAndJoin(); open()
            val saved = settings.settings.first()
            assertTrue(saved.showTimer); assertEquals(theme.id, saved.theme); assertEquals(Appearance.DARK, saved.appearance)
            assertEquals(RoomTheme.LINEN, RoomTheme.displayed(saved.theme, false))
            assertEquals(theme, RoomTheme.displayed(saved.theme, true))
        }
    }

    @Test fun proIsRecheckedInsideAQueuedSettingsWriteAndFreeCanSelectLinen() = runBlocking {
        settings.selectTheme(RoomTheme.WALNUT) { true }
        var pro = true
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val blocker = launch { store.updateData { entered.complete(Unit); release.await(); it } }
        entered.await()
        val selection = async { runCatching { settings.selectTheme(RoomTheme.SLATE) { pro } } }
        yield(); pro = false; release.complete(Unit); blocker.join()
        assertTrue(selection.await().isFailure)
        assertEquals(RoomTheme.WALNUT.id, settings.settings.first().theme)
        settings.selectTheme(RoomTheme.LINEN) { false }
        assertEquals(RoomTheme.LINEN.id, settings.settings.first().theme)
        settings.update { it.copy(appearance = Appearance.SYSTEM) }
        assertEquals(Appearance.SYSTEM, settings.settings.first().appearance)
    }

    @Test fun backupImportAndRecoveryKeepThemeChoiceWithoutChangingProgressOrGrantingPro() = runBlocking {
        val level = LevelDefinition("theme-traffic", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
            initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
            reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL,
            targetTimeMs = 60_000, provenance = Provenance("Parlor", "theme fixture", 0, "original"))
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        var pro = true
        val repo = ProgressRepository(db, today = { "2026-09-28" }, isPro = { pro })
        repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", listOf(LevelRef(level.id, 1))))))
        try {
            val attempt = repo.startOrResume(level)
            settings.selectTheme(RoomTheme.SAGE) { pro }; settings.update { it.copy(appearance = Appearance.DARK) }
            val file = repo.exportProgress(settings.settings.first())
            settings.selectTheme(RoomTheme.WALNUT) { pro }; settings.update { it.copy(appearance = Appearance.SYSTEM) }
            repo.importProgress(file, settings.settings.first())
            val imported = requireNotNull(repo.pendingBackupSettings()); settings.replace(imported); repo.finishBackupSettings(imported)
            assertEquals(RoomTheme.SAGE.id, settings.settings.first().theme)
            assertEquals(Appearance.DARK, settings.settings.first().appearance)
            val recovery = requireNotNull(repo.recoverySnapshot()); pro = false
            repo.importProgress(recovery, settings.settings.first(), recovery = true)
            val restored = requireNotNull(repo.pendingBackupSettings()); settings.replace(restored); repo.finishBackupSettings(restored)
            assertEquals(RoomTheme.WALNUT.id, settings.settings.first().theme)
            assertEquals(Appearance.SYSTEM, settings.settings.first().appearance)
            assertFalse(repo.hasPro()); assertEquals(RoomTheme.LINEN, RoomTheme.displayed(restored.theme, false))
            assertEquals(attempt, repo.observeAttempt(attempt.id).first())
        } finally { db.close() }
    }
}
