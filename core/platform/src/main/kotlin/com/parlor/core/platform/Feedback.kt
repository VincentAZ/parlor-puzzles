package com.parlor.core.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import com.parlor.core.model.*

enum class Cue { PICKUP, PLACE, SLIDE, ILLEGAL, UNDO, HINT, STAR, COMPLETE, TIER }

fun moveCue(move: Move): Cue = when (move) {
    is TransferDisk, is Toggle, is FillJug, is PlaceQueen, is RemoveQueen, is JumpPeg, is KnightStep -> Cue.PLACE
    else -> Cue.SLIDE
}

class Feedback(context: Context) : AutoCloseable {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val ready = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val sounds: Map<Cue, Int>
    private val wood: Map<Cue, Int>
    private val metal: Map<Cue, Int>
    private val water: Map<Cue, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        sounds = mapOf(Cue.PICKUP to R.raw.pickup, Cue.PLACE to R.raw.place, Cue.SLIDE to R.raw.slide,
            Cue.ILLEGAL to R.raw.illegal, Cue.UNDO to R.raw.undo, Cue.HINT to R.raw.hint,
            Cue.STAR to R.raw.star, Cue.COMPLETE to R.raw.complete, Cue.TIER to R.raw.tier)
            .mapValues { pool.load(context, it.value, 1) }
        fun material(pickup: Int, place: Int, slide: Int, undo: Int, illegal: Int) =
            mapOf(Cue.PICKUP to pickup, Cue.PLACE to place, Cue.SLIDE to slide, Cue.UNDO to undo, Cue.ILLEGAL to illegal)
                .mapValues { pool.load(context, it.value, 1) }
        wood = material(R.raw.wood_pickup, R.raw.wood_place, R.raw.wood_slide, R.raw.wood_undo, R.raw.wood_illegal)
        metal = material(R.raw.metal_pickup, R.raw.metal_place, R.raw.metal_slide, R.raw.metal_undo, R.raw.metal_illegal)
        water = material(R.raw.water_pickup, R.raw.water_place, R.raw.water_slide, R.raw.water_undo, R.raw.water_illegal)
    }
    fun play(cue: Cue, settings: PlayerSettings, view: View? = null, puzzle: Puzzle = Puzzle.TRAFFIC_JAM) {
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val palette = when (puzzle) {
            Puzzle.HANOI, Puzzle.PEG_SOLITAIRE, Puzzle.PANCAKE_FLIP, Puzzle.QUEENS, Puzzle.KNIGHTS_TOUR -> wood
            Puzzle.SWITCH_GRID -> metal
            Puzzle.WATER_JUGS, Puzzle.CROSSINGS -> water
            else -> sounds
        }
        val sound = palette[cue] ?: sounds.getValue(cue)
        if (sound in ready && settings.sfxVolume > 0) {
            val volume = settings.sfxVolume.coerceIn(0f, 1f)
            pool.play(sound, volume, volume, 1, 0, 1f)
        }
        if (settings.haptics) view?.performHapticFeedback(
            if (cue == Cue.ILLEGAL) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CLOCK_TICK,
        )
    }
    override fun close() = pool.release()
}
