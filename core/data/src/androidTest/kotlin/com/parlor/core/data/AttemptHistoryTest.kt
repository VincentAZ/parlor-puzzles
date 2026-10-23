package com.parlor.core.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.core.progress.Scoring
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class AttemptHistoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ParlorDatabase
    private lateinit var repo: ProgressRepository
    private val health = StorageHealth()
    private var date = "2026-09-28"
    private var now = 1_000L
    private var pro = true
    private var ids = 0
    private val traffic = LevelDefinition("history-traffic", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL), TrafficPiece("B", 2, 0, 3, Axis.VERTICAL))),
        reference = listOf(Translate("B", 3), Translate("A", 6)), par = 2, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "fixture", 1, "original"))
    private val jugs = LevelDefinition("history-jugs", puzzle = Puzzle.WATER_JUGS, tier = Tier.EASY, order = 1,
        initial = JugsBoard(listOf(3, 5), listOf(0, 0), 3), reference = listOf(FillJug(0)),
        par = 1, parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000, provenance = traffic.provenance)
    private val catalog = Catalog(levels = listOf(traffic, jugs))
    private val schedule = DailySchedule(listOf(ScheduleEpoch(1, date, listOf(LevelRef(traffic.id, 1)))))
    private fun open() {
        db = Room.databaseBuilder(context, ParlorDatabase::class.java, "history-test.db").build()
        repo = ProgressRepository(db, { date }, { now }, { "history-${(ids++).toString().padStart(4, '0')}" }, { pro }, health)
        repo.configure(catalog, schedule)
    }
    @Before fun setup() { context.deleteDatabase("history-test.db"); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase("history-test.db") }
    private suspend fun solve(level: LevelDefinition = traffic): Attempt {
        val start = repo.startOrResume(level)
        level.reference.forEach { repo.move(level, start.id, it, 100) }
        return requireNotNull(repo.observeAttempt(start.id).first())
    }

    @Test fun keysetPagesReachEveryTieWithoutRepeatsEvenWhenNewCompletionsArrive() = runBlocking {
        val completed = List(47) { solve().id }.reversed()
        val first = repo.historyPage(HistoryQuery()).first()
        assertEquals(20, first.entries.size)
        assertEquals(completed.take(20), first.entries.map { it.attempt.id })
        now += 1; val newest = solve().id
        val second = repo.historyPage(HistoryQuery(before = first.next)).first()
        val third = repo.historyPage(HistoryQuery(before = second.next)).first()
        assertEquals(completed, (first.entries + second.entries + third.entries).map { it.attempt.id })
        assertNull(third.next)
        assertEquals(newest, repo.historyPage(HistoryQuery()).first().entries.first().attempt.id)
        db.close(); open()
        assertEquals(second, repo.historyPage(HistoryQuery(before = first.next)).first())
    }

    @Test fun filtersKeepCampaignDailyAndPracticeSeparateAndExcludeUnfinishedOrDemos() = runBlocking {
        val campaign = solve(); val other = solve(jugs)
        pro = false
        val scored = repo.startDaily(); traffic.reference.forEach { repo.move(traffic, scored.attempt.id, it, 100) }
        val practice = repo.startDaily(); traffic.reference.forEach { repo.move(traffic, practice.attempt.id, it, 100) }
        repo.startOrResume(traffic)
        db.progress().saveAttempt(AttemptRow("demo", traffic.id, AttemptMode.DEMONSTRATION.name, true, now,
            ParlorJson.encodeToString(campaign.copy(id = "demo", mode = AttemptMode.DEMONSTRATION))))
        pro = true
        val all = repo.historyPage(HistoryQuery()).first()
        assertEquals(4, all.entries.size)
        assertEquals(setOf(campaign.id, other.id), repo.historyPage(HistoryQuery(kind = HistoryKind.CAMPAIGN)).first().entries.map { it.attempt.id }.toSet())
        assertEquals(listOf(other.id), repo.historyPage(HistoryQuery(Puzzle.WATER_JUGS)).first().entries.map { it.attempt.id })
        val daily = repo.historyPage(HistoryQuery(Puzzle.TRAFFIC_JAM, HistoryKind.DAILY)).first().entries
        assertEquals(setOf(scored.attempt.id, practice.attempt.id), daily.map { it.attempt.id }.toSet())
        assertNull(daily.first { it.attempt.id == practice.attempt.id }.result)
        assertEquals(3, daily.first { it.attempt.id == scored.attempt.id }.result!!.stars)
    }

    @Test fun frozenDailyTargetsAndActualCompletionDateSurviveCatalogChangesAndReopen() = runBlocking {
        val campaign = solve()
        val start = repo.startDaily()
        repo.useHint(traffic, start.attempt.id, 0)
        date = "2026-09-29"
        traffic.reference.forEach { repo.move(traffic, start.attempt.id, it, 20_000) }
        db.close(); open()
        val revised = traffic.copy(revision = 2, targetTimeMs = 1_000, par = 1)
        repo.configure(catalog.copy(levels = listOf(revised, jugs)), DailySchedule(listOf(ScheduleEpoch(2, date, listOf(LevelRef(revised.id, 2))))))
        val entries = repo.historyPage(HistoryQuery()).first().entries
        val daily = entries.first { it.attempt.id == start.attempt.id }
        assertEquals("2026-09-28", daily.attempt.challengeDate)
        assertEquals("2026-09-29", daily.completedDate)
        assertEquals(traffic, daily.level)
        assertEquals(2, daily.result!!.stars); assertEquals(250, daily.result!!.points)
        val older = entries.first { it.attempt.id == campaign.id }
        assertNull(older.level); assertNull(older.result)
        assertEquals(200L, older.attempt.activeTimeMs)
    }

    @Test fun pageDecodingIsBoundedAndUnreadableRowsAreErrorsInsteadOfEmptyHistory() = runBlocking {
        val completed = List(22) { solve().id }
        val oldest = db.progress().attempt(completed.first())!!
        db.progress().saveAttempt(oldest.copy(json = "{"))
        val page = repo.historyPage(HistoryQuery()).first()
        assertEquals(20, page.entries.size); assertNotNull(page.next)
        assertNull(health.state.value.issue)
        assertTrue(runCatching { repo.historyPage(HistoryQuery(before = page.next)).first() }.isFailure)
        assertEquals(StorageProblem.DAMAGED, health.state.value.issue!!.problem)
        db.progress().saveAttempt(oldest); health.retry()
        assertEquals(2, repo.historyPage(HistoryQuery(before = page.next)).first().entries.size)
    }

    @Test fun livePagesReflectResetAndProLossAndFreeDoesNotOpenTheDatabase() = runBlocking {
        solve()
        val channel = Channel<HistoryPage>(Channel.UNLIMITED)
        val observer = launch { repo.historyPage(HistoryQuery()).collect { channel.send(it) } }
        try {
            assertEquals(1, withTimeout(5_000) { channel.receive() }.entries.size)
            repo.resetProgress()
            assertTrue(withTimeout(5_000) { channel.receive() }.entries.isEmpty())
            pro = false
            solve()
            var next: HistoryPage
            do { next = withTimeout(5_000) { channel.receive() } } while (!next.proRequired)
            assertTrue(next.entries.isEmpty())
        } finally { observer.cancelAndJoin(); channel.close() }
        db.close()
        assertTrue(repo.historyPage(HistoryQuery()).first().proRequired)
    }

    @Test fun importedHistoryKeepsDatesCountersAndNeverMutatesBestTotals() = runBlocking {
        val saved = solve()
        val before = repo.exportProgress(PlayerSettings())
        val bests = repo.bests.first(); val player = repo.player.first()
        repo.importProgress(before, PlayerSettings())
        repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings()))
        db.close(); open()
        val entry = repo.historyPage(HistoryQuery()).first().entries.single()
        assertEquals(saved, entry.attempt); assertEquals(date, entry.completedDate)
        assertEquals(Scoring.result(traffic, saved), entry.result)
        assertEquals(bests, repo.bests.first()); assertEquals(player, repo.player.first())
    }
}
