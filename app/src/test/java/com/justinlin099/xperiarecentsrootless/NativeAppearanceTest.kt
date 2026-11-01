package com.justinlin099.xperiarecentsrootless

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.google.android.material.button.MaterialButton
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
class NativeAppearanceTest {
    @Test fun allOfficialPresetsHaveDistinctDrawablePathsAndRender() {
        val c = RuntimeEnvironment.getApplication()
        val fingerprints = mutableSetOf<Int>()
        for (name in MaterialShapeCatalog.names) {
            val drawable = MaterialShapeCatalog.drawable(name, Color.MAGENTA)
            drawable.setBounds(0, 0, 160, 160)
            val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(160 * 160); bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 160)
            assertTrue("Empty preset $name", pixels.any { it != 0 })
            fingerprints += pixels.contentHashCode(); bitmap.recycle()
        }
        assertEquals(35, MaterialShapeCatalog.names.size)
        assertTrue(fingerprints.size >= 33)
    }
    @Test @Config(qualifiers="night") fun filledButtonUsesThemeRolesAndExplicitWhiteRemainsWhite() {
        val wrapper=ClearButtonView(RuntimeEnvironment.getApplication())
        wrapper.configure(ButtonAppearance(mode=ButtonMode.TEXT,textBackground=true))
        val button=wrapper.getChildAt(0) as MaterialButton
        fun theme(attr:Int)=com.google.android.material.color.MaterialColors.getColor(wrapper,attr)
        assertEquals(theme(com.google.android.material.R.attr.colorPrimaryContainer),button.backgroundTintList!!.defaultColor)
        assertEquals(theme(com.google.android.material.R.attr.colorOnPrimaryContainer),button.currentTextColor)
        wrapper.configure(ButtonAppearance(mode=ButtonMode.TEXT,textBackground=false,plainTextColor=PlainTextColor.WHITE))
        assertEquals(Color.WHITE,(wrapper.getChildAt(0) as MaterialButton).currentTextColor)
    }
    @Test fun textUsesMaterialButtonAndTransparentVariantIsPersisted() {
        val c = RuntimeEnvironment.getApplication()
        val p = c.getSharedPreferences("background-test", 0)
        p.edit().putString("buttonMode","TEXT").putBoolean("textBackground", false).putString("materialShape", "HEART").apply()
        val style = ButtonAppearance.read(p)
        assertFalse(style.textBackground); assertEquals("HEART", style.materialShape)
        val wrapper = ClearButtonView(c).apply { configure(style) }
        val text = wrapper.getChildAt(0) as MaterialButton
        assertEquals(Color.TRANSPARENT, text.backgroundTintList!!.defaultColor)
        assertEquals(c.getString(R.string.clear_all), text.text.toString())
        wrapper.configure(style.copy(textBackground = true))
        assertNotEquals(Color.TRANSPARENT, (wrapper.getChildAt(0) as MaterialButton).backgroundTintList!!.defaultColor)
    }
    @Test fun manualPreviewUsesSavedFractions() {
        val c = RuntimeEnvironment.getApplication()
        val preview = RecentsPreviewView(androidx.appcompat.view.ContextThemeWrapper(c, R.style.Theme_XperiaRecents)).apply { configure(ButtonAppearance(mode = ButtonMode.ICON, position = ButtonPosition.BOTTOM_RIGHT)) }
        preview.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        preview.layout(0, 0, 600, 400)
        val button = preview.getChildAt(0)
        val metrics = c.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
        val pad = (4*c.resources.displayMetrics.density).toInt()
        val area = android.graphics.Rect(insets.left+pad,insets.top+pad,bounds.width()-insets.right-pad,bounds.height()-insets.bottom-pad)
        val point = ManualPlacement.pixels(area, button.width, button.height, 800, 790)
        val scale = minOf(600f/bounds.width(),400f/bounds.height())
        assertEquals(scale, button.scaleX, .0001f); assertEquals(scale,button.scaleY,0f)
        assertEquals(kotlin.math.round((600-bounds.width()*scale)/2f+point.x*scale).toInt(),button.left)
        assertEquals(kotlin.math.round((400-bounds.height()*scale)/2f+point.y*scale).toInt(),button.top)
        val real = ClearButtonView(c).apply { configure(ButtonAppearance(mode=ButtonMode.ICON)) }.preferredSize()
        assertEquals(real.first,button.width); assertEquals(real.second,button.height)
    }
    @Test fun combinedLayoutsKeepNativeIconAndLabelAndFit() {
        val c = RuntimeEnvironment.getApplication()
        for (arrangement in IconArrangement.entries) {
            val wrapper = ClearButtonView(c).apply { configure(ButtonAppearance(mode = ButtonMode.TEXT_ICON, arrangement = arrangement)) }
            val size = wrapper.preferredSize()
            wrapper.measure(View.MeasureSpec.makeMeasureSpec(size.first, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size.second, View.MeasureSpec.EXACTLY))
            wrapper.layout(0, 0, size.first, size.second)
            fun descendants(v: View): List<View> = listOf(v) + if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
            val label = descendants(wrapper).filterIsInstance<android.widget.TextView>().first { it.text.toString() == c.getString(R.string.clear_all) }
            assertEquals(1, descendants(wrapper).filterIsInstance<android.widget.ImageView>().size)
            assertTrue("Label clipped: $arrangement", label.layout.height + label.compoundPaddingTop + label.compoundPaddingBottom <= label.height + 2)
        }
    }
}
