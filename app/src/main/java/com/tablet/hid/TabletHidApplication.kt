package com.tablet.hid

import android.app.Application
import com.tablet.hid.util.AppearanceStore

class TabletHidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppearanceStore.apply(this)
    }
}
