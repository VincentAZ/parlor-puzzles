package com.parlor.core.progress

import com.parlor.core.model.*

enum class Access { AVAILABLE, PREVIOUS_LEVEL, PREVIOUS_TIER, PRO }

object Progression {
    fun access(level: LevelDefinition, catalog: List<LevelDefinition>, bests: List<LevelBest>, pro: Boolean,
               temporaryExpiryMs: Long? = null, nowMs: Long = 0): Access {
        if (pro) return Access.AVAILABLE
        if (temporaryExpiryMs != null && temporaryExpiryMs > nowMs) return Access.AVAILABLE
        if (level.proVariant || level.tier >= Tier.HARD) return Access.PRO
        val completed = bests.filter { it.stars > 0 }.map { it.levelId }.toSet()
        val previousTier = Tier.entries.getOrNull(level.tier.ordinal - 1)
        if (previousTier != null) {
            val eligible = catalog.filter { it.puzzle == level.puzzle && it.tier == previousTier && !it.proVariant }
            val required = (eligible.size * 7 + 9) / 10
            if (eligible.count { it.id in completed } < required) return Access.PREVIOUS_TIER
        }
        val previous = catalog.filter {
            it.puzzle == level.puzzle && it.tier == level.tier && !it.proVariant && it.order < level.order
        }
        return if (previous.all { it.id in completed }) Access.AVAILABLE else Access.PREVIOUS_LEVEL
    }
}
