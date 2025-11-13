package com.iie.st10320489.stylu.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.data.local.entities.CalendarEntity
import com.iie.st10320489.stylu.data.local.entities.ItemEntity
import com.iie.st10320489.stylu.data.local.entities.OutfitEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StyluDatabaseTest {

    private lateinit var database: StyluDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            StyluDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun database_canBeCreated() {
        // Force the database to initialize and open
        database.openHelper.writableDatabase

        // Then
        assertThat(database).isNotNull()
        assertThat(database.isOpen).isTrue()
    }

    @Test
    fun database_hasAllDaos() {
        // Then
        assertThat(database.itemDao()).isNotNull()
        assertThat(database.outfitDao()).isNotNull()
        assertThat(database.calendarDao()).isNotNull()
    }

    @Test
    fun database_supportsTransactions() = runTest {
        // Given
        val item = ItemEntity(
            1, "Item", "Sub", "Cat", "Red", "M", "url", "Summer", 0
        )
        val outfit = OutfitEntity(
            1, "user", "Outfit", "Cat", null, "2025-11-01", "2025-11-01"
        )
        val calendar = CalendarEntity(
            1, "user", 1, System.currentTimeMillis(),
            "Event", null, null, null, null, null
        )

        // When - Insert all in sequence
        database.itemDao().insertItem(item)
        database.outfitDao().insertOutfit(outfit)
        database.calendarDao().insertScheduledOutfit(calendar)

        // Then - All should be retrievable
        assertThat(database.itemDao().getItemById(1)).isNotNull()
        assertThat(database.outfitDao().getOutfitById(1)).isNotNull()
        assertThat(database.calendarDao().getScheduledOutfitById(1)).isNotNull()
    }

    @Test
    fun database_handlesForeignKeyConstraints() = runTest {
        // Given
        val item = ItemEntity(
            101, "Item", "Sub", "Cat", "Red", "M", "url", "Summer", 0
        )
        val outfit = OutfitEntity(
            1, "user", "Outfit", "Cat", null, "2025-11-01", "2025-11-01"
        )

        // When
        database.itemDao().insertItem(item)
        database.outfitDao().insertOutfit(outfit)

        // Delete outfit (should cascade to outfit_items)
        database.outfitDao().deleteOutfit(1)

        // Then
        assertThat(database.outfitDao().getOutfitById(1)).isNull()
    }

    @Test
    fun database_itemDao_basicOperations() = runTest {
        // Given
        val item = ItemEntity(
            1, "Test", "Sub", "Cat", "Blue", "M", "url", "All Weather", 0
        )

        // When
        database.itemDao().insertItem(item)
        val retrieved = database.itemDao().getItemById(1)

        // Then
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("Test")
    }

    @Test
    fun database_outfitDao_basicOperations() = runTest {
        // Given
        val outfit = OutfitEntity(
            1, "user", "Test Outfit", "Casual", null, "2025-11-01", "2025-11-01"
        )

        // When
        database.outfitDao().insertOutfit(outfit)
        val retrieved = database.outfitDao().getOutfitById(1)

        // Then
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("Test Outfit")
    }

    @Test
    fun database_calendarDao_basicOperations() = runTest {
        // Given
        val calendar = CalendarEntity(
            1, "user", 101, System.currentTimeMillis(),
            "Event", "Notes", null, null, null, null
        )

        // When
        database.calendarDao().insertScheduledOutfit(calendar)
        val retrieved = database.calendarDao().getScheduledOutfitById(1)

        // Then
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.eventName).isEqualTo("Event")
        assertThat(retrieved?.notes).isEqualTo("Notes")
    }

    @Test
    fun database_multipleInserts_work() = runTest {
        // Given
        val items = listOf(
            ItemEntity(1, "Item1", "Sub", "Cat", "Red", "S", "url", "Summer", 0),
            ItemEntity(2, "Item2", "Sub", "Cat", "Blue", "M", "url", "Winter", 0),
            ItemEntity(3, "Item3", "Sub", "Cat", "Green", "L", "url", "All Weather", 0)
        )

        // When
        database.itemDao().insertItems(items)
        val allItems = database.itemDao().getAllItems()

        // Then
        assertThat(allItems).hasSize(3)
    }

    @Test
    fun database_deleteOperations_work() = runTest {
        // Given
        val item = ItemEntity(
            1, "Test", "Sub", "Cat", "Red", "M", "url", "Summer", 0
        )
        database.itemDao().insertItem(item)

        // When
        database.itemDao().deleteItem(1)
        val retrieved = database.itemDao().getItemById(1)

        // Then
        assertThat(retrieved).isNull()
    }
}
