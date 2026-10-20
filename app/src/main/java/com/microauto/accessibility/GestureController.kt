package com.microauto.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GestureController(private val service: AccessibilityService) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun tap(x: Int, y: Int, duration: Long = 50): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x + 1f, y.toFloat())
        }
        return dispatch(path, duration)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        if (duration <= 0) return false
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return dispatch(path, duration)
    }

    private fun dispatch(path: Path, duration: Long): Boolean {
        val latch = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(1))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                completed.set(true)
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                completed.set(false)
                latch.countDown()
            }
        }
        mainHandler.post {
            val accepted = runCatching {
                service.dispatchGesture(gesture, callback, null)
            }.getOrDefault(false)
            if (!accepted) latch.countDown()
        }
        return latch.await(duration + 1500, TimeUnit.MILLISECONDS) && completed.get()
    }
}
