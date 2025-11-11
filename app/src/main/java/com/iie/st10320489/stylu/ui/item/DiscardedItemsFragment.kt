package com.iie.st10320489.stylu.ui.item

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentDiscardedItemsBinding
import com.iie.st10320489.stylu.repository.DiscardedItemsManager
import com.iie.st10320489.stylu.repository.ItemRepository
import com.iie.st10320489.stylu.ui.item.models.DiscardedItem
import kotlinx.coroutines.launch

class DiscardedItemsFragment : Fragment() {

    private var _binding: FragmentDiscardedItemsBinding? = null
    private val binding get() = _binding!!

    private lateinit var discardedItemsManager: DiscardedItemsManager
    private lateinit var itemRepository: ItemRepository
    private lateinit var discardedAdapter: DiscardedItemAdapter

    private var discardedItems: List<DiscardedItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscardedItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        discardedItemsManager = DiscardedItemsManager(requireContext())
        itemRepository = ItemRepository(requireContext())

        setupRecyclerView()
        setupButtons()
        loadDiscardedItems()
    }

    private fun setupRecyclerView() {
        val spanCount = 2
        val spacing = 16.dpToPx()
        val includeEdge = true

        binding.rvDiscardedItems.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvDiscardedItems.addItemDecoration(
            ItemFragment.GridSpacingItemDecoration(spanCount, spacing, includeEdge)
        )

        discardedAdapter = DiscardedItemAdapter(
            onRestoreClick = { item ->
                confirmRestore(item)
            },
            onDeleteClick = { item ->
                confirmDeletePermanently(item)
            }
        )

        binding.rvDiscardedItems.adapter = discardedAdapter
    }

    private fun setupButtons() {
        binding.btnRestoreAll.setOnClickListener {
            if (discardedItems.isNotEmpty()) {
                confirmRestoreAll()
            }
        }

        binding.btnDeleteAll.setOnClickListener {
            if (discardedItems.isNotEmpty()) {
                confirmDeleteAll()
            }
        }
    }

    private fun loadDiscardedItems() {
        discardedItems = discardedItemsManager.getDiscardedItems()

        if (discardedItems.isEmpty()) {
            showEmptyState(true)
            binding.actionButtonsLayout.visibility = View.GONE
        } else {
            showEmptyState(false)
            binding.actionButtonsLayout.visibility = View.VISIBLE
            discardedAdapter.submitList(discardedItems)
        }
    }

    private fun confirmRestore(item: DiscardedItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.restore_item)
            .setMessage(getString(R.string.confirm_restore_item, item.item.name ?: item.item.subcategory))
            .setPositiveButton(R.string.restore_item) { _, _ ->
                restoreItem(item)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeletePermanently(item: DiscardedItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_permanently)
            .setMessage(getString(R.string.confirm_delete_item))
            .setPositiveButton(R.string.delete) { _, _ ->
                deletePermanently(item)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmRestoreAll() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.restore_all)
            .setMessage(R.string.confirm_restore_all)
            .setPositiveButton(R.string.restore_all) { _, _ ->
                restoreAll()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAll() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_all)
            .setMessage(R.string.confirm_delete_all)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                deleteAll()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun restoreItem(item: DiscardedItem) {
        // Remove from discarded list
        discardedItemsManager.removeDiscardedItem(item.item.itemId)

        Toast.makeText(
            requireContext(),
            R.string.item_restored,
            Toast.LENGTH_SHORT
        ).show()

        // Reload list
        loadDiscardedItems()
    }

    private fun deletePermanently(item: DiscardedItem) {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val result = itemRepository.deleteItem(item.item.itemId)
                result.onSuccess {
                    // Remove from discarded list
                    discardedItemsManager.removeDiscardedItem(item.item.itemId)

                    Toast.makeText(
                        requireContext(),
                        R.string.item_deleted,
                        Toast.LENGTH_SHORT
                    ).show()

                    // Reload list
                    loadDiscardedItems()
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.failed_to_delete, error.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_message, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun restoreAll() {
        discardedItemsManager.clearAll()

        Toast.makeText(
            requireContext(),
            R.string.items_restored,
            Toast.LENGTH_SHORT
        ).show()

        loadDiscardedItems()
    }

    private fun deleteAll() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                var successCount = 0
                var failCount = 0

                for (discardedItem in discardedItems) {
                    val result = itemRepository.deleteItem(discardedItem.item.itemId)
                    result.onSuccess {
                        successCount++
                    }.onFailure {
                        failCount++
                    }
                }

                // Clear all from discarded list
                discardedItemsManager.clearAll()

                val message = if (failCount == 0) {
                    getString(R.string.items_deleted)
                } else {
                    "Deleted $successCount items, failed to delete $failCount items"
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

                // Reload list
                loadDiscardedItems()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_message, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        _binding?.progressBar?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        _binding?.emptyStateLayout?.visibility = if (show) View.VISIBLE else View.GONE
        _binding?.rvDiscardedItems?.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}