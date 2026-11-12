package com.vidya.ai.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class AppPerm { Mic, Camera, Media, Location, Notifications, Screen }

object Permissions {

    private fun has(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Runtime permissions to request (empty = special permission handled in Settings). */
    fun runtimePerms(p: AppPerm): Array<String> = when (p) {
        AppPerm.Mic -> arrayOf(Manifest.permission.RECORD_AUDIO)
        AppPerm.Camera -> arrayOf(Manifest.permission.CAMERA)
        AppPerm.Media -> when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        AppPerm.Location -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        AppPerm.Notifications ->
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            else emptyArray()
        AppPerm.Screen -> emptyArray()
    }

    fun isGranted(ctx: Context, p: AppPerm): Boolean = when (p) {
        AppPerm.Notifications -> NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        AppPerm.Screen -> Settings.canDrawOverlays(ctx)
        else -> runtimePerms(p).any { has(ctx, it) }
    }

    fun openAppSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        )
    }

    fun openNotificationSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        )
    }

    fun openOverlaySettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        )
    }

    fun openSettingsFor(ctx: Context, p: AppPerm) = when (p) {
        AppPerm.Notifications -> openNotificationSettings(ctx)
        AppPerm.Screen -> openOverlaySettings(ctx)
        else -> openAppSettings(ctx)
    }
}
