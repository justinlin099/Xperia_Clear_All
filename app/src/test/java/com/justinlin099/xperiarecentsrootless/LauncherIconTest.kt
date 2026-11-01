package com.justinlin099.xperiarecentsrootless

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    private fun pixels(drawable: Drawable): IntArray {
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 432, 432)
        drawable.draw(Canvas(bitmap))
        return IntArray(432 * 432).also {
            bitmap.getPixels(it, 0, 432, 0, 0, 432, 432)
            bitmap.recycle()
        }
    }

    @Test fun launcherHasIndependentMonochromeAndMatchingSafeSilhouette() {
        val app = RuntimeEnvironment.getApplication()
        val icon = app.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.monochrome)
        val color = pixels(icon.foreground)
        val mono = pixels(icon.monochrome!!)
        assertTrue(color.any { Color.alpha(it) > 0 })
        color.indices.forEach { i ->
            assertEquals(Color.alpha(color[i]), Color.alpha(mono[i]))
            if (Color.alpha(color[i]) > 0) {
                val x = (i % 432 + 0.5) / 4 - 54
                val y = (i / 432 + 0.5) / 4 - 54
                assertTrue("Mark outside adaptive safe circle", x*x + y*y < 33*33)
            }
        }
        assertEquals(Color.rgb(0,175,200), color[40*4*432 + 65*4])
        assertEquals(Color.rgb(55,103,232), color[53*4*432 + 60*4])
        assertEquals(Color.rgb(117,68,216), color[66*4*432 + 55*4])
    }

    @Test @Config(sdk = [32]) fun legacySupportedAndroidLoadsAdaptiveIcon() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(app.getDrawable(R.mipmap.ic_launcher) is AdaptiveIconDrawable)
    }

    @Test fun notificationGlyphKeepsTransparentBackground() {
        val app = RuntimeEnvironment.getApplication()
        val glyph = pixels(app.getDrawable(R.drawable.ic_rootless)!!)
        assertEquals(0, Color.alpha(glyph[0]))
        assertTrue(glyph.count { Color.alpha(it) > 0 } in 10000..100000)
        assertTrue(glyph.filter { Color.alpha(it) == 255 }.all { it == Color.WHITE })
    }
}
