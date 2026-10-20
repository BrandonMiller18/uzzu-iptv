package com.bmiller.uzzu.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/** Hands a downloaded APK to the system package installer. */
interface ApkInstaller {
    /** Whether this app may launch installs ("Install unknown apps" granted). */
    fun canInstall(): Boolean

    /** Opens the per-app "Install unknown apps" screen. False if the device has no such screen. */
    fun openInstallPermission(): Boolean

    /** Starts the installer for [apk]; throws if no installer activity exists. */
    fun install(apk: File)
}

class AndroidApkInstaller(context: Context) : ApkInstaller {
    private val app = context.applicationContext

    override fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    override fun openInstallPermission(): Boolean = try {
        app.startActivity(unknownSourcesIntent(app))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    override fun install(apk: File) = app.startActivity(installIntent(app, apk))

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"

        fun authority(context: Context) = "${context.packageName}.fileprovider"

        /**
         * ACTION_VIEW with a FileProvider content URI: handled by the stock installer on API 24+, and
         * by Fire OS 7's installer (ACTION_INSTALL_PACKAGE is deprecated since API 29).
         */
        fun installIntent(context: Context, apk: File): Intent =
            installIntent(FileProvider.getUriForFile(context, authority(context), apk))

        fun installIntent(apkUri: Uri): Intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        fun unknownSourcesIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
