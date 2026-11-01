package com.justinlin099.xperiarecentsrootless

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsLayoutTest {
    private fun children(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()

    @Test
    @Config(qualifiers = "w393dp-h851dp-night-xxhdpi")
    fun rendersDarkSettings() { rendersMaterialSettingsAndExpandsDiagnostics() }

    @Test @Config(qualifiers = "b+zh+Hant-w393dp-h851dp-xxhdpi")
    fun rendersTraditionalChinese() { rendersMaterialSettingsAndExpandsDiagnostics() }
    @Test @Config(qualifiers = "de-rDE-w393dp-h851dp-xxhdpi")
    fun rendersGerman() { rendersMaterialSettingsAndExpandsDiagnostics() }
    @Test @Config(qualifiers = "ar-rEG-ldrtl-w393dp-h851dp-xxhdpi")
    fun rendersArabicRtl() { rendersMaterialSettingsAndExpandsDiagnostics() }
    @Test @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h851dp-xxhdpi")
    fun rendersHebrewRtl() { rendersMaterialSettingsAndExpandsDiagnostics() }
    @Test @Config(qualifiers = "th-rTH-w393dp-h851dp-xxhdpi")
    fun rendersThai() { rendersMaterialSettingsAndExpandsDiagnostics() }
    @Test @Config(qualifiers = "hi-rIN-w393dp-h851dp-xxhdpi")
    fun rendersHindi() { rendersMaterialSettingsAndExpandsDiagnostics() }

    @Test fun rendersMaterialSettingsAndExpandsDiagnostics() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        assertEquals("Status must match the current UI language", activity.getString(R.string.shizuku_not_running), RootlessConnection.status)
        val decor = activity.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2553, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1179, 2553)
        val scroll = children(decor).filterIsInstance<ScrollView>().first()
        assertTrue(children(scroll).filterIsInstance<TextView>().any { it.text == activity.getString(R.string.shizuku_not_installed) })
        for (button in children(scroll).filterIsInstance<com.google.android.material.button.MaterialButton>()) {
            if (button.visibility == View.VISIBLE && button.height > 0 && button.layout != null) {
                assertTrue("Button text clipped: ${button.text}",
                    button.layout.height + button.paddingTop + button.paddingBottom <= button.height + 2)
            }
        }
        val toggle = children(scroll).filterIsInstance<TextView>().first { it.text == activity.getString(R.string.expand) }
        val body = scroll.getChildAt(0)
        assertTrue(body.height > 0)
        children(body).forEach { it.setLayerType(View.LAYER_TYPE_SOFTWARE, null); it.invalidate() }
        val png = Bitmap.createBitmap(body.width, body.height, Bitmap.Config.ARGB_8888)
        body.draw(Canvas(png))
        val file = File("build/ui-preview/settings-${activity.resources.configuration.locales[0].toLanguageTag()}-${if (activity.resources.configuration.uiMode and 48 == 32) "dark" else "light"}.png").apply { parentFile?.mkdirs() }
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { assertTrue(png.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        png.recycle()
        java.nio.file.Files.move(temporary.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertTrue(file.isFile)
        toggle.performClick()
        assertEquals(View.VISIBLE, children(scroll).first { it is TextView && it.text == activity.getString(R.string.copy) }.parent.let { it as View }.visibility)
        controller.pause().stop().destroy()
    }
}
