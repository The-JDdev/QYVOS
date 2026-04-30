package com.qyvos.app.di

import android.content.Context
import androidx.room.Room
import com.qyvos.app.data.AppConfig
import com.qyvos.app.data.AppDatabase
import com.qyvos.app.data.MessageDao
import com.qyvos.app.data.SessionDao
import com.qyvos.app.engine.OpenManusEngine
import com.qyvos.app.network.ChatRepository
import com.qyvos.app.security.TokenVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "qyvos_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides @Singleton
    fun provideChatRepository(
        httpClient: OkHttpClient,
        appConfig: AppConfig
    ): ChatRepository = ChatRepository(httpClient, appConfig)

    /**
     * On-device Python agent (Chaquopy). Kept registered so existing
     * developer-mode tooling continues to compile, but no longer used by
     * ChatViewModel — chat now flows through ChatRepository over HTTP.
     */
    @Provides @Singleton
    fun provideOpenManusEngine(
        @ApplicationContext context: Context,
        appConfig: AppConfig,
        tokenVault: TokenVault
    ): OpenManusEngine = OpenManusEngine(context, appConfig, tokenVault)

    @Provides @Singleton
    fun provideTokenVault(@ApplicationContext context: Context): TokenVault = TokenVault(context)
}
