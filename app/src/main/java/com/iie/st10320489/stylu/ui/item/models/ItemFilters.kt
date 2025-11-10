package com.iie.st10320489.stylu.ui.item.models

data class ItemFilters(
    val colors: Set<String> = emptySet(),
    val sizes: Set<String> = emptySet(),
    val weatherTags: Set<String> = emptySet(),
    val timesWornFilter: TimesWornFilter = TimesWornFilter.ALL
) {
    fun isActive(): Boolean {
        return colors.isNotEmpty() ||
                sizes.isNotEmpty() ||
                weatherTags.isNotEmpty() ||
                timesWornFilter != TimesWornFilter.ALL
    }

    fun getActiveFilterCount(): Int {
        var count = 0
        if (colors.isNotEmpty()) count++
        if (sizes.isNotEmpty()) count++
        if (weatherTags.isNotEmpty()) count++
        if (timesWornFilter != TimesWornFilter.ALL) count++
        return count
    }
}

enum class TimesWornFilter {
    ALL,
    NEVER_WORN,    // 0 times
    LEAST_WORN,    // 1-5 times
    MOST_WORN      // 6+ times
}