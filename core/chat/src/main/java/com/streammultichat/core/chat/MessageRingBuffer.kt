package com.streammultichat.core.chat

import java.util.ArrayDeque

/**
 * Thread-safe bounded ring buffer designed for streaming chat feeds.
 * Guarantees that memory remains strictly bounded to [capacity] elements.
 * Drops oldest elements in O(1) when capacity is exceeded.
 */
class MessageRingBuffer<T>(val capacity: Int = 200) {

    private val deque = ArrayDeque<T>(capacity)
    private val lock = Any()

    /**
     * Appends an item to the buffer. If the buffer is full,
     * the oldest item is discarded.
     * @return true if an old item was dropped, false otherwise.
     */
    fun push(item: T): Boolean {
        synchronized(lock) {
            val dropped = if (deque.size >= capacity) {
                deque.removeFirst()
                true
            } else {
                false
            }
            deque.addLast(item)
            return dropped
        }
    }

    /**
     * Returns an immutable snapshot list of all current items from oldest to newest.
     */
    fun toList(): List<T> {
        synchronized(lock) {
            return deque.toList()
        }
    }

    /**
     * Clears all items in the buffer.
     */
    fun clear() {
        synchronized(lock) {
            deque.clear()
        }
    }

    val size: Int
        get() = synchronized(lock) { deque.size }
}
