package com.qyvos.app

import android.app.Application
import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class QyvosApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        lateinit var instance: QyvosApplication
            private set
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContext = applicationContext

        // Initialize Chaquopy Python runtime
        initializePython()

        // Initialize workspace directories on Android storage
        applicationScope.launch {
            WorkspaceManager.initialize(applicationContext)
        }
    }

    private fun initializePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        // After Python is started, inject Android paths into Python environment
        try {
            val py = Python.getInstance()
            val sys = py.getModule("sys")
            val workspacePath = WorkspaceManager.getWorkspacePath(this)
            val logsPath = WorkspaceManager.getLogsPath(this)
            // Inject paths into Python builtins for OpenManus to pick up
            py.getModule("builtins").callAttr(
                "setattr",
                py.getBuiltins(),
                "__qyvos_workspace__",
                workspacePath
            )
            py.getModule("builtins").callAttr(
                "setattr",
                py.getBuiltins(),
                "__qyvos_logs__",
                logsPath
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
