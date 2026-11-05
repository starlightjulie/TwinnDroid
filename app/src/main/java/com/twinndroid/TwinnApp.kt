package com.twinndroid

import android.app.Application
import com.twinndroid.sandbox.Sandbox

class TwinnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The copy inside the sandbox only answers the bridge; updates are the main copy's job.
        if (Sandbox.isInside(this)) return
        UpdateWorker.createChannel(this)
        UpdateWorker.schedule(this)
    }
}
