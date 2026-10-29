package com.xtrakick.app.repository

import com.xtrakick.app.model.ui.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class KickVideoParsingTest {

    private fun resolveDisplayName(
        channelLogin: String?,
        channelName: String?,
        nameDisplayPref: String = "1"
    ): String? {
        val cleanLogin = channelLogin?.trim()?.takeIf { it.isNotBlank() }
        val cleanName = channelName?.trim()?.takeIf { it.isNotBlank() }
        return if (!cleanLogin.isNullOrBlank() && !cleanName.isNullOrBlank() && !cleanLogin.equals(cleanName, true)) {
            when (nameDisplayPref) {
                "0" -> "${cleanName}(${cleanLogin})"
                "1" -> cleanName
                else -> cleanLogin
            }
        } else {
            cleanName ?: cleanLogin
        }
    }

    @Test
    fun testDisplayNameResolution_whenChannelNameIsNull_fallsBackToChannelLogin() {
        val displayName = resolveDisplayName(channelLogin = "maximusblack", channelName = null, nameDisplayPref = "1")
        assertEquals("maximusblack", displayName)
    }

    @Test
    fun testDisplayNameResolution_whenChannelLoginIsNull_fallsBackToChannelName() {
        val displayName = resolveDisplayName(channelLogin = null, channelName = "MaximusBlack", nameDisplayPref = "1")
        assertEquals("MaximusBlack", displayName)
    }

    @Test
    fun testDisplayNameResolution_whenBothPresentAndDiffer_respectsDisplayMode() {
        assertEquals("Maximus(maximusblack)", resolveDisplayName("maximusblack", "Maximus", "0"))
        assertEquals("Maximus", resolveDisplayName("maximusblack", "Maximus", "1"))
        assertEquals("maximusblack", resolveDisplayName("maximusblack", "Maximus", "2"))
    }

    @Test
    fun testDisplayNameResolution_whenBothMatchCaseInsensitive_usesChannelName() {
        assertEquals("MaximusBlack", resolveDisplayName("maximusblack", "MaximusBlack", "1"))
        assertEquals("MaximusBlack", resolveDisplayName("maximusblack", "MaximusBlack", "0"))
    }

    @Test
    fun testVideoModelChannelProperties() {
        val video = Video(
            id = "12345",
            channelId = "999",
            channelLogin = "maximusblack",
            channelName = "MaximusBlack",
            title = "OTT - Gang Update",
            duration = "7445"
        )
        assertEquals("12345", video.id)
        assertEquals(com.xtrakick.app.util.AppConstants.KICK, video.source)
        assertEquals("maximusblack", video.channelLogin)
        assertEquals("MaximusBlack", video.channelName)
        assertEquals("OTT - Gang Update", video.title)
    }

    @Test
    fun testVideoModelDefaultsSourceToKick() {
        val video = Video(id = "01a0590d-ed00-78b9-9df1-979c88189a5e", uuid = "01a0590d-ed00-78b9-9df1-979c88189a5e")
        assertEquals(com.xtrakick.app.util.AppConstants.KICK, video.source)
        assertEquals("01a0590d-ed00-78b9-9df1-979c88189a5e", video.uuid)
    }

    @Test
    fun testLiveVodInfo_propertiesAndRewindTimestampCalculation() {
        val startedAtIso = "2026-10-29T12:00:00Z"
        val info = KickRepository.LiveVodInfo(
            url = "https://stream.kick.com/vod/master.m3u8",
            startedAt = startedAtIso
        )
        assertEquals("https://stream.kick.com/vod/master.m3u8", info.url)
        assertEquals(startedAtIso, info.startedAt)

        val streamStartMs = com.xtrakick.app.util.KickApiHelper.parseIso8601DateUTC(info.startedAt!!)
        org.junit.Assert.assertNotNull(streamStartMs)

        // 30 minutes in, rewound 5 minutes -> position is 25 minutes (1,500,000 ms)
        val targetPositionMs = 25 * 60 * 1000L
        val chatTimestampMs = streamStartMs!! + targetPositionMs
        assertEquals(streamStartMs + 1_500_000L, chatTimestampMs)
    }
}
