package com.xtrakick.app.model.kick

import com.xtrakick.app.model.chat.Raid

data class KickRealtimeChannelPointsUpdate(
    val reason: String? = null,
    val points: Int? = null,
    val balance: Int? = null,
    val userId: String? = null,
    val channelId: String? = null,
)

data class KickChannelMoveEvent(
    val raid: Raid,
    val message: String,
    val rawEventName: String? = null,
    val rawPayload: String? = null,
)
