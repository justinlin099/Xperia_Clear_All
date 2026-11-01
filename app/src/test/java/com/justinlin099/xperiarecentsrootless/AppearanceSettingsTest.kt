package com.justinlin099.xperiarecentsrootless

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "b+zh+Hant-w393dp-h851dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceSettingsTest {
    private fun children(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()

    @Test fun freshSettingsUseWhiteStackedPlainContentAtBottomCenter() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("fresh-style-test", 0)
        prefs.edit().clear().apply()
        val style = ButtonAppearance.read(prefs)
        assertEquals(ButtonMode.TEXT_ICON,style.mode)
        assertEquals(IconArrangement.TOP,style.arrangement)
        assertFalse(style.textBackground)
        assertEquals(PlainTextColor.WHITE,style.plainTextColor)
        assertEquals(ButtonPosition.BOTTOM_CENTER,style.position)
        assertEquals(13,style.verticalMargin)
        assertEquals(0,style.plainTextPadding)
        assertEquals(ButtonAppearance(),style)
    }

    @Test fun iconShapesUseCorrectDimensionsAndEveryPositionUsesPhysicalRight() {
        val c = RuntimeEnvironment.getApplication()
        val circle = ClearButtonView(c).apply { configure(ButtonAppearance(mode = ButtonMode.ICON)) }.preferredSize()
        assertEquals(circle.first, circle.second)
        val rounded = ClearButtonView(c).apply { configure(ButtonAppearance(mode = ButtonMode.ICON, materialShape = "SQUARE")) }.preferredSize()
        assertEquals(rounded.first, rounded.second)
        assertEquals(Gravity.TOP or Gravity.RIGHT, ButtonAppearance(position = ButtonPosition.TOP_RIGHT).gravity)
        assertEquals(Gravity.BOTTOM or Gravity.RIGHT, ButtonAppearance(position = ButtonPosition.BOTTOM_RIGHT).gravity)
    }

    @Test fun previewSelectionsPersistAndDoNotClearTasks() {
        val c = RuntimeEnvironment.getApplication()
        val prefs = c.getSharedPreferences("rootless", 0)
        prefs.edit().clear().apply()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        val decor = activity.window.decorView
        fun select(res: Int) { children(decor).first { it.contentDescription == activity.getString(res) }.performClick() }
        select(R.string.mode_icon)
        select(R.string.shape_material)
        val picker = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val pickerView = picker.window!!.decorView
        assertEquals(35, children(pickerView).count { it.contentDescription?.toString() in MaterialShapeCatalog.names.map { MaterialShapeCatalog.label(activity, it) } })
        pickerView.measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2100, View.MeasureSpec.EXACTLY))
        pickerView.layout(0, 0, 1179, 2100)
        val pickerBitmap = Bitmap.createBitmap(1179, 2100, Bitmap.Config.ARGB_8888)
        pickerView.draw(Canvas(pickerBitmap))
        File("build/ui-preview/material-shape-picker.png").apply { parentFile?.mkdirs() }.outputStream().use { pickerBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        pickerBitmap.recycle()
        val heart = children(pickerView).first { it.contentDescription == "Heart" }
        heart.performClick()
        assertEquals("HEART", ButtonAppearance.read(prefs).materialShape)
        select(R.string.position_top_right)
        val style = ButtonAppearance.read(prefs)
        assertEquals(ButtonMode.ICON, style.mode)
        assertEquals("HEART", style.materialShape)
        assertEquals(ButtonPosition.TOP_RIGHT, style.position)
        assertFalse(RootlessConnection.busy)
        decor.measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2553, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1179, 2553)
        val preview = children(decor).filterIsInstance<RecentsPreviewView>().first()
        children(preview).forEach { it.setLayerType(View.LAYER_TYPE_SOFTWARE, null); it.invalidate() }
        val bitmap = Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888)
        preview.draw(Canvas(bitmap))
        File("build/ui-preview/style-material-top-right.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(preview.getChildAt(0).scaleX, preview.getChildAt(0).scaleY, 0f)
        select(R.string.position_bottom_right)
        select(R.string.shape_material)
        val secondPicker = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        children(secondPicker.window!!.decorView).first { it.contentDescription == activity.getString(R.string.shape_rounded) }.performClick()
        assertEquals("SQUARE", ButtonAppearance.read(prefs).materialShape)
        select(R.string.mode_text)
        assertEquals(ButtonMode.TEXT, ButtonAppearance.read(prefs).mode)
        select(R.string.mode_text_icon)
        select(R.string.icon_above)
        assertEquals(ButtonMode.TEXT_ICON, ButtonAppearance.read(prefs).mode)
        assertEquals(IconArrangement.TOP, ButtonAppearance.read(prefs).arrangement)
        select(R.string.background_off)
        assertEquals(ButtonMode.TEXT_ICON, ButtonAppearance.read(prefs).mode)
        assertFalse(ButtonAppearance.read(prefs).textBackground)
        select(R.string.color_white)
        assertEquals(PlainTextColor.WHITE,ButtonAppearance.read(prefs).plainTextColor)
        select(R.string.position_bottom_right)
        prefs.edit().putInt(ManualPlacement.key(ButtonAppearance.read(prefs),false,true),537).putInt(ManualPlacement.key(ButtonAppearance.read(prefs),false,false),723).apply()
        children(decor).filterIsInstance<android.widget.TextView>().first { it.text == activity.getString(R.string.reset_position) }.performClick()
        assertEquals(800, ButtonAppearance.read(prefs).manualX)
        assertEquals(790, ButtonAppearance.read(prefs).manualY)
        decor.measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2553, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1179, 2553)
        val body = children(decor).filterIsInstance<android.widget.ScrollView>().first().getChildAt(0)
        val combined = Bitmap.createBitmap(body.width, body.height, Bitmap.Config.ARGB_8888)
        body.draw(Canvas(combined))
        val encoded = java.io.ByteArrayOutputStream()
        assertTrue(combined.compress(Bitmap.CompressFormat.PNG,100,encoded))
        val bytes=encoded.toByteArray()
        assertEquals("IEND",String(bytes,bytes.size-8,4,Charsets.US_ASCII))
        File("build/ui-preview/settings-combined-manual.png").writeBytes(bytes)
        combined.recycle()
        controller.pause().stop().destroy()
    }
    @Test fun allSlidersShowPresetBelowAndPresetsFollowLayoutInsteadOfCurrentValue() {
        val c=RuntimeEnvironment.getApplication()
        val p=c.getSharedPreferences("rootless",0);p.edit().clear().putString("buttonMode","TEXT").putString("iconArrangement","START").putBoolean("textBackground",true).apply()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val a=controller.get();val decor=a.window.decorView
        val settings=children(decor).filterIsInstance<AppearanceSettings>().first()
        fun select(res:Int) { children(decor).first { it.contentDescription==a.getString(res) }.performClick() }
        fun assertDefaults(vararg values:String) {
            val captions=children(settings).filterIsInstance<android.widget.TextView>().filter { it.tag=="slider-default" && it.isShown }
            assertEquals(values.map { a.getString(R.string.default_value,it) },captions.map { it.text.toString() })
            for(caption in captions) {
                val row=caption.parent as View
                val owner=row.parent as ViewGroup
                assertTrue(owner.getChildAt(owner.indexOfChild(row)-1) is com.google.android.material.slider.Slider)
            }
        }
        select(R.string.position_top_right)
        assertEquals(0,ButtonAppearance.read(p).topMargin);assertEquals(23,ButtonAppearance.read(p).topSideMargin)
        assertDefaults("16 dp","0 dp","23 dp")
        select(R.string.background_off);assertDefaults("0 dp","0 dp","23 dp")
        select(R.string.mode_text_icon);select(R.string.icon_above);select(R.string.position_bottom_center)
        assertDefaults("0 dp","13 dp","12 dp")
        select(R.string.position_bottom_right);assertDefaults("0 dp","80%","79%")
        select(R.string.icon_before);assertDefaults("0 dp","76%","79%")
        p.edit().putInt(ManualPlacement.key(ButtonAppearance.read(p),false,true),650).apply()
        assertDefaults("0 dp","76%","79%")
        controller.pause().stop().destroy()
    }

    @Test fun controlsFollowModeAndKeepSavedChoicesWhenHidden() {
        val c=RuntimeEnvironment.getApplication();val p=c.getSharedPreferences("rootless",0);p.edit().clear().apply()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get();val decor=a.window.decorView
        fun option(res:Int)=children(decor).first { it.contentDescription==a.getString(res) }
        fun select(res:Int) { val v=option(res);assertTrue(v.isShown);v.performClick() }
        select(R.string.mode_text)
        assertFalse(option(R.string.shape_material).isShown);assertFalse(option(R.string.icon_above).isShown)
        assertTrue(option(R.string.background_off).isShown);assertTrue(option(R.string.text_padding_label).isShown)
        select(R.string.background_off);select(R.string.color_white)
        select(R.string.mode_icon)
        assertTrue(option(R.string.shape_material).isShown);assertFalse(option(R.string.icon_above).isShown)
        assertFalse(option(R.string.background_off).isShown);assertFalse(option(R.string.color_white).isShown);assertFalse(option(R.string.text_padding_label).isShown)
        select(R.string.mode_text_icon)
        assertFalse(option(R.string.shape_material).isShown);assertTrue(option(R.string.icon_above).isShown)
        assertTrue(option(R.string.background_off).isShown);assertTrue(option(R.string.color_white).isShown)
        assertEquals(PlainTextColor.WHITE,ButtonAppearance.read(p).plainTextColor);assertFalse(ButtonAppearance.read(p).textBackground)
        controller.pause().stop().destroy()
    }

    @Test fun restoreButtonsResetOnlyTheirOwnSetting() {
        val c=RuntimeEnvironment.getApplication();val p=c.getSharedPreferences("rootless",0)
        p.edit().clear().putString("buttonMode","TEXT").putString("iconArrangement","START").putBoolean("textBackground",true).putInt(ManualPlacement.bottomKey("TEXT"),13).putInt("filledTextPadding",15).putInt("plainTextPadding",7).apply()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get();val decor=a.window.decorView
        fun select(res:Int) { children(decor).first { it.contentDescription==a.getString(res) }.performClick() }
        fun restore(id:String) {
            val button=children(decor).first { it.tag=="restore-$id" }
            assertTrue(button.isShown);assertTrue(button.isEnabled);button.performClick();assertFalse(button.isEnabled)
        }
        restore("padding")
        assertEquals(16,ButtonAppearance.read(p).filledTextPadding);assertEquals(7,ButtonAppearance.read(p).plainTextPadding)
        assertEquals(13,ButtonAppearance.read(p).verticalMargin)
        restore("margin-vertical")
        assertEquals(32,ButtonAppearance.read(p).verticalMargin);assertEquals(32,p.getInt(ManualPlacement.bottomKey("TEXT"),0))
        select(R.string.position_top_right)
        var style=ButtonAppearance.read(p)
        p.edit().putInt(ManualPlacement.topKey(style,false),12).putInt(ManualPlacement.topKey(style,true),40).apply()
        restore("margin-vertical")
        assertEquals(0,ButtonAppearance.read(p).topMargin);assertEquals(40,ButtonAppearance.read(p).topSideMargin)
        restore("margin-side")
        assertEquals(23,ButtonAppearance.read(p).topSideMargin)
        select(R.string.mode_text_icon);select(R.string.icon_above);select(R.string.position_bottom_right)
        style=ButtonAppearance.read(p)
        p.edit().putInt(ManualPlacement.key(style,false,true),600).putInt(ManualPlacement.key(style,false,false),500)
            .putInt("position.HORIZONTAL.portrait.x",700).apply()
        restore("manual-x")
        assertEquals(800,ButtonAppearance.read(p).manualX);assertEquals(500,ButtonAppearance.read(p).manualY)
        restore("manual-y")
        assertEquals(790,ButtonAppearance.read(p).manualY);assertEquals(700,p.getInt("position.HORIZONTAL.portrait.x",0))
        select(R.string.background_off);restore("padding")
        assertEquals(0,ButtonAppearance.read(p).plainTextPadding);assertEquals(16,ButtonAppearance.read(p).filledTextPadding)
        controller.pause().stop().destroy()
    }

}
