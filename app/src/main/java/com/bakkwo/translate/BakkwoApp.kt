package com.bakkwo.translate

import android.app.Application
import com.google.android.material.color.DynamicColors

class BakkwoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Follow the wallpaper palette (Material You) on Android 12+, like the rest of One UI.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
