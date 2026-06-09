package com.nekolaska.calabiyau.core.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.nekolaska.calabiyau.core.preferences.AppPrefs

object LauncherIconTheme {
    private const val BRAND_ALIAS = "LauncherIconBrand"
    private const val SYSTEM_ALIAS = "LauncherIconSystem"

    fun syncWithPreference(context: Context, theme: Int) {
        if (theme == AppPrefs.LAUNCHER_ICON_SYSTEM) {
            apply(context, theme)
        }
    }

    fun apply(context: Context, theme: Int) {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val brandAlias = ComponentName(appContext, "${appContext.packageName}.$BRAND_ALIAS")
        val systemAlias = ComponentName(appContext, "${appContext.packageName}.$SYSTEM_ALIAS")

        val enableAlias: ComponentName
        val disableAlias: ComponentName
        if (theme == AppPrefs.LAUNCHER_ICON_SYSTEM) {
            enableAlias = systemAlias
            disableAlias = brandAlias
        } else {
            enableAlias = brandAlias
            disableAlias = systemAlias
        }

        setEnabledIfNeeded(packageManager, enableAlias, true)
        setEnabledIfNeeded(packageManager, disableAlias, false)
    }

    private fun setEnabledIfNeeded(
        packageManager: PackageManager,
        componentName: ComponentName,
        enabled: Boolean
    ) {
        val state = packageManager.getComponentEnabledSetting(componentName)
        val targetState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (state == targetState) return

        packageManager.setComponentEnabledSetting(
            componentName,
            targetState,
            PackageManager.DONT_KILL_APP
        )
    }
}
