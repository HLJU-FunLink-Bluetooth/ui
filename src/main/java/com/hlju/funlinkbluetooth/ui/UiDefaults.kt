package com.hlju.funlinkbluetooth.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars

@Composable
fun defaultPageWindowInsets(): WindowInsets {
    return WindowInsets.systemBars
        .add(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
}
