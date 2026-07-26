package de.tobiga.yougattme

import android.app.Application
import de.tobiga.yougattme.diagnostics.BleCaptureManager

class YouGattMeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BleCaptureManager.initialize(this)
    }
}
