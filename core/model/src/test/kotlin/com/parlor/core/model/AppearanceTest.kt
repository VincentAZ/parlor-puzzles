package com.parlor.core.model

import kotlin.test.*

class AppearanceTest {
    @Test fun legacySettingsStayLightAndEveryThemeUsesStableAccessRules() {
        val legacy = ParlorJson.decodeFromString<PlayerSettings>("""{"theme":"linen"}""")
        assertEquals(Appearance.LIGHT, legacy.appearance)
        assertEquals(6, RoomTheme.entries.size)
        assertEquals(6, RoomTheme.entries.map { it.id }.distinct().size)
        RoomTheme.entries.forEach { theme ->
            val settings = PlayerSettings(theme = theme.id, appearance = Appearance.DARK)
            assertEquals(settings, ParlorJson.decodeFromString<PlayerSettings>(ParlorJson.encodeToString(settings)))
            assertEquals(theme, RoomTheme.displayed(settings.theme, true))
            assertEquals(theme, RoomTheme.displayed(settings.theme, false))
            assertEquals(theme.id, settings.theme)
        }
        assertEquals(RoomTheme.LINEN, RoomTheme.displayed("unknown", true))
        assertNull(RoomTheme.fromId("unknown"))
    }

    @Test fun appearanceHonorsExplicitChoiceBeforeDeviceMode() {
        for (systemDark in listOf(false, true)) {
            assertFalse(Appearance.LIGHT.isDark(systemDark))
            assertTrue(Appearance.DARK.isDark(systemDark))
            assertEquals(systemDark, Appearance.SYSTEM.isDark(systemDark))
        }
    }
}
