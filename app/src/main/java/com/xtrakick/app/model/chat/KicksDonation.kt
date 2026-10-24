package com.xtrakick.app.model.chat

data class KicksDonation(
    val amount: Long,
    val message: String? = null,
    val giftName: String? = null,
    val giftTier: Int? = null,
    val recipientName: String? = null,
    val senderName: String? = null,
    val senderColor: String? = null,
)
