package com.vidya.ai.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vidya.ai.service.VidyaAccessibilityService

enum class AppPerm { Mic, Camera, Media, Location, Contacts, Calls, Notifications, Screen, Accessibility }

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
        AppPerm.Contacts -> arrayOf(Manifest.permission.READ_CONTACTS)
        AppPerm.Calls -> arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS)
        AppPerm.Notifications ->
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            else emptyArray()
        AppPerm.Screen, AppPerm.Accessibility -> emptyArray()
    }

    fun isGranted(ctx: Context, p: AppPerm): Boolean = when (p) {
        AppPerm.Notifications -> NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        AppPerm.Screen -> Settings.canDrawOverlays(ctx)
        AppPerm.Accessibility -> isAccessibilityOn(ctx)
        AppPerm.Calls -> runtimePerms(p).all { has(ctx, it) }
        else -> runtimePerms(p).any { has(ctx, it) }
    }

    fun isAccessibilityOn(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, VidyaAccessibilityService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true)
        }
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
        AppPerm.Accessibility -> ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        else -> openAppSettings(ctx)
    }
}
