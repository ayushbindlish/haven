package org.havenapp.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

/**
 * Aggressive OEM battery management (Samsung One UI, Xiaomi MIUI, OnePlus, Huawei, ...)
 * will put a background monitoring app to sleep no matter how correctly the foreground
 * service is implemented. This helper surfaces a short checklist and deep-links the user
 * into the relevant system screens; the intents it fires are all standard AOSP actions.
 *
 * See https://dontkillmyapp.com for the per-vendor behaviour.
 */
object DeviceSetupHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        val intent = if (isIgnoringBatteryOptimizations(activity)) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${activity.packageName}"))
        }
        activity.tryStart(intent)
    }

    /**
     * App-info screen. On Samsung this is where "Pause app activity if unused",
     * "Remove permissions if unused" and the Battery -> Unrestricted toggle live.
     */
    fun openAppInfo(activity: Activity) {
        activity.tryStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${activity.packageName}"))
        )
    }

    fun showChecklist(activity: Activity) {
        val battState = if (isIgnoringBatteryOptimizations(activity)) "✓ done" else "✗ not yet"
        val message = activity.getString(R.string.device_setup_body, battState)

        AlertDialog.Builder(activity)
            .setTitle(R.string.device_setup_title)
            .setMessage(message)
            .setPositiveButton(R.string.device_setup_battery) { _, _ ->
                requestIgnoreBatteryOptimizations(activity)
            }
            .setNeutralButton(R.string.device_setup_appinfo) { _, _ ->
                openAppInfo(activity)
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun Context.tryStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Some OEM builds omit these settings actions; fall back to app info.
            if (intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        }
    }
}
