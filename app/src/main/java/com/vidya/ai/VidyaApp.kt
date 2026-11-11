package com.vidya.ai

import android.app.Application
import com.vidya.ai.core.NetworkMonitor

/**
 * Application entry point. Owns the objects that should live as long as the process.
 */
class VidyaApp : Application() {

    /** Connectivity observer shared by the whole app. Started once, here. */
    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        networkMonitor = NetworkMonitor(this).apply { start() }
    }
}
