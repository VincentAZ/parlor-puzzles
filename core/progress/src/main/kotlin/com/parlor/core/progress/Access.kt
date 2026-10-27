package com.parlor.core.progress

import com.parlor.core.model.*

enum class Access { AVAILABLE, PREVIOUS_LEVEL, PREVIOUS_TIER, PRO }

object Progression {
    fun access(level: LevelDefinition, catalog: List<LevelDefinition>, bests: List<LevelBest>, pro: Boolean,
               temporaryExpiryMs: Long? = null, nowMs: Long = 0): Access {
        // Legacy flags/temporary unlocks remain in old saves; all bundled levels are now free.
        return Access.AVAILABLE
    }
}
