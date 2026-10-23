package com.parlor.core.data

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.*
import org.junit.Assert.*
import java.io.*

class PhotoPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ParlorDatabase
    private lateinit var repo: ProgressRepository
    private var pro = true
    private val day = "2026-09-28"
    private val level = LevelDefinition("photo-tiles", puzzle = Puzzle.SLIDING_TILES, tier = Tier.EASY, order = 1,
        initial = TilesBoard(3, listOf(1, 2, 3, 4, 5, 6, 0, 7, 8)), reference = listOf(SlideTile(7), SlideTile(8)),
        par = 2, parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000, provenance = Provenance("Parlor", "photo fixture", 0, "original"))
    private fun open() {
        db = Room.databaseBuilder(context, ParlorDatabase::class.java, "photo-test.db").build()
        repo = ProgressRepository(db, today = { day }, wallTime = { 1000 }, isPro = { pro })
        repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, day, listOf(LevelRef(level.id, 1))))))
    }
    @Before fun setup() { context.deleteDatabase("photo-test.db"); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase("photo-test.db") }
    private fun source(color: Int = Color.RED, width: Int = 96, height: Int = 64): ByteArray {
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try { b.eraseColor(color); ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { b.recycle() }
    }
    private fun photo(color: Int = Color.RED) = source(color).inputStream().use { PhotoCodec.normalize(it, context.cacheDir) }
    private suspend fun finish() { repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings())) }

    @Test fun squareCopySurvivesSourceDeletionReopenMovesUndoAndRestart() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level)
        val original = File(context.cacheDir, "selected-photo.png").apply { writeBytes(source()) }
        val image = original.inputStream().use { PhotoCodec.normalize(it, context.cacheDir) }; original.delete()
        val withPhoto = repo.setPhoto(level, a.id, image)
        assertEquals(a.copy(photoId = image.id), withPhoto)
        repo.move(level, a.id, SlideTile(7), 500)
        repo.undo(level, a.id, 0)
        val saved = repo.observeAttempt(a.id).first()!!
        db.close(); open()
        assertEquals(saved, repo.startOrResume(level)); assertArrayEquals(PhotoCodec.bytes(image), repo.photo(image.id))
        assertEquals(image.id, repo.restart(level, a.id).photoId)
        assertEquals(listOf(image.id), db.progress().photoIds())
    }

    @Test fun photoBackupRoundTripRecoversPreviousImageAndLegacyFilesWithoutImages() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level); val red = photo(); val blue = photo(Color.BLUE)
        repo.setPhoto(level, a.id, red)
        val backup = BackupFile.read(BackupFile.bytes(repo.exportProgress(PlayerSettings())).inputStream())
        assertEquals(4, backup.schemaVersion); assertEquals(listOf(red), backup.photos)
        repo.setPhoto(level, a.id, blue)
        repo.importProgress(backup, PlayerSettings()); finish()
        assertArrayEquals(PhotoCodec.bytes(red), repo.photo(red.id)); assertNull(repo.photo(blue.id))
        val recovery = repo.recoverySnapshot()!!; assertEquals(listOf(blue), recovery.photos)
        db.close(); open(); pro = false
        repo.importProgress(repo.recoverySnapshot()!!, PlayerSettings(), recovery = true); finish()
        assertEquals(blue.id, repo.startOrResume(level).photoId); assertNotNull(repo.photo(blue.id)); assertFalse(repo.hasPro())
        pro = true
        val schemaThree = ParlorJson.parseToJsonElement(BackupFile.bytes(backup.copy(schemaVersion = 3)).decodeToString()).jsonObject
        val legacyPhotoJson = JsonObject(schemaThree + ("settings" to JsonObject(schemaThree.getValue("settings").jsonObject - "appearance")))
        val legacyPhoto = BackupFile.read(legacyPhotoJson.toString().byteInputStream())
        assertEquals(Appearance.LIGHT, legacyPhoto.settings.appearance)
        repo.importProgress(legacyPhoto, PlayerSettings()); finish()
        assertEquals(red.id, repo.startOrResume(level).photoId)
        assertArrayEquals(PhotoCodec.bytes(red), repo.photo(red.id)); assertNull(repo.photo(blue.id))
        val legacy = backup.copy(schemaVersion = 2, attempts = backup.attempts.map { it.copy(photoId = null) }, photos = emptyList())
        repo.importProgress(legacy, PlayerSettings()); finish()
        assertNull(repo.startOrResume(level).photoId); assertTrue(db.progress().photoIds().isEmpty())
    }

    @Test fun missingCorruptUnexpectedOversizedOrDailyPhotosNeverReplaceProgress() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level); val image = photo()
        repo.setPhoto(level, a.id, image)
        val file = repo.exportProgress(PlayerSettings())
        val bytes = PhotoCodec.bytes(image)
        val broken = PhotoCodec.payload(bytes.copyOf(bytes.size - 1))
        val daily = repo.startDaily().attempt
        val dailyFile = repo.exportProgress(PlayerSettings())
        for (bad in listOf(file.copy(photos = emptyList()), file.copy(photos = listOf(image, image)),
            file.copy(schemaVersion = 2), file.copy(photos = listOf(image.copy(jpeg = listOf("@@@@")))),
            file.copy(photos = listOf(broken), attempts = file.attempts.map { it.copy(photoId = broken.id) }),
            dailyFile.copy(attempts = dailyFile.attempts.map { if (it.id == daily.id) it.copy(photoId = image.id) else it }))) {
            assertTrue(runCatching { repo.importProgress(bad, PlayerSettings()) }.isFailure)
            assertEquals(image.id, repo.startOrResume(level).photoId); assertNull(repo.recoverySnapshot())
        }
        val oversized = image.copy(jpeg = List(PhotoLimits.MAX_CHUNKS + 1) { "A".repeat(PhotoLimits.CHUNK) })
        assertTrue(runCatching { PhotoCodec.validate(oversized) }.isFailure)
        assertTrue(runCatching { PhotoCodec.validate(PhotoCodec.payload(bytes + byteArrayOf(0))) }.isFailure)
    }

    @Test fun failedImageAndImportTransactionsLeaveReferencesAndRecoveryIntact() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level); val image = photo(); val other = photo(Color.BLUE)
        repo.setPhoto(level, a.id, image)
        val incoming = repo.exportProgress(PlayerSettings()).let { it.copy(photos = listOf(other), attempts = it.attempts.map { a -> a.copy(photoId = other.id) }) }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo BEFORE INSERT ON photos BEGIN SELECT RAISE(ABORT, 'full disk fixture'); END")
        assertTrue(runCatching { repo.setPhoto(level, a.id, other) }.isFailure)
        assertEquals(image.id, repo.startOrResume(level).photoId)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_photo")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo_import BEFORE INSERT ON backup_state BEGIN SELECT RAISE(ABORT, 'full disk fixture'); END")
        assertTrue(runCatching { repo.importProgress(incoming, PlayerSettings()) }.isFailure)
        assertEquals(image.id, repo.startOrResume(level).photoId); assertNull(repo.photo(other.id)); assertNull(repo.recoverySnapshot())
    }

    @Test fun proAndDailyGatesKeepBoardsAndAllowFreePrivacyDeletionIncludingRecovery() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level); val image = photo()
        pro = false
        assertTrue(runCatching { repo.setPhoto(level, a.id, image) }.isFailure)
        pro = true; repo.setPhoto(level, a.id, image)
        val backup = repo.exportProgress(PlayerSettings())
        val daily = repo.startDaily().attempt
        assertTrue(runCatching { repo.setPhoto(level, daily.id, image) }.isFailure)
        repo.importProgress(backup, PlayerSettings()); finish(); pro = false
        val saved = repo.startOrResume(level)
        repo.deleteAllPhotos()
        assertEquals(saved.copy(photoId = null), repo.startOrResume(level))
        assertTrue(db.progress().photoIds().isEmpty())
        assertTrue(repo.recoverySnapshot()!!.photos.isEmpty()); assertTrue(repo.recoverySnapshot()!!.attempts.all { it.photoId == null })
        assertFalse(repo.hasPro()); assertEquals(0, repo.completionCount.first())
    }

    @Test fun resetAndReplacingPhotosPruneUnusedCopiesWhileRepeatedImagesAreDeduplicated() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level); val red = photo(); val blue = photo(Color.BLUE)
        repo.setPhoto(level, a.id, red); repo.setPhoto(level, a.id, blue)
        assertNull(repo.photo(red.id)); assertEquals(listOf(blue.id), db.progress().photoIds())
        level.reference.forEach { repo.move(level, a.id, it, 0) }
        val again = repo.restart(level, a.id)
        repo.setPhoto(level, again.id, blue); assertEquals(1, db.progress().photoIds().size)
        repo.setPhoto(level, again.id, null); assertNotNull(repo.photo(blue.id)) // Completed history still owns it.
        repo.resetProgress(Puzzle.SLIDING_TILES)
        assertTrue(db.progress().photoIds().isEmpty())
    }

    @Test fun privacyDeletionClearsStagedInputAndProcessStartupDiscardsUnconfirmedCopies() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level); repo.setPhoto(level, a.id, photo())
        val source = File(context.cacheDir, "photo-backup-fixture.json").apply { writeBytes(BackupFile.bytes(repo.exportProgress(PlayerSettings()))) }
        val staged = File(context.noBackupFilesDir, "backup-preview.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val preferences = SettingsRepository(context)
        try {
            val backup = ManualBackup(context, repo, preferences, scope)
            backup.select(android.net.Uri.fromFile(source))!!.join()
            assertEquals(BackupPhase.PREVIEW, backup.state.value.phase); assertEquals(1, backup.state.value.preview!!.photos)
            assertTrue(staged.exists())
            pro = false
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val held = launch(Dispatchers.Default) { repo.withCompletionCredits { entered.complete(Unit); release.await() } }
            entered.await()
            val screen = launch { backup.deletePhotos() }
            withTimeout(5_000) { while (scope.coroutineContext[Job]!!.children.none { it.isActive }) delay(10) }
            screen.cancelAndJoin() // Leaving the screen must not cancel the application's confirmed deletion.
            release.complete(Unit); held.join()
            withTimeout(5_000) { while (staged.exists() || backup.state.value.phase != BackupPhase.READY) delay(10) }
            assertFalse(staged.exists()); assertNull(repo.startOrResume(level).photoId)
            assertEquals(BackupPhase.READY, backup.state.value.phase); assertTrue(source.exists())
            staged.writeBytes(source.readBytes())
            ManualBackup(context, repo, preferences, scope)
            assertFalse(staged.exists())
        } finally { scope.cancel(); source.delete(); staged.delete() }
    }

    @Test fun cropOrientationAndMetadataRemovalMatchAllEightExifTransforms() {
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            for ((i, color) in listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).withIndex())
                drawRect((i % 2 * 32).toFloat(), (i / 2 * 32).toFloat(), (i % 2 * 32 + 32).toFloat(), (i / 2 * 32 + 32).toFloat(), Paint().apply { this.color = color })
        }
        val source = File(context.cacheDir, "orientation-photo.jpg")
        val expected = listOf(listOf(0,1,2,3), listOf(1,0,3,2), listOf(3,2,1,0), listOf(2,3,0,1),
            listOf(0,2,1,3), listOf(2,0,3,1), listOf(3,1,2,0), listOf(1,3,0,2))
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        try {
            for (orientation in 1..8) {
                source.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                ExifInterface(source).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    setAttribute(ExifInterface.TAG_USER_COMMENT, "private fixture metadata")
                    saveAttributes()
                }
                val bytes = PhotoCodec.validate(source.inputStream().use { PhotoCodec.normalize(it, context.cacheDir) })
                val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                try {
                    for (i in 0..3) {
                        val color = image.getPixel(if (i % 2 == 0) 8 else 56, if (i / 2 == 0) 8 else 56)
                        val target = colors[expected[orientation - 1][i]]
                        assertTrue("orientation $orientation corner $i", kotlin.math.abs(Color.red(color) - Color.red(target)) < 20 &&
                            kotlin.math.abs(Color.green(color) - Color.green(target)) < 20 && kotlin.math.abs(Color.blue(color) - Color.blue(target)) < 20)
                    }
                    val exif = ExifInterface(bytes.inputStream())
                    assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
                    assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                } finally { image.recycle() }
            }
        } finally { b.recycle(); source.delete() }
    }

    @Test fun inputAndPixelLimitsFailClosedAndLargePicturesAreDownsampled() {
        val large = source(Color.BLUE, 2400, 1600)
        val bytes = PhotoCodec.validate(large.inputStream().use { PhotoCodec.normalize(it, context.cacheDir) })
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue(bitmap.width in 1..1024); assertEquals(bitmap.width, bitmap.height); bitmap.recycle()
        val oversized = object : InputStream() {
            override fun read() = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int { b.fill(0, off, off + len); return len }
        }
        assertTrue(runCatching { PhotoCodec.normalize(oversized, context.cacheDir) }.isFailure)
        assertTrue(runCatching { PhotoCodec.normalize("not an image".byteInputStream(), context.cacheDir) }.isFailure)
        val encoded = PhotoCodec.bytes(photo())
        val sof = (0 until encoded.size - 1).first { encoded[it] == 0xff.toByte() && encoded[it + 1] == 0xc0.toByte() }
        for ((width, height) in listOf(1 to 16_385, 8192 to 8192)) {
            val oversizedPixels = encoded.copyOf()
            oversizedPixels[sof + 5] = (height shr 8).toByte(); oversizedPixels[sof + 6] = height.toByte()
            oversizedPixels[sof + 7] = (width shr 8).toByte(); oversizedPixels[sof + 8] = width.toByte()
            val error = runCatching { PhotoCodec.normalize(oversizedPixels.inputStream(), context.cacheDir) }.exceptionOrNull()
            assertEquals("Unsupported photo dimensions", error?.message)
        }
        assertTrue(context.cacheDir.listFiles()!!.none { it.name.startsWith("photo-") && it.extension == "input" })
    }

    @Test fun centerCropTransparencyAndAnimatedFirstFrameProduceOnlyTheExpectedPixels() {
        val b = Bitmap.createBitmap(192, 64, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.RED)
        Canvas(b).drawRect(64f, 0f, 128f, 64f, Paint().apply { color = Color.BLUE })
        fun check(bytes: ByteArray, expected: Int) {
            val jpeg = PhotoCodec.bytes(bytes.inputStream().use { PhotoCodec.normalize(it, context.cacheDir) })
            val saved = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            try {
                assertEquals(saved.width, saved.height)
                for ((x, y) in listOf(1 to 1, saved.width / 2 to saved.height / 2, saved.width - 2 to saved.height - 2)) {
                    val actual = saved.getPixel(x, y)
                    assertTrue(kotlin.math.abs(Color.red(actual) - Color.red(expected)) < 20 &&
                        kotlin.math.abs(Color.green(actual) - Color.green(expected)) < 20 && kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) < 20)
                }
            } finally { saved.recycle() }
        }
        try {
            check(ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray(), Color.BLUE)
            b.eraseColor(Color.TRANSPARENT)
            check(ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray(), Color.WHITE)
            val frames = InstrumentationRegistry.getInstrumentation().context.assets.open("photos/animated.gif").use { it.readBytes() }
            check(frames, Color.RED)
        } finally { b.recycle() }
    }

    @Test fun detailedImageChunksRoundTripAndAggregateStorageLimitRollsBackTheLastSelection() = runBlocking {
        val levels = (0 until 32).map { level.copy(id = "photo-budget-$it", order = it + 1) }
        repo.configure(Catalog(levels = levels), DailySchedule(listOf(ScheduleEpoch(1, day, listOf(LevelRef(levels.first().id, 1))))))
        repo.refreshDay()
        var refused = false
        for ((seed, currentLevel) in levels.withIndex()) {
            val b = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            val random = java.util.Random(seed.toLong())
            val pixels = IntArray(1024 * 1024) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
            b.setPixels(pixels, 0, 1024, 0, 0, 1024, 1024)
            val input = try { ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
            finally { b.recycle() }
            val image = input.inputStream().use { PhotoCodec.normalize(it, context.cacheDir) }
            assertTrue(image.jpeg.size > 1)
            val a = repo.startOrResume(currentLevel)
            val before = db.progress().photoBytes()
            val result = runCatching { repo.setPhoto(currentLevel, a.id, image) }
            if (result.isFailure) {
                assertEquals("Saved photo storage is full", result.exceptionOrNull()?.message)
                assertTrue(before + PhotoCodec.bytes(image).size > PhotoLimits.TOTAL_BYTES)
                assertEquals(before, db.progress().photoBytes()); assertNull(repo.observeAttempt(a.id).first()!!.photoId)
                assertNull(repo.photo(image.id)); refused = true; break
            }
        }
        assertTrue(refused)
        val full = repo.exportProgress(PlayerSettings())
        assertEquals(full, BackupFile.read(BackupFile.bytes(full).inputStream()))
        repo.importProgress(full, PlayerSettings()); finish()
        val recovery = repo.recoverySnapshot()!!
        assertEquals(full.photos, recovery.photos)
        assertTrue(db.progress().photoBytes() <= PhotoLimits.TOTAL_BYTES)
    }
}
