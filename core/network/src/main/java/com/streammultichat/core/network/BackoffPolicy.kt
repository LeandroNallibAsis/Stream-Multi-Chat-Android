package com.streammultichat.core.network

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class BackoffPolicy(
    private val initialDelayMs: Long = 1000L,
    private val maxDelayMs: Long = 30000L,
    private val factor: Double = 1.6,
    private val jitterFactor: Double = 0.2
) {
    fun calculateDelay(attempt: Int): Long {
        if (attempt <= 0) return initialDelayMs
        val calculated = initialDelayMs * factor.pow(attempt.toDouble())
        val capped = min(calculated, maxDelayMs.toDouble())
        val jitter = capped * jitterFactor * (Random.nextDouble() * 2 - 1)
        return (capped + jitter).toLong().coerceIn(initialDelayMs, maxDelayMs)
    }
}
