package com.justinlin099.xperiarecentsrootless

import android.content.res.Configuration
import android.graphics.Rect
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ButtonContentLayoutTest {
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun measure(v: ClearButtonView) {
        val size = v.preferredSize()
        v.measure(View.MeasureSpec.makeMeasureSpec(size.first, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size.second, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, size.first, size.second)
    }
    @Test fun verticalIconAndActualTextLinesShareButtonCenterAcrossLocalesPaddingAndFontSizes() {
        val app = RuntimeEnvironment.getApplication()
        for (locale in listOf("zh-Hant", "de", "ar")) for (fontScale in listOf(1f, 1.8f)) {
            val config = Configuration(app.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(locale)); this.fontScale = fontScale }
            val context = app.createConfigurationContext(config)
            for (arrangement in listOf(IconArrangement.TOP)) for (padding in listOf(0,16,48)) for (scale in listOf(1f,.65f)) {
                val v = ClearButtonView(context).apply { configure(ButtonAppearance(mode = ButtonMode.TEXT_ICON, arrangement = arrangement, textBackground=true, filledTextPadding = padding), scale) }
                measure(v)
                val text = descendants(v).filterIsInstance<TextView>().first { it.text.isNotEmpty() }
                val icon = descendants(v).filterIsInstance<ImageView>().single()
                fun rect(child: View) = Rect(0,0,child.width,child.height).also { v.offsetDescendantRectToMyCoords(child,it) }
                val tr = rect(text); val ir = rect(icon)
                val center = v.width / 2f
                val drawnCenter = tr.left + text.compoundPaddingLeft + (text.layout.getLineLeft(0) + text.layout.getLineRight(0))/2f
                val tag = "$locale/$fontScale/$arrangement/$padding/$scale"
                assertEquals("Icon center $tag", center, ir.exactCenterX(), 1.1f)
                assertEquals("Text center $tag", center, drawnCenter, 1.1f)
                val union = Rect(tr).apply { union(ir) }
                assertEquals("Content group center $tag", v.height/2f, union.exactCenterY(), 1.1f)
                val expectedGap = 8 * context.resources.displayMetrics.density * scale
                val gap = if (arrangement == IconArrangement.TOP) tr.top - ir.bottom else ir.top - tr.bottom
                assertEquals("Icon-text gap $tag", expectedGap, gap.toFloat(), 1.1f)
                assertTrue("Text clipped $tag", text.layout.height <= text.height + 1)
            }
        }
    }
    @Test fun noBackgroundTextHasNoFixed160dpBoxAndConfiguredPaddingMatchesBothSides() {
        val c = RuntimeEnvironment.getApplication()
        val view = ClearButtonView(c)
        for (padding in listOf(0, 8, 32, 48)) {
            view.configure(ButtonAppearance(mode=ButtonMode.TEXT,textBackground = false, plainTextPadding = padding)); measure(view)
            val text = view.getChildAt(0) as TextView
            val d = c.resources.displayMetrics.density
            val expected = maxOf(48*d, ceil(text.paint.measureText(c.getString(R.string.clear_all))) + 2*padding*d)
            assertEquals(expected+2*view.feedbackInset, view.width.toFloat(), 1.1f)
            assertEquals((padding*d).toInt(), text.paddingLeft)
            assertEquals(text.paddingLeft, text.paddingRight)
        }
    }
    @Test fun plainFeedbackHasIndependentRoomWithoutMovingVisibleContentAnchors() {
        val c=RuntimeEnvironment.getApplication();val d=c.resources.displayMetrics.density
        for(mode in listOf(ButtonMode.TEXT,ButtonMode.TEXT_ICON)) for(arrangement in IconArrangement.entries) {
            val v=ClearButtonView(c).apply { configure(ButtonAppearance(mode=mode,arrangement=arrangement,textBackground=false,plainTextPadding=0)) }
            measure(v)
            assertEquals((8*d).toInt(),v.feedbackInset)
            assertTrue(v.foreground is android.graphics.drawable.RippleDrawable)
            val content=v.getChildAt(0)
            assertEquals(v.feedbackInset,content.left)
            assertEquals(v.feedbackInset,v.width-content.right)
            val native=descendants(v).filterIsInstance<com.google.android.material.button.MaterialButton>().single()
            assertEquals(android.graphics.Color.TRANSPARENT,native.rippleColor!!.defaultColor)
            val area=Rect(0,0,1080,2200)
            val bodyWidth=v.width-2*v.feedbackInset
            val original=ManualPlacement.pixels(area,bodyWidth,v.height,800,790)
            val feedbackArea=Rect(area).apply { inset(-v.feedbackInset,0) }
            val adjusted=ManualPlacement.pixels(feedbackArea,v.width,v.height,800,790)
            assertEquals(original.x,adjusted.x+v.feedbackInset)
            assertEquals(original.y,adjusted.y)
        }
    }
    @Test fun filledAndPlainPaddingAndOfficialSquarePersistDirectly() {
        val c = RuntimeEnvironment.getApplication(); val p = c.getSharedPreferences("padding-shape-test",0)
        assertEquals(0,ButtonAppearance.read(p).copy(textBackground=false).effectiveTextPadding)
        assertEquals(16,ButtonAppearance.read(p).copy(textBackground=true).effectiveTextPadding)
        p.edit().putInt("filledTextPadding",24).putInt("plainTextPadding",3).putString("materialShape","SQUARE").apply()
        val style=ButtonAppearance.read(p)
        assertEquals(24,style.copy(textBackground=true).effectiveTextPadding);assertEquals(3,style.copy(textBackground=false).effectiveTextPadding)
        assertEquals("SQUARE",style.materialShape)
        val square=ClearButtonView(c).apply { configure(style.copy(mode=ButtonMode.ICON)) }.preferredSize()
        assertEquals(square.first,square.second)
        assertEquals(35,MaterialShapeCatalog.names.size)
        assertEquals(1,MaterialShapeCatalog.names.count { it=="CIRCLE" });assertEquals(1,MaterialShapeCatalog.names.count { it=="SQUARE" })
    }
}
