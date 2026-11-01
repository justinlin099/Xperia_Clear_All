package com.justinlin099.xperiarecentsrootless

import android.annotation.SuppressLint
import android.graphics.drawable.ShapeDrawable
import com.google.android.material.shape.MaterialShapes

/** Official Material Components 1.13 presets. Isolated because the Views catalog is library-restricted. */
@SuppressLint("RestrictedApi")
internal object MaterialShapeCatalog {
    val names = listOf("CIRCLE", "SQUARE", "SLANTED_SQUARE", "ARCH", "FAN", "ARROW", "SEMI_CIRCLE", "OVAL", "PILL", "TRIANGLE", "DIAMOND", "CLAM_SHELL", "PENTAGON", "GEM", "SUNNY", "VERY_SUNNY", "COOKIE_4", "COOKIE_6", "COOKIE_7", "COOKIE_9", "COOKIE_12", "GHOSTISH", "CLOVER_4", "CLOVER_8", "BURST", "SOFT_BURST", "BOOM", "SOFT_BOOM", "FLOWER", "PUFFY", "PUFFY_DIAMOND", "PIXEL_CIRCLE", "PIXEL_TRIANGLE", "BUN", "HEART")
    fun label(context: android.content.Context, name: String): String = when (name) {
        "CIRCLE" -> context.getString(R.string.shape_circle)
        "SQUARE" -> context.getString(R.string.shape_rounded)
        else -> label(name)
    }
    fun label(name: String) = name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    fun drawable(name: String, color: Int): ShapeDrawable {
        // Use direct library constants, never redraw approximations or reflect on app classes.
        val polygon = when (name) {
            "CIRCLE" -> MaterialShapes.CIRCLE
            "SQUARE" -> MaterialShapes.SQUARE
            "SLANTED_SQUARE" -> MaterialShapes.SLANTED_SQUARE
            "ARCH" -> MaterialShapes.ARCH
            "FAN" -> MaterialShapes.FAN
            "ARROW" -> MaterialShapes.ARROW
            "SEMI_CIRCLE" -> MaterialShapes.SEMI_CIRCLE
            "OVAL" -> MaterialShapes.OVAL
            "PILL" -> MaterialShapes.PILL
            "TRIANGLE" -> MaterialShapes.TRIANGLE
            "DIAMOND" -> MaterialShapes.DIAMOND
            "CLAM_SHELL" -> MaterialShapes.CLAM_SHELL
            "PENTAGON" -> MaterialShapes.PENTAGON
            "GEM" -> MaterialShapes.GEM
            "SUNNY" -> MaterialShapes.SUNNY
            "VERY_SUNNY" -> MaterialShapes.VERY_SUNNY
            "COOKIE_4" -> MaterialShapes.COOKIE_4
            "COOKIE_6" -> MaterialShapes.COOKIE_6
            "COOKIE_7" -> MaterialShapes.COOKIE_7
            "COOKIE_9" -> MaterialShapes.COOKIE_9
            "COOKIE_12" -> MaterialShapes.COOKIE_12
            "GHOSTISH" -> MaterialShapes.GHOSTISH
            "CLOVER_4" -> MaterialShapes.CLOVER_4
            "CLOVER_8" -> MaterialShapes.CLOVER_8
            "BURST" -> MaterialShapes.BURST
            "SOFT_BURST" -> MaterialShapes.SOFT_BURST
            "BOOM" -> MaterialShapes.BOOM
            "SOFT_BOOM" -> MaterialShapes.SOFT_BOOM
            "FLOWER" -> MaterialShapes.FLOWER
            "PUFFY" -> MaterialShapes.PUFFY
            "PUFFY_DIAMOND" -> MaterialShapes.PUFFY_DIAMOND
            "PIXEL_CIRCLE" -> MaterialShapes.PIXEL_CIRCLE
            "PIXEL_TRIANGLE" -> MaterialShapes.PIXEL_TRIANGLE
            "BUN" -> MaterialShapes.BUN
            "HEART" -> MaterialShapes.HEART
            else -> MaterialShapes.FLOWER
        }
        return MaterialShapes.createShapeDrawable(polygon).apply { paint.color = color }
    }
}
