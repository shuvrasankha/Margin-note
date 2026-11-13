package com.shuva.jotter.di

import android.content.Context
import androidx.room.Room
import com.shuva.jotter.data.JotterDatabase
import com.shuva.jotter.data.MIGRATION_1_2
import com.shuva.jotter.data.MIGRATION_2_3
import com.shuva.jotter.data.MIGRATION_3_4
import com.shuva.jotter.data.MIGRATION_4_5
import com.shuva.jotter.data.MIGRATION_5_6
import com.shuva.jotter.data.MIGRATION_6_7
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): JotterDatabase =
        Room.databaseBuilder(context, JotterDatabase::class.java, "jotter.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
}
