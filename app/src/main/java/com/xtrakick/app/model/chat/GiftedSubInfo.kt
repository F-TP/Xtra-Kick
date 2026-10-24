package com.xtrakick.app.model.chat

data class GiftedSubInfo(
    val gifterName: String,
    val gifterColor: String? = null,
    val count: Int = 1,
    val gifteeName: String? = null,
    val gifteeNames: List<String> = emptyList(),
    val isCommunityGift: Boolean = false,
)
