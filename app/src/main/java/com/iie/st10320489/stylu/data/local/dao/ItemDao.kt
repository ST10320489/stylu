package com.iie.st10320489.stylu.data.local.dao

import androidx.room.*
import com.iie.st10320489.stylu.data.local.entities.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun getAllItemsFlow(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    suspend fun getAllItems(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE itemId = :itemId")
    suspend fun getItemById(itemId: Int): ItemEntity?

    @Query("SELECT * FROM items WHERE category = :category")
    suspend fun getItemsByCategory(category: String): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE itemId = :itemId")
    suspend fun deleteItem(itemId: Int)

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("SELECT COUNT(*) FROM items WHERE category = :category")
    suspend fun getItemCountByCategory(category: String): Int

    @Query("SELECT * FROM items WHERE itemId = :itemId LIMIT 1")
    suspend fun getItemByIdOne(itemId: Int): ItemEntity?
}
