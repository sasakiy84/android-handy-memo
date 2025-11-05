package net.sasakiy85.handymemo

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class HandyMemoApplication : Application() {
    companion object {
        private const val TAG = "HandyMemoApplication"
        @Volatile
        var isAppInForeground = false
            private set
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            isAppInForeground = true
            Log.d(TAG, "App moved to foreground")
        }

        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            isAppInForeground = false
            Log.d(TAG, "App moved to background")
        }
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }
}

