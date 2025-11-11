package com.iie.st10320489.stylu.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.iie.st10320489.stylu.data.local.dao.CalendarDao
import com.iie.st10320489.stylu.data.local.dao.ItemDao
import com.iie.st10320489.stylu.data.local.dao.OutfitDao
import com.iie.st10320489.stylu.data.local.entities.CalendarEntity
import com.iie.st10320489.stylu.data.local.entities.ItemEntity
import com.iie.st10320489.stylu.data.local.entities.OutfitItemEntity
import com.iie.st10320489.stylu.data.local.entities.OutfitEntity


@Database(
    entities = [
        ItemEntity::class,
        OutfitEntity::class,
        OutfitItemEntity::class,
        CalendarEntity::class
    ],
    version = 3,  // ✅ Increment version
    exportSchema = false
)
abstract class StyluDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun outfitDao(): OutfitDao
    abstract fun calendarDao(): CalendarDao

    companion object {
        @Volatile
        private var INSTANCE: StyluDatabase? = null

        fun getDatabase(context: Context): StyluDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StyluDatabase::class.java,
                    "stylu_database"
                )
                    .fallbackToDestructiveMigration() // For development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}