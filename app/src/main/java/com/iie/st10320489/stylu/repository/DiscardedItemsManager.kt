package com.iie.st10320489.stylu.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.iie.st10320489.stylu.ui.item.models.DiscardedItem
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem

/**
 * Manages discarded items locally using SharedPreferences
 * Items are stored temporarily until user decides to permanently delete or restore them
 */
class DiscardedItemsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "discarded_items_prefs",
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    companion object {
        private const val KEY_DISCARDED_ITEMS = "discarded_items"
    }

    /**
     * Add an item to the discarded list
     */
    fun addDiscardedItem(item: WardrobeItem) {
        val discardedItems = getDiscardedItems().toMutableList()

        // Remove if already exists (to update timestamp)
        discardedItems.removeAll { it.item.itemId == item.itemId }

        // Add new discarded item
        discardedItems.add(DiscardedItem(item))

        saveDiscardedItems(discardedItems)
    }

    /**
     * Remove an item from the discarded list (restore it)
     */
    fun removeDiscardedItem(itemId: Int) {
        val discardedItems = getDiscardedItems().toMutableList()
        discardedItems.removeAll { it.item.itemId == itemId }
        saveDiscardedItems(discardedItems)
    }

    /**
     * Get all discarded items
     */
    fun getDiscardedItems(): List<DiscardedItem> {
        val json = prefs.getString(KEY_DISCARDED_ITEMS, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<DiscardedItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if an item is discarded
     */
    fun isItemDiscarded(itemId: Int): Boolean {
        return getDiscardedItems().any { it.item.itemId == itemId }
    }

    /**
     * Clear all discarded items
     */
    fun clearAll() {
        prefs.edit().remove(KEY_DISCARDED_ITEMS).apply()
    }

    /**
     * Get count of discarded items
     */
    fun getDiscardedCount(): Int {
        return getDiscardedItems().size
    }

    private fun saveDiscardedItems(items: List<DiscardedItem>) {
        val json = gson.toJson(items)
        prefs.edit().putString(KEY_DISCARDED_ITEMS, json).apply()
    }
}