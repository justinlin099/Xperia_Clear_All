package com.justinlin099.xperiarecentsrootless

import android.graphics.Point
import android.graphics.Rect
import kotlin.math.roundToInt

/** Fractions of the usable movement range, independent of pixel density and button dimensions. */
internal object ManualPlacement {
    private val profiles = listOf("TEXT", "ICON", "HORIZONTAL", "VERTICAL")
    private fun profile(mode: ButtonMode, arrangement: IconArrangement) = when {
        mode == ButtonMode.ICON -> "ICON"
        mode == ButtonMode.TEXT -> "TEXT"
        arrangement == IconArrangement.TOP -> "VERTICAL"
        else -> "HORIZONTAL"
    }
    fun profile(style: ButtonAppearance) = profile(style.mode, style.arrangement)
    fun defaultX(mode: ButtonMode, arrangement: IconArrangement) = if (profile(mode, arrangement) in setOf("ICON", "VERTICAL")) 800 else 760
    fun defaultBottom(mode: ButtonMode, arrangement: IconArrangement) = if (profile(mode, arrangement) == "VERTICAL") 13 else 32
    fun key(profile: String, landscape: Boolean, horizontal: Boolean) = "position.$profile.${if (landscape) "landscape" else "portrait"}.${if (horizontal) "x" else "y"}"
    fun key(style: ButtonAppearance, landscape: Boolean, horizontal: Boolean) = key(profile(style), landscape, horizontal)
    fun topKey(profile: String, horizontal: Boolean) = "position.$profile.top.${if (horizontal) "x" else "y"}"
    fun topKey(style: ButtonAppearance, horizontal: Boolean) = topKey(profile(style),horizontal)
    fun bottomKey(profile: String) = "position.$profile.bottom"
    fun bottomKey(style: ButtonAppearance) = bottomKey(profile(style))
    fun keysFor(style: ButtonAppearance) = listOf(false, true).flatMap { land -> listOf(true, false).map { key(style, land, it) } }.toSet()
    val keys = profiles.flatMap { profile ->
        listOf(false, true).flatMap { land -> listOf(true, false).map { key(profile, land, it) } } + listOf(bottomKey(profile),topKey(profile,true),topKey(profile,false))
    }.toSet()
    fun fractions(style: ButtonAppearance, landscape: Boolean) = if (landscape)
        style.manualLandscapeX to style.manualLandscapeY else style.manualX to style.manualY
    fun pixels(area: Rect, width: Int, height: Int, x: Int, y: Int): Point = Point(
        area.left + ((area.width() - width).coerceAtLeast(0) * x.coerceIn(0, 1000) / 1000f).roundToInt(),
        area.top + ((area.height() - height).coerceAtLeast(0) * y.coerceIn(0, 1000) / 1000f).roundToInt())
    fun normalized(area: Rect, width: Int, height: Int, x: Int, y: Int): Pair<Int, Int> {
        fun fraction(value: Int, start: Int, range: Int) = if (range <= 0) 0 else
            ((value - start) * 1000f / range).roundToInt().coerceIn(0, 1000)
        return fraction(x, area.left, area.width() - width) to fraction(y, area.top, area.height() - height)
    }
    fun clamp(area: Rect, width: Int, height: Int, x: Int, y: Int) = Point(
        x.coerceIn(area.left, maxOf(area.left, area.right - width)),
        y.coerceIn(area.top, maxOf(area.top, area.bottom - height)))
}
