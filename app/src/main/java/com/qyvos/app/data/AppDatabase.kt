package com.qyvos.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.qyvos.app.data.models.Message
import com.qyvos.app.data.models.Session

@Database(
    entities = [Message::class, Session::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao
}
