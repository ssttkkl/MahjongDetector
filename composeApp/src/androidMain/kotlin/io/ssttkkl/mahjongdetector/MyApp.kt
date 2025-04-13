package io.ssttkkl.mahjongdetector

import android.app.Application
import io.github.vinceglb.filekit.FileKit

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        _current = this
    }

    companion object {
        private var _current: MyApp? = null
        val current: MyApp
            get() = checkNotNull(_current)
    }
}