package com.kansen.fanfan.di

import android.content.Context
import androidx.room.Room
import com.kansen.fanfan.core.database.FanfanDatabase
import com.kansen.fanfan.core.database.MIGRATION_1_2
import com.kansen.fanfan.core.database.MIGRATION_2_3
import com.kansen.fanfan.core.database.dao.DraftDao
import com.kansen.fanfan.core.database.dao.StatusDao
import com.kansen.fanfan.core.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FanfanDatabase =
        Room.databaseBuilder(context, FanfanDatabase::class.java, "fanfan.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideStatusDao(db: FanfanDatabase): StatusDao = db.statusDao()

    @Provides
    fun provideUserDao(db: FanfanDatabase): UserDao = db.userDao()

    @Provides
    fun provideDraftDao(db: FanfanDatabase): DraftDao = db.draftDao()
}
