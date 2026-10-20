package com.bmiller.uzzu.update

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApkInstallerTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun apk() = File(app.cacheDir, "updates/uzzu-iptv-1.0.0.apk").apply { parentFile!!.mkdirs(); writeText("apk") }

    @Test fun `install intent is ACTION_VIEW on the APK URI with read grant and a new task`() {
        val uri = Uri.parse("content://com.bmiller.uzzu.fileprovider/updates/uzzu-iptv-1.0.0.apk")
        val intent = AndroidApkInstaller.installIntent(uri)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals(uri, intent.data)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun `the authority is the manifest FileProvider and it shares cache updates`() {
        val authority = AndroidApkInstaller.authority(app)
        assertEquals("com.bmiller.uzzu.fileprovider", authority)
        val info = app.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)
        assertNotNull("provider $authority not in the manifest", info)
        assertEquals("androidx.core.content.FileProvider", info!!.name)
        assertTrue(info.grantUriPermissions)
        val xml = info.loadXmlMetaData(app.packageManager, "android.support.FILE_PROVIDER_PATHS")!!
        val roots = buildList {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "cache-path") add(xml.getAttributeValue(null, "path"))
            }
        }
        assertEquals(listOf("updates/"), roots)
    }

    /** FileProvider matches roots with '/', so the real URI round trip only works on a POSIX JVM (CI). */
    @Test fun `downloaded APK maps to a content URI under the provider`() {
        assumeFalse(File.separatorChar != '/')
        val intent = AndroidApkInstaller.installIntent(app, apk())
        assertEquals("content", intent.data!!.scheme)
        assertEquals("com.bmiller.uzzu.fileprovider", intent.data!!.authority)
        assertEquals("/updates/uzzu-iptv-1.0.0.apk", intent.data!!.path)
    }

    @Test fun `permission screen is this app's install-unknown-apps page`() {
        val intent = AndroidApkInstaller.unknownSourcesIntent(app)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:com.bmiller.uzzu", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
