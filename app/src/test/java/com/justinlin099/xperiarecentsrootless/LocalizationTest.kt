package com.justinlin099.xperiarecentsrootless

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.LocaleList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalizationTest {
    @Test fun allDeclaredLocalesResolveTheirOwnTranslations() {
        val context = RuntimeEnvironment.getApplication()
        val parser = context.resources.getXml(R.xml.locales_config)
        val tags = mutableListOf<String>()
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale")
                tags.add(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name"))
            parser.next()
        }
        assertEquals(43, tags.size)
        for (tag in tags) {
            val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
            val localized = context.createConfigurationContext(config)
            assertEquals("Xperia Clear All", localized.getString(R.string.app_name))
            if (tag != "en") {
                assertNotEquals("Fallback language for $tag", "English", localized.getString(R.string.locale_name))
                assertNotEquals("Fallback button for $tag", "Clear all", localized.getString(R.string.clear_all))
            }
            assertFalse(localized.getString(R.string.offset, 32).contains("%1\$d"))
        }
    }
    @Test fun manifestContainsNoAccessibilityService() {
        val context = RuntimeEnvironment.getApplication()
        val services = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services.orEmpty()
        assertTrue(services.none { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" })
        assertTrue(services.none { it.name.contains("Accessibility") })
    }
}
