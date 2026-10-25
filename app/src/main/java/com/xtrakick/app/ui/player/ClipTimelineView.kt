package com.xtrakick.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import com.xtrakick.app.util.KickClipApi
import kotlin.math.abs

/**
 * Unified timeline view for Kick clip creation:
 * Shows the full stream buffer with draggable Start and End handles, an active clip window,
 * and a Playhead scrubber that can be freely scrubbed without affecting the trim markers.
 */
class ClipTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private enum class DragTarget {
        NONE,
        START_HANDLE,
        END_HANDLE,
        PLAYHEAD
    }

    var maxDurationSeconds: Float = KickClipApi.CLIP_MAX_LENGTH_SECONDS.toFloat()
        set(value) {
            field = value.coerceAtLeast(1f)
            invalidate()
        }

    var minClipDurationSeconds: Float = KickClipApi.CLIP_MIN_LENGTH_SECONDS.toFloat()
    var maxClipDurationSeconds: Float = KickClipApi.CLIP_MAX_LENGTH_SECONDS.toFloat()

    var startSeconds: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, maxDurationSeconds)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var endSeconds: Float = KickClipApi.CLIP_DEFAULT_LENGTH_SECONDS.toFloat()
        set(value) {
            val clamped = value.coerceIn(startSeconds, maxDurationSeconds)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var playheadSeconds: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, maxDurationSeconds)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var onRangeChanged: ((start: Float, end: Float, fromUser: Boolean) -> Unit)? = null
    var onPlayheadScrubbing: ((position: Float) -> Unit)? = null
    var onPlayheadScrubFinished: ((position: Float) -> Unit)? = null

    private var dragTarget = DragTarget.NONE

    // Dimensions
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val handleWidthPx = dp(16f)
    private val cornerRadiusPx = dp(8f)
    private val handleRadiusPx = dp(6f)
    private val handleGripHalfHeightPx = dp(6f)
    private val playheadWidthPx = dp(3f)
    private val playheadCapRadiusPx = dp(5f)
    private val touchThresholdPx = dp(28f)

    // Colors
    @ColorInt private val trackBgColor = Color.parseColor("#222222")
    @ColorInt private val activeClipBgColor = Color.parseColor("#3353FC18") // 20% Kick green
    @ColorInt private val activeBorderColor = Color.parseColor("#53FC18")   // Kick green
    @ColorInt private val handleGripColor = Color.parseColor("#121212")
    @ColorInt private val playheadColor = Color.WHITE

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackBgColor
        style = Paint.Style.FILL
    }

    private val activeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeClipBgColor
        style = Paint.Style.FILL
    }

    private val activeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeBorderColor
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeBorderColor
        style = Paint.Style.FILL
    }

    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = handleGripColor
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }

    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = playheadColor
        style = Paint.Style.FILL
    }

    private val playheadShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.STROKE
        strokeWidth = dp(4.5f)
    }

    private val trackRect = RectF()
    private val activeRect = RectF()
    private val startHandleRect = RectF()
    private val endHandleRect = RectF()

    private val usableStart: Float get() = paddingLeft + handleWidthPx
    private val usableEnd: Float get() = width - paddingRight - handleWidthPx
    private val usableWidth: Float get() = (usableEnd - usableStart).coerceAtLeast(1f)

    private fun timeToX(time: Float): Float {
        if (maxDurationSeconds <= 0f) return usableStart
        val fraction = (time / maxDurationSeconds).coerceIn(0f, 1f)
        return usableStart + fraction * usableWidth
    }

    private fun xToTime(x: Float): Float {
        if (usableWidth <= 0f) return 0f
        val fraction = ((x - usableStart) / usableWidth).coerceIn(0f, 1f)
        return fraction * maxDurationSeconds
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val top = paddingTop.toFloat()
        val bottom = h - paddingBottom.toFloat()

        // 1. Full track background
        trackRect.set(usableStart, top, usableEnd, bottom)
        canvas.drawRoundRect(trackRect, cornerRadiusPx, cornerRadiusPx, trackPaint)

        val startX = timeToX(startSeconds)
        val endX = timeToX(endSeconds)

        // 2. Highlighted active clip window [startX, endX]
        activeRect.set(startX, top, endX, bottom)
        canvas.drawRect(activeRect, activeBgPaint)

        // Top & bottom green borders connecting handles
        canvas.drawLine(startX, top, endX, top, activeBorderPaint)
        canvas.drawLine(startX, bottom, endX, bottom, activeBorderPaint)

        // 3. Start Handle: pill on the left of active window
        startHandleRect.set(startX - handleWidthPx, top, startX, bottom)
        canvas.drawRoundRect(startHandleRect, handleRadiusPx, handleRadiusPx, handlePaint)
        // Grip lines in center of handle
        val startMidX = startHandleRect.centerX()
        val midY = (top + bottom) / 2f
        canvas.drawLine(startMidX, midY - handleGripHalfHeightPx, startMidX, midY + handleGripHalfHeightPx, gripPaint)

        // 4. End Handle: pill on the right of active window
        endHandleRect.set(endX, top, endX + handleWidthPx, bottom)
        canvas.drawRoundRect(endHandleRect, handleRadiusPx, handleRadiusPx, handlePaint)
        val endMidX = endHandleRect.centerX()
        canvas.drawLine(endMidX, midY - handleGripHalfHeightPx, endMidX, midY + handleGripHalfHeightPx, gripPaint)

        // 5. Playhead scrubber needle
        val playheadX = timeToX(playheadSeconds)
        // Shadow line
        canvas.drawLine(playheadX, top, playheadX, bottom, playheadShadowPaint)
        // Solid needle
        canvas.drawLine(playheadX, top, playheadX, bottom, playheadPaint)
        // Playhead top cap / pill
        canvas.drawCircle(playheadX, top + playheadCapRadiusPx, playheadCapRadiusPx, playheadPaint)
        canvas.drawCircle(playheadX, bottom - playheadCapRadiusPx, playheadCapRadiusPx, playheadPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val startHandleCenterX = startHandleRect.centerX()
                val endHandleCenterX = endHandleRect.centerX()
                val playheadX = timeToX(playheadSeconds)

                val distStart = abs(x - startHandleCenterX)
                val distEnd = abs(x - endHandleCenterX)
                val distPlayhead = abs(x - playheadX)

                dragTarget = when {
                    distStart <= touchThresholdPx && distStart <= distEnd -> DragTarget.START_HANDLE
                    distEnd <= touchThresholdPx -> DragTarget.END_HANDLE
                    distPlayhead <= touchThresholdPx / 1.5f -> DragTarget.PLAYHEAD
                    else -> {
                        // Tapping anywhere on the timeline scrubs the playhead immediately
                        playheadSeconds = xToTime(x)
                        onPlayheadScrubbing?.invoke(playheadSeconds)
                        invalidate()
                        DragTarget.PLAYHEAD
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                when (dragTarget) {
                    DragTarget.START_HANDLE -> {
                        var newStart = xToTime(x)
                        if (endSeconds - newStart < minClipDurationSeconds) {
                            newStart = endSeconds - minClipDurationSeconds
                        }
                        if (endSeconds - newStart > maxClipDurationSeconds) {
                            newStart = endSeconds - maxClipDurationSeconds
                        }
                        startSeconds = newStart.coerceIn(0f, maxDurationSeconds)
                        playheadSeconds = startSeconds
                        invalidate()
                        onRangeChanged?.invoke(startSeconds, endSeconds, true)
                        onPlayheadScrubbing?.invoke(playheadSeconds)
                    }

                    DragTarget.END_HANDLE -> {
                        var newEnd = xToTime(x)
                        if (newEnd - startSeconds < minClipDurationSeconds) {
                            newEnd = startSeconds + minClipDurationSeconds
                        }
                        if (newEnd - startSeconds > maxClipDurationSeconds) {
                            newEnd = startSeconds + maxClipDurationSeconds
                        }
                        endSeconds = newEnd.coerceIn(startSeconds + minClipDurationSeconds, maxDurationSeconds)
                        playheadSeconds = endSeconds
                        invalidate()
                        onRangeChanged?.invoke(startSeconds, endSeconds, true)
                        onPlayheadScrubbing?.invoke(playheadSeconds)
                    }

                    DragTarget.PLAYHEAD -> {
                        playheadSeconds = xToTime(x)
                        invalidate()
                        onPlayheadScrubbing?.invoke(playheadSeconds)
                    }

                    DragTarget.NONE -> {}
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragTarget != DragTarget.NONE) {
                    onPlayheadScrubFinished?.invoke(playheadSeconds)
                }
                dragTarget = DragTarget.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
