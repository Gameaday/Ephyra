package ephyra.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.data.notification.NotificationHandler
import ephyra.app.extension.util.ExtensionInstaller
import ephyra.data.notification.Notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppUpdateInstallerTest {

    @Test
    fun manifestDeclaresRequestInstallPackagesPermission() {
        var rootDir: File? = File(".").absoluteFile
        while (rootDir != null && !File(rootDir, "settings.gradle.kts").exists()) {
            rootDir = rootDir.parentFile
        }
        assertNotNull(rootDir)
        val manifestFile = File(rootDir, "app/src/main/AndroidManifest.xml")
        assertTrue("Manifest file does not exist", manifestFile.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val doc = dbFactory.newDocumentBuilder().parse(manifestFile)
        val permissionNodes = doc.getElementsByTagName("uses-permission")

        val permissions = (0 until permissionNodes.length).mapNotNull { i ->
            permissionNodes.item(i).attributes.getNamedItem("android:name")?.nodeValue
        }.toSet()

        assertTrue(
            "Manifest must declare android.permission.REQUEST_INSTALL_PACKAGES for in-app self updates",
            permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES"),
        )
        assertTrue(
            "Manifest must declare android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
            permissions.contains("android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION"),
        )
    }

    @Test
    fun installApkPendingActivityConstructsCorrectIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyUri = Uri.parse("content://app.ephyra.provider/ext_cache_files/update.apk")

        val pendingIntent = NotificationHandler.installApkPendingActivity(context, dummyUri)
        assertNotNull(pendingIntent)

        val shadowPendingIntent = shadowOf(pendingIntent)
        assertEquals(Notifications.ID_APP_UPDATE_PROMPT, shadowPendingIntent.requestCode)

        val intent = shadowPendingIntent.savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ExtensionInstaller.APK_MIME, intent.type)
        assertEquals(dummyUri, intent.data)
        assertTrue(
            "Must have FLAG_ACTIVITY_NEW_TASK",
            (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
        )
        assertTrue(
            "Must have FLAG_GRANT_READ_URI_PERMISSION",
            (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0,
        )
    }
}
