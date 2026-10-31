package com.xtrakick.app.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelsUndoTest {

    private class UndoManager(private val maxSteps: Int = NotificationChannelsViewModel.MAX_UNDO_STEPS) {
        val stack = ArrayDeque<Set<String>>()

        fun push(snapshot: Set<String>) {
            if (stack.lastOrNull() == snapshot) return
            if (stack.size >= maxSteps) {
                stack.removeFirst()
            }
            stack.addLast(snapshot)
        }

        fun undo(): Set<String>? = stack.removeLastOrNull()

        val canUndo: Boolean get() = stack.isNotEmpty()
    }

    @Test
    fun `push records snapshot and enables undo`() {
        val manager = UndoManager()
        assertFalse(manager.canUndo)
        manager.push(setOf("1", "2"))
        assertTrue(manager.canUndo)
        assertEquals(setOf("1", "2"), manager.undo())
        assertFalse(manager.canUndo)
    }

    @Test
    fun `multi-step undo preserves LIFO order for emergency recovery`() {
        val manager = UndoManager()
        // State 0: user has custom follows enabled
        manager.push(setOf("custom1", "custom2"))
        // State 1: user accidentally enabled all
        manager.push(setOf("custom1", "custom2", "channel3", "channel4"))

        // Emergency action: user panicked and disabled all
        // Undo 1 should revert to "all enabled"
        assertEquals(setOf("custom1", "custom2", "channel3", "channel4"), manager.undo())
        // Undo 2 must restore the original custom selection!
        assertEquals(setOf("custom1", "custom2"), manager.undo())
        assertFalse(manager.canUndo)
    }

    @Test
    fun `stack caps at max steps without memory leak`() {
        val manager = UndoManager(maxSteps = 3)
        manager.push(setOf("step1"))
        manager.push(setOf("step2"))
        manager.push(setOf("step3"))
        manager.push(setOf("step4"))

        assertEquals(3, manager.stack.size)
        assertEquals(setOf("step4"), manager.undo())
        assertEquals(setOf("step3"), manager.undo())
        assertEquals(setOf("step2"), manager.undo())
        assertFalse(manager.canUndo)
    }

    @Test
    fun `duplicate consecutive states are ignored`() {
        val manager = UndoManager()
        manager.push(setOf("1"))
        manager.push(setOf("1"))
        assertEquals(1, manager.stack.size)
    }
}
