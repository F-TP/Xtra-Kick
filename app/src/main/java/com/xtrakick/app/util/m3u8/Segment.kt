package com.xtrakick.app.util.m3u8

data class Segment(
    val uri: String,
    val duration: Float,
    val title: String?,
    val programDateTime: String?,
    val byteRangeStart: Long? = null,
    val byteRangeLength: Long? = null
)