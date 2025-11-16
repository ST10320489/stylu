package com.iie.st10320489.stylu.data.local.dao

import androidx.room.*
import com.iie.st10320489.stylu.data.local.entities.OutfitEntity
import com.iie.st10320489.stylu.data.local.entities.OutfitItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutfitDao {

    @Query("SELECT * FROM outfits ORDER BY updatedAt DESC")
    fun getAllOutfitsFlow(): Flow<List<OutfitEntity>>

    @Query("SELECT * FROM outfits ORDER BY updatedAt DESC")
    suspend fun getAllOutfits(): List<OutfitEntity>

    @Query("SELECT * FROM outfits WHERE outfitId = :outfitId")
    suspend fun getOutfitById(outfitId: Int): OutfitEntity?

    @Query("SELECT * FROM outfits WHERE scheduledDate = :date")
    suspend fun getOutfitsByDate(date: String): List<OutfitEntity>


    @Query("SELECT * FROM outfits WHERE scheduledDate IS NOT NULL ORDER BY scheduledDate ASC")
    suspend fun getScheduledOutfits(): List<OutfitEntity>


    @Query("SELECT DISTINCT scheduledDate FROM outfits WHERE scheduledDate IS NOT NULL")
    fun getScheduledDatesFlow(): Flow<List<String>>

    @Query("SELECT * FROM outfits WHERE scheduledDate BETWEEN :startDate AND :endDate ORDER BY scheduledDate ASC")
    suspend fun getOutfitsInRange(startDate: String, endDate: String): List<OutfitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfit(outfit: OutfitEntity): Long


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfits(outfits: List<OutfitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfitItem(outfitItem: OutfitItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfitItems(outfitItems: List<OutfitItemEntity>)

    @Update
    suspend fun updateOutfit(outfit: OutfitEntity)


    @Query("UPDATE outfits SET scheduledDate = :scheduledDate, updatedAt = :updatedAt WHERE outfitId = :outfitId")
    suspend fun updateScheduledDate(outfitId: Int, scheduledDate: String?, updatedAt: String)

    @Query("DELETE FROM outfits WHERE outfitId = :outfitId")
    suspend fun deleteOutfit(outfitId: Int)

    @Query("DELETE FROM outfit_items WHERE outfitId = :outfitId")
    suspend fun deleteOutfitItems(outfitId: Int)

    @Transaction
    suspend fun deleteOutfitComplete(outfitId: Int) {
        deleteOutfitItems(outfitId)
        deleteOutfit(outfitId)
    }

    @Query("SELECT DISTINCT outfitId FROM outfits")
    suspend fun getAllOutfitIds(): List<Int>

    @Query("DELETE FROM outfits")
    suspend fun clearAllOutfits()

    @Query("DELETE FROM outfit_items")
    suspend fun clearAllOutfitItems()

    @Transaction
    suspend fun clearAllOutfitData() {
        clearAllOutfitItems()
        clearAllOutfits()
    }

    @Query("SELECT * FROM outfit_items WHERE outfitId = :outfitId ORDER BY zIndex ASC")
    suspend fun getOutfitLayout(outfitId: Int): List<OutfitItemEntity>
}