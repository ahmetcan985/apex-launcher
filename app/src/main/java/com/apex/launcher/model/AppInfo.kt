package com.apex.launcher.model

import android.content.Intent
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val launchIntent: Intent
)
