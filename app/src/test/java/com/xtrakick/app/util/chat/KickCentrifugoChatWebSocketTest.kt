package com.xtrakick.app.util.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KickCentrifugoChatWebSocketTest {

    @Test
    fun buildsCanonicalCentrifugoChannels() {
        val channels = KickCentrifugoChatWebSocket.buildChannelNames(
            chatroomId = "123",
            channelId = "456",
            publicChannelNames = listOf("drops_category_7")
        )

        assertEquals(
            listOf(
                "chatrooms.123.v2",
                "chatroom_123",
                "channel.456",
                "channel_456",
                "drops_category_7"
            ),
            channels
        )
    }

    @Test
    fun omitsChannelScopedSubscriptionsWhenChannelIdIsMissing() {
        val channels = KickCentrifugoChatWebSocket.buildChannelNames(
            chatroomId = "123",
            channelId = null
        )

        assertEquals(
            listOf(
                "chatrooms.123.v2",
                "chatroom_123"
            ),
            channels
        )
        assertTrue(channels.none { it.startsWith("channel.") || it.startsWith("channel_") })
    }

    @Test
    fun buildsPrivateCentrifugoChannelsWhenProvided() {
        val channels = KickCentrifugoChatWebSocket.buildChannelNames(
            chatroomId = "123",
            channelId = "456",
            publicChannelNames = listOf("drops_category_7"),
            privateChannelNames = listOf("private-channelpoints-999", "private-999")
        )

        assertEquals(
            listOf(
                "chatrooms.123.v2",
                "chatroom_123",
                "channel.456",
                "channel_456",
                "drops_category_7",
                "private-channelpoints-999",
                "private-999"
            ),
            channels
        )
    }
}
