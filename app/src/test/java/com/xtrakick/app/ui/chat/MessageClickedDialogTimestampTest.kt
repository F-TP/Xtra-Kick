package com.xtrakick.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MessageClickedDialogTimestampTest {

    @Test
    fun `formatHistoryTimestamp formats zero offset as mm ss`() {
        assertEquals("00:00", MessageClickedDialog.formatHistoryTimestamp(0L, Locale.US))
    }

    @Test
    fun `formatHistoryTimestamp formats relative vod offsets under one hour`() {
        assertEquals("01:05", MessageClickedDialog.formatHistoryTimestamp(65_000L, Locale.US))
        assertEquals("59:59", MessageClickedDialog.formatHistoryTimestamp(3_599_000L, Locale.US))
    }

    @Test
    fun `formatHistoryTimestamp formats relative vod offsets over one hour`() {
        assertEquals("1:02:05", MessageClickedDialog.formatHistoryTimestamp(3_725_000L, Locale.US))
        assertEquals("10:00:00", MessageClickedDialog.formatHistoryTimestamp(36_000_000L, Locale.US))
    }

    @Test
    fun `formatHistoryTimestamp formats epoch timestamps as calendar dates`() {
        val result = MessageClickedDialog.formatHistoryTimestamp(1761800000000L, Locale.US)
        assertTrue("Expected full date but got $result", result.contains("2025"))
    }
}
