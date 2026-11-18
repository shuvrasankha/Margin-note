package com.margin.app.di

import android.content.Context
import androidx.room.Room
import com.margin.app.data.MarginDatabase
import com.margin.app.data.MIGRATION_1_2
import com.margin.app.data.MIGRATION_2_3
import com.margin.app.data.MIGRATION_3_4
import com.margin.app.data.MIGRATION_4_5
import com.margin.app.data.MIGRATION_5_6
import com.margin.app.data.MIGRATION_6_7
import com.margin.app.data.MIGRATION_7_8
import com.margin.app.data.MIGRATION_8_9
import com.margin.app.data.MIGRATION_9_10
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
    fun database(@ApplicationContext context: Context): MarginDatabase =
        // Stored under the app's old name (Jotter); renaming it would lose what is saved.
        Room.databaseBuilder(context, MarginDatabase::class.java, "jotter.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
}
