package com.maunkavach

import android.app.Application

class MaunKavachApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Master key + DB are lazily created on first access (CryptoManager / VaultKeyManager /
        // MaunKavachDbHelper), so nothing sensitive is touched merely by process start-up.
    }
}
