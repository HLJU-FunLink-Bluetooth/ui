package com.hlju.funlinkbluetooth.ui

import androidx.navigation3.runtime.NavKey

sealed interface UiRoute : NavKey {
    data object Home : UiRoute

    data class AppDetail(val appId: String) : UiRoute

    data object SettingsTheme : UiRoute
}
