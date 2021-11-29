/*
 * Copyright (C) 2021 Chaldeaprjkt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.gamebar

import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.utils.ScreenUtils


class TaskListenerService : Service(), GameStackAction {
    private val taskManager by lazy { ActivityTaskManager.getService() }
    private val settings by lazy { SystemSettings(applicationContext) }
    private val listener by lazy {
        object : TaskStackListener() {
            override fun onTaskStackChanged() {
                checkTaskStack(taskManager?.focusedRootTaskInfo)
            }
        }
    }
    private val screenReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                previousApp = UNKNOWN_APP
            }
        }
    }

    private var previousApp = UNKNOWN_APP
    private var sessionKey = UNKNOWN_APP

    override fun onCreate() {
        try {
            taskManager.registerTaskStackListener(listener)
            ScreenUtils.init(this)
        } catch (e: RemoteException) {
            Log.d(TAG, e.toString())
        }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    private fun isGame(packageName: String) = settings.userGames.contains(packageName)

    private fun checkTaskStack(info: ActivityTaskManager.RootTaskInfo?) {
        try {
            val currentApp = info?.topActivity?.packageName ?: return
            if (currentApp == previousApp) return
            if (isGame(currentApp)) {
                sessionKey = currentApp
                onGameEnter(this, currentApp)
            } else if (sessionKey != UNKNOWN_APP) {
                onGameLeave(this, previousApp)
                sessionKey = UNKNOWN_APP
            }

            previousApp = currentApp
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
        }
    }

    companion object {
        const val TAG = "TaskListener"
        const val UNKNOWN_APP = "unknown"
    }
}
