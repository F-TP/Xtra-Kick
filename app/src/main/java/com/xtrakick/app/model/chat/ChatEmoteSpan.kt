package com.xtrakick.app.model.chat

/**
 * Metadata span attached to emote ranges in chat messages to support inspecting/copying
 * emote details via long-press in the chat view.
 */
class ChatEmoteSpan(
    val url: String?,
    val name: String?,
    val format: String? = null,
    val isAnimated: Boolean? = null,
    val source: Int? = null,
    val thirdParty: Boolean? = null,
    val emoteId: String? = null,
)
