package com.example.mcoc.engine

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sqrt

enum class RecognizedGesture {
    NONE,
    TAP,
    SWIPE_RIGHT,
    SWIPE_LEFT,
    HOLD_START,
    HOLD_RELEASE
}

data class TouchSample(
    val position: Offset,
    val timestampMs: Long
)

/**
 * High-performance Swipe Velocity Analysis Engine.
 * Evaluates touch trajectory history over a sliding window,
 * calculating directional velocity (px/sec), instantaneous release acceleration,
 * and displacement to distinguish frame-accurate fighting game gestures:
 * - Tap (< 150ms, minimal drift)
 * - Velocity-driven Swipe Right / Swipe Left (dx/dt > threshold)
 * - Hold & Release (duration >= threshold with low drift)
 */
class SwipeVelocityEngine(
    private val swipeVelocityThreshold: Float = 420f, // px per second
    private val swipeMinDistance: Float = 30f,         // px
    private val tapMaxDurationMs: Long = 180L,
    private val tapMaxDistance: Float = 24f,
    private val holdThresholdMs: Long = 260L
) {
    private val samples = mutableListOf<TouchSample>()
    private var touchStartTimeMs: Long = 0L
    private var initialPosition: Offset = Offset.Zero
    private var isHolding: Boolean = false
    private var gestureDispatched: Boolean = false

    fun onTouchDown(position: Offset, currentTimeMs: Long) {
        samples.clear()
        touchStartTimeMs = currentTimeMs
        initialPosition = position
        isHolding = false
        gestureDispatched = false
        samples.add(TouchSample(position, currentTimeMs))
    }

    fun onTouchMove(position: Offset, currentTimeMs: Long): RecognizedGesture {
        samples.add(TouchSample(position, currentTimeMs))
        if (samples.size > 8) {
            samples.removeAt(0)
        }

        val elapsedMs = currentTimeMs - touchStartTimeMs
        val deltaX = position.x - initialPosition.x
        val deltaY = position.y - initialPosition.y
        val totalDistance = sqrt((deltaX * deltaX) + (deltaY * deltaY))

        // Check for immediate high-velocity swipe before hold trigger
        val velocity = computeInstantaneousVelocity()
        if (!gestureDispatched && abs(velocity.x) >= swipeVelocityThreshold && abs(deltaX) >= swipeMinDistance) {
            gestureDispatched = true
            isHolding = false
            return if (velocity.x > 0f) RecognizedGesture.SWIPE_RIGHT else RecognizedGesture.SWIPE_LEFT
        }

        // Check for Hold trigger
        if (!isHolding && !gestureDispatched && elapsedMs >= holdThresholdMs && totalDistance < tapMaxDistance * 2f) {
            isHolding = true
            gestureDispatched = true
            return RecognizedGesture.HOLD_START
        }

        return RecognizedGesture.NONE
    }

    fun onTouchUp(position: Offset, currentTimeMs: Long): RecognizedGesture {
        samples.add(TouchSample(position, currentTimeMs))
        val elapsedMs = currentTimeMs - touchStartTimeMs
        val deltaX = position.x - initialPosition.x
        val deltaY = position.y - initialPosition.y
        val totalDistance = sqrt((deltaX * deltaX) + (deltaY * deltaY))

        // If currently in a hold state, releasing triggers HOLD_RELEASE
        if (isHolding) {
            isHolding = false
            return RecognizedGesture.HOLD_RELEASE
        }

        // If swipe was already dispatched during move, ignore release
        if (gestureDispatched) {
            return RecognizedGesture.NONE
        }

        // Velocity analysis on release
        val velocity = computeInstantaneousVelocity()
        if (abs(velocity.x) >= swipeVelocityThreshold && abs(deltaX) >= swipeMinDistance) {
            return if (velocity.x > 0f) RecognizedGesture.SWIPE_RIGHT else RecognizedGesture.SWIPE_LEFT
        }

        // Fast tap check
        if (elapsedMs <= tapMaxDurationMs && totalDistance <= tapMaxDistance) {
            return RecognizedGesture.TAP
        }

        // Fallback swipe check based on net displacement
        if (elapsedMs <= 300L && abs(deltaX) >= swipeMinDistance) {
            return if (deltaX > 0f) RecognizedGesture.SWIPE_RIGHT else RecognizedGesture.SWIPE_LEFT
        }

        return RecognizedGesture.NONE
    }

    private fun computeInstantaneousVelocity(): Offset {
        if (samples.size < 2) return Offset.Zero

        val oldest = samples.first()
        val newest = samples.last()
        val dt = (newest.timestampMs - oldest.timestampMs) / 1000f // seconds
        if (dt <= 0.001f) return Offset.Zero

        val vx = (newest.position.x - oldest.position.x) / dt
        val vy = (newest.position.y - oldest.position.y) / dt
        return Offset(vx, vy)
    }

    fun isCurrentlyHolding(): Boolean = isHolding
}
