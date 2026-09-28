package com.mersadai.app.core

import android.content.Context
import com.mersadai.app.core.network.NetworkMonitor
import com.mersadai.app.data.local.MersadDatabase
import com.mersadai.app.data.local.SettingsRepository
import com.mersadai.app.data.repository.RoomContentRepository
import com.mersadai.app.worker.SyncScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database = MersadDatabase.create(appContext)
    val contentRepository = RoomContentRepository(database.contentDao())
    val settingsRepository = SettingsRepository(appContext)
    val networkMonitor = NetworkMonitor(appContext)
    val syncScheduler = SyncScheduler(appContext)
}
