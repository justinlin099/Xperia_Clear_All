package com.justinlin099.xperiarecentsrootless

import android.graphics.Color
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceProfileTest {
    @Test fun screenshotDefaultsAndProfilesKeepTheirOwnPositions() {
        val p=RuntimeEnvironment.getApplication().getSharedPreferences("profiles-test",0)
        p.edit().putString("buttonMode","TEXT_ICON").putString("iconArrangement","TOP").apply()
        val vertical=ButtonAppearance.read(p)
        assertEquals(800 to 790,ManualPlacement.fractions(vertical,false));assertEquals(13,vertical.verticalMargin)
        p.edit().putInt(ManualPlacement.key(vertical,false,true),650).putInt(ManualPlacement.key(vertical,false,false),720)
            .putInt(ManualPlacement.bottomKey(vertical),18).putInt(ManualPlacement.topKey(vertical,false),3).apply()
        p.edit().putString("iconArrangement","START").apply()
        val horizontal=ButtonAppearance.read(p)
        assertEquals(760 to 790,ManualPlacement.fractions(horizontal,false));assertEquals(0,horizontal.topMargin)
        p.edit().putInt(ManualPlacement.key(horizontal,false,true),700).putInt(ManualPlacement.bottomKey(horizontal),26).apply()
        p.edit().putString("iconArrangement","TOP").apply()
        val restored=ButtonAppearance.read(p)
        assertEquals(650 to 720,ManualPlacement.fractions(restored,false));assertEquals(18,restored.verticalMargin);assertEquals(3,restored.topMargin)
        val reset=p.edit();ManualPlacement.keysFor(restored).forEach { reset.remove(it) };reset.apply()
        assertEquals(800 to 790,ManualPlacement.fractions(ButtonAppearance.read(p),false))
        p.edit().putString("iconArrangement","START").apply()
        assertEquals(700,ButtonAppearance.read(p).manualX);assertEquals(26,ButtonAppearance.read(p).verticalMargin)
        p.edit().putString("buttonMode","ICON").apply()
        assertEquals(800 to 790,ManualPlacement.fractions(ButtonAppearance.read(p),false))
        p.edit().putString("buttonMode","TEXT").apply()
        assertEquals(760 to 790,ManualPlacement.fractions(ButtonAppearance.read(p),false))
    }
    @Test fun whiteAppliesToTextAndIconWhileFilledSurfaceKeepsItsNormalInk() {
        val c=RuntimeEnvironment.getApplication()
        fun children(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
        val prefs=c.getSharedPreferences("plain-color-test",0)
        prefs.edit().putString("plainTextColor","WHITE").putBoolean("textBackground",false).putString("buttonMode","TEXT_ICON").putString("iconArrangement","TOP").apply()
        val style=ButtonAppearance.read(prefs)
        val wrapper=ClearButtonView(c).apply { configure(style) }
        val label=children(wrapper).filterIsInstance<TextView>().first { it.text.isNotEmpty() }
        val icon=children(wrapper).filterIsInstance<ImageView>().single()
        assertEquals(Color.WHITE,label.currentTextColor);assertEquals(Color.WHITE,icon.imageTintList!!.defaultColor)
        wrapper.configure(style.copy(plainTextColor=PlainTextColor.THEME))
        val themed=children(wrapper).filterIsInstance<TextView>().first { it.text.isNotEmpty() }
        assertEquals(com.google.android.material.color.MaterialColors.getColor(themed,androidx.appcompat.R.attr.colorPrimary),themed.currentTextColor)
        wrapper.configure(style.copy(textBackground=true))
        assertNotEquals(Color.WHITE,children(wrapper).filterIsInstance<TextView>().first { it.text.isNotEmpty() }.currentTextColor)
    }
    @Test fun currentLayoutsKeepTheirOwnProfiles() {
        val p=RuntimeEnvironment.getApplication().getSharedPreferences("layout-profiles-test",0)
        p.edit().putString("buttonMode","TEXT_ICON").putString("iconArrangement","START").putInt("position.HORIZONTAL.portrait.x",735).apply()
        assertEquals(IconArrangement.START,ButtonAppearance.read(p).arrangement)
        assertEquals(735,ButtonAppearance.read(p).manualX)
        p.edit().putString("iconArrangement","TOP").putInt("position.VERTICAL.portrait.x",812).apply()
        assertEquals(IconArrangement.TOP,ButtonAppearance.read(p).arrangement)
        assertEquals(812,ButtonAppearance.read(p).manualX)
        assertEquals(listOf(IconArrangement.START,IconArrangement.TOP),IconArrangement.entries)
    }

}
