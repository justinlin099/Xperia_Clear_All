package com.justinlin099.xperiarecentsrootless

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reference: supplied Sony Launcher17 values/styles.xml, OverviewActionButton. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], qualifiers="b+zh+Hant-w393dp-h851dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverviewActionTypographyTest {
    private fun children(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
    @Test fun verticalLabelsMatchSonyActionFontSizeWeightAndDoNotAutoShrinkWhenTheyFit() {
        val c=RuntimeEnvironment.getApplication()
        for(arrangement in listOf(IconArrangement.TOP)) for(background in listOf(true,false)) {
            val wrapper=ClearButtonView(c).apply { configure(ButtonAppearance(mode=ButtonMode.TEXT_ICON,arrangement=arrangement,textBackground=background)) }
            val size=wrapper.preferredSize()
            wrapper.measure(View.MeasureSpec.makeMeasureSpec(size.first,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size.second,View.MeasureSpec.EXACTLY))
            wrapper.layout(0,0,size.first,size.second)
            val label=children(wrapper).filterIsInstance<TextView>().single { it.text.isNotEmpty() }
            val expected=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,14f,c.resources.displayMetrics)
            assertEquals("Must retain Sony14sp after layout",expected,label.textSize,.1f)
            assertEquals("Sony OverviewActionButton font weight",600,label.typeface.weight)
            assertFalse(label.typeface.isItalic)
            assertFalse(label.includeFontPadding)
            assertEquals(1,label.lineCount)
            assertEquals(13,ButtonAppearance(mode=ButtonMode.TEXT_ICON,arrangement=arrangement).verticalMargin)
            assertEquals(800 to 790,ManualPlacement.fractions(ButtonAppearance(mode=ButtonMode.TEXT_ICON,arrangement=arrangement),false))
        }
    }
}
