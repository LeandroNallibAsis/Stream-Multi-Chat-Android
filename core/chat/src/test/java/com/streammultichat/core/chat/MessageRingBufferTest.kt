package com.streammultichat.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRingBufferTest {

    @Test
    fun `buffer preserves order and stays within capacity`() {
        val buffer = MessageRingBuffer<String>(capacity = 3)

        assertFalse(buffer.push("Msg 1"))
        assertFalse(buffer.push("Msg 2"))
        assertFalse(buffer.push("Msg 3"))
        assertEquals(3, buffer.size)
        assertEquals(listOf("Msg 1", "Msg 2", "Msg 3"), buffer.toList())

        // Pushing a 4th item should drop "Msg 1"
        val dropped = buffer.push("Msg 4")
        assertTrue(dropped)
        assertEquals(3, buffer.size)
        assertEquals(listOf("Msg 2", "Msg 3", "Msg 4"), buffer.toList())
    }

    @Test
    fun `buffer clear empties all elements`() {
        val buffer = MessageRingBuffer<Int>(capacity = 10)
        repeat(5) { buffer.push(it) }
        assertEquals(5, buffer.size)

        buffer.clear()
        assertEquals(0, buffer.size)
        assertTrue(buffer.toList().isEmpty())
    }
}
