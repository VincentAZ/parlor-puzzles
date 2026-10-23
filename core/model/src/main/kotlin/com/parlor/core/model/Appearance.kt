package com.parlor.core.model

import kotlinx.serialization.Serializable

/** Stable settings IDs; presentation choices never change puzzle state or entitlement. */
enum class RoomTheme(val id: String) {
    LINEN("linen"), SAGE("sage"), ROSE("rose"), AMBER("amber"), SLATE("slate"), WALNUT("walnut");

    companion object {
        fun fromId(id: String): RoomTheme? = entries.firstOrNull { it.id == id }
        fun displayed(id: String, pro: Boolean): RoomTheme =
            if (pro) fromId(id) ?: LINEN else LINEN
    }
}

@Serializable
enum class Appearance {
    LIGHT, DARK, SYSTEM;
    fun isDark(systemDark: Boolean): Boolean = this == DARK || (this == SYSTEM && systemDark)
}
