package com.qyvos.app

import android.content.Context
import android.os.Environment
import java.io.File

object WorkspaceManager {

    private const val DIR_WORKSPACE = "workspace"
    private const val DIR_LOGS     = "logs"
    private const val DIR_OUTPUTS  = "outputs"
    private const val DIR_TEMP     = "temp"

    fun initialize(context: Context) {
        listOf(
            getWorkspacePath(context),
            getLogsPath(context),
            getOutputsPath(context),
            getTempPath(context)
        ).forEach { path ->
            File(path).apply { if (!exists()) mkdirs() }
        }
    }

    /** Returns the absolute path for the OpenManus workspace directory on Android. */
    fun getWorkspacePath(context: Context): String =
        File(context.getExternalFilesDir(null), DIR_WORKSPACE).absolutePath

    fun getLogsPath(context: Context): String =
        File(context.getExternalFilesDir(null), DIR_LOGS).absolutePath

    fun getOutputsPath(context: Context): String =
        File(context.getExternalFilesDir(null), DIR_OUTPUTS).absolutePath

    fun getTempPath(context: Context): String =
        File(context.getExternalFilesDir(null), DIR_TEMP).absolutePath

    fun listWorkspaceFiles(context: Context): List<File> =
        File(getWorkspacePath(context)).listFiles()?.toList() ?: emptyList()

    fun getLogFile(context: Context, sessionId: String): File =
        File(getLogsPath(context), "session_$sessionId.log")
}
