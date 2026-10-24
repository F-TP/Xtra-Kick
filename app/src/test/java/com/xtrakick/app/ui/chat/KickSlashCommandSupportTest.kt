package com.xtrakick.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickSlashCommandSupportTest {

    @Test
    fun slowArguments_on() {
        assertEquals(true to 1, kickSlowModeArguments(listOf("on")))
        assertEquals(true to 1, kickSlowModeArguments(listOf("ON")))
    }

    @Test
    fun slowArguments_off() {
        assertEquals(false to 1, kickSlowModeArguments(listOf("off")))
        assertEquals(false to 1, kickSlowModeArguments(listOf("OFF")))
        assertEquals(false to 30, kickSlowModeArguments(listOf("off", "30")))
    }

    @Test
    fun slowArguments_onTakesExplicitSeconds() {
        assertEquals(true to 30, kickSlowModeArguments(listOf("on", "30")))
        assertEquals(true to 60, kickSlowModeArguments(listOf("on", "1m")))
    }

    @Test
    fun slowArguments_clampedToWebRange() {
        // kick.com web clamps slow mode to 1..120 seconds.
        assertEquals(true to 1, kickSlowModeArguments(listOf("on", "0")))
        assertEquals(true to 120, kickSlowModeArguments(listOf("on", "999")))
    }

    @Test
    fun followersArguments_on() {
        assertEquals(true to 0, kickFollowersModeArguments(listOf("on")))
        assertEquals(true to 10, kickFollowersModeArguments(listOf("on", "10")))
    }

    @Test
    fun followersArguments_off() {
        assertEquals(false to 0, kickFollowersModeArguments(listOf("off")))
    }

    @Test
    fun followersArguments_clampedToWebRange() {
        // kick.com web clamps followers mode to 0..10080 minutes.
        assertEquals(true to 0, kickFollowersModeArguments(listOf("on", "-5")))
        assertEquals(true to 10080, kickFollowersModeArguments(listOf("on", "99999")))
    }

    @Test
    fun timeoutMinutes_plainNumbersAreMinutes() {
        // kick.com web's Timeout listener posts {duration: minutes}; plain ints are minutes.
        assertEquals(30, kickTimeoutMinutes("30"))
        assertEquals(1, kickTimeoutMinutes("1"))
        assertEquals(600, kickTimeoutMinutes("600"))
    }

    @Test
    fun timeoutMinutes_suffixedTokensConvertToWholeMinutes() {
        // KickApiHelper returns seconds for suffixed durations; convert to whole minutes.
        assertEquals(1, kickTimeoutMinutes("1m"))
        assertEquals(60, kickTimeoutMinutes("1h"))
        assertEquals(2, kickTimeoutMinutes("90s"))
    }

    @Test
    fun timeoutMinutes_missingOrInvalidReturnsNull() {
        // kick.com web requires the minutes argument; never invent a default.
        assertNull(kickTimeoutMinutes(null))
        assertNull(kickTimeoutMinutes(""))
        assertNull(kickTimeoutMinutes("@"))
        assertNull(kickTimeoutMinutes("abc"))
        assertNull(kickTimeoutMinutes("0"))
        assertNull(kickTimeoutMinutes("-5"))
    }

    @Test
    fun slashCatalog_matchesVerifiedKickCommandSet() {
        val commands = slashCommands.map { it.command }
        // Verified Kick-native commands (kick.com web frontend).
        for (expected in listOf(
            "ban", "clear", "timeout", "unban", "title",
            "mod", "unmod", "raid", "vip", "unvip", "og", "unog",
            "slow", "slowoff", "emoteonly", "emoteonlyoff", "followers", "followersoff",
            "subscribers", "subscribersoff", "delete", "disconnect", "mods", "vips",
        )) {
            assertTrue("missing command: $expected", commands.contains(expected))
        }
        // Commands that do not exist on Kick must not be offered.
        for (removed in listOf(
            "announce", "color", "commercial", "marker", "w", "unraid", "uniquechat",
            "uniquechatoff", "category", "user",
        )) {
            assertFalse("unexpected command: $removed", commands.contains(removed))
        }
        assertEquals(commands.size, commands.toSet().size)
    }
}
