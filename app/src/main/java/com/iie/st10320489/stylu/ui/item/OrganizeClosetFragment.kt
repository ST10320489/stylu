package com.iie.st10320489.stylu.ui.item

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentOrganizeClosetBinding
import com.iie.st10320489.stylu.repository.DiscardedItemsManager
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem
import kotlin.math.abs

class OrganizeClosetFragment : Fragment() {

    private var _binding: FragmentOrganizeClosetBinding? = null
    private val binding get() = _binding!!

    private lateinit var discardedItemsManager: DiscardedItemsManager

    private var itemsToOrganize: MutableList<WardrobeItem> = mutableListOf()
    private var currentIndex = 0
    private var keptItems = mutableListOf<WardrobeItem>()
    private var discardedItems = mutableListOf<WardrobeItem>()

    // Touch handling variables
    private var initialX = 0f
    private var initialY = 0f
    private var dX = 0f
    private var dY = 0f

    companion object {
        private const val SWIPE_THRESHOLD = 300f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get items from Safe Args
        itemsToOrganize = OrganizeClosetFragmentArgs.fromBundle(requireArguments())
            .items.toMutableList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrganizeClosetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        discardedItemsManager = DiscardedItemsManager(requireContext())

        if (itemsToOrganize.isEmpty()) {
            Toast.makeText(requireContext(), "No items to organize", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        setupSwipeGesture()
        setupActionButtons()
        displayCurrentItem()
    }

    private fun setupSwipeGesture() {
        binding.swipeCard.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = view.x
                    initialY = view.y
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    view.x = event.rawX + dX
                    view.y = event.rawY + dY

                    // Calculate rotation based on horizontal movement
                    val deltaX = view.x - initialX
                    val rotation = deltaX / 20f
                    view.rotation = rotation

                    // Show visual feedback
                    updateSwipeOverlay(deltaX)

                    true
                }

                MotionEvent.ACTION_UP -> {
                    val deltaX = view.x - initialX

                    when {
                        deltaX > SWIPE_THRESHOLD -> {
                            // Swiped right - Keep
                            animateSwipeRight {
                                keepCurrentItem()
                            }
                        }
                        deltaX < -SWIPE_THRESHOLD -> {
                            // Swiped left - Discard
                            animateSwipeLeft {
                                discardCurrentItem()
                            }
                        }
                        else -> {
                            // Return to center
                            animateReturnToCenter()
                        }
                    }

                    true
                }

                else -> false
            }
        }
    }

    private fun setupActionButtons() {
        binding.fabKeep.setOnClickListener {
            animateSwipeRight {
                keepCurrentItem()
            }
        }

        binding.fabDiscard.setOnClickListener {
            animateSwipeLeft {
                discardCurrentItem()
            }
        }
    }

    private fun displayCurrentItem() {
        if (currentIndex >= itemsToOrganize.size) {
            // All items reviewed
            navigateToComplete()
            return
        }

        val item = itemsToOrganize[currentIndex]

        // Update progress
        binding.tvProgress.text = "${currentIndex + 1} / ${itemsToOrganize.size}"

        // Display item details
        binding.tvItemName.text = if (!item.name.isNullOrEmpty()) {
            item.name
        } else if (!item.colour.isNullOrEmpty()) {
            "${item.colour} ${item.subcategory}"
        } else {
            item.subcategory
        }

        binding.tvItemDetails.text = buildString {
            append(item.category)
            if (!item.size.isNullOrEmpty()) {
                append(" • ${item.size}")
            }
            if (!item.colour.isNullOrEmpty()) {
                append(" • ${item.colour}")
            }
        }

        binding.tvTimesWorn.text = getString(R.string.times_worn_label, item.timesWorn)

        // Load image
        Glide.with(this)
            .load(item.imageUrl)
            .placeholder(R.drawable.default_img)
            .error(R.drawable.default_img)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .fitCenter()
            .into(binding.ivItemImage)

        // Reset card position and overlays
        binding.swipeCard.apply {
            x = 0f
            y = 0f
            rotation = 0f
            alpha = 1f
        }
        binding.keepOverlay.visibility = View.GONE
        binding.discardOverlay.visibility = View.GONE
    }

    private fun updateSwipeOverlay(deltaX: Float) {
        when {
            deltaX > 50f -> {
                // Swiping right - show keep overlay
                binding.keepOverlay.visibility = View.VISIBLE
                binding.discardOverlay.visibility = View.GONE
                binding.keepOverlay.alpha = (deltaX / SWIPE_THRESHOLD).coerceIn(0f, 0.5f)
            }
            deltaX < -50f -> {
                // Swiping left - show discard overlay
                binding.keepOverlay.visibility = View.GONE
                binding.discardOverlay.visibility = View.VISIBLE
                binding.discardOverlay.alpha = (abs(deltaX) / SWIPE_THRESHOLD).coerceIn(0f, 0.5f)
            }
            else -> {
                binding.keepOverlay.visibility = View.GONE
                binding.discardOverlay.visibility = View.GONE
            }
        }
    }

    private fun animateSwipeRight(onComplete: () -> Unit) {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        val animX = ObjectAnimator.ofFloat(binding.swipeCard, "x", screenWidth)
        val animRotation = ObjectAnimator.ofFloat(binding.swipeCard, "rotation", 45f)
        val animAlpha = ObjectAnimator.ofFloat(binding.swipeCard, "alpha", 0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animX, animRotation, animAlpha)
        animatorSet.duration = 300

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })

        binding.keepOverlay.visibility = View.VISIBLE
        binding.keepOverlay.animate().alpha(0.7f).duration = 300

        animatorSet.start()
    }

    private fun animateSwipeLeft(onComplete: () -> Unit) {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        val animX = ObjectAnimator.ofFloat(binding.swipeCard, "x", -screenWidth)
        val animRotation = ObjectAnimator.ofFloat(binding.swipeCard, "rotation", -45f)
        val animAlpha = ObjectAnimator.ofFloat(binding.swipeCard, "alpha", 0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animX, animRotation, animAlpha)
        animatorSet.duration = 300

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })

        binding.discardOverlay.visibility = View.VISIBLE
        binding.discardOverlay.animate().alpha(0.7f).duration = 300

        animatorSet.start()
    }

    private fun animateReturnToCenter() {
        val animX = ObjectAnimator.ofFloat(binding.swipeCard, "x", 0f)
        val animY = ObjectAnimator.ofFloat(binding.swipeCard, "y", 0f)
        val animRotation = ObjectAnimator.ofFloat(binding.swipeCard, "rotation", 0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animX, animY, animRotation)
        animatorSet.duration = 200
        animatorSet.start()

        binding.keepOverlay.visibility = View.GONE
        binding.discardOverlay.visibility = View.GONE
    }

    private fun keepCurrentItem() {
        val item = itemsToOrganize[currentIndex]
        keptItems.add(item)

        currentIndex++
        displayCurrentItem()
    }

    private fun discardCurrentItem() {
        val item = itemsToOrganize[currentIndex]
        discardedItems.add(item)

        // Save to discarded items manager
        discardedItemsManager.addDiscardedItem(item)

        currentIndex++
        displayCurrentItem()
    }

    private fun navigateToComplete() {
        val totalReviewed = itemsToOrganize.size
        val keptCount = keptItems.size
        val discardedCount = discardedItems.size

        try {
            val action = OrganizeClosetFragmentDirections
                .actionOrganizeClosetToComplete(totalReviewed, keptCount, discardedCount)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Navigation error: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}