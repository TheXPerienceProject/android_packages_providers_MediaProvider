/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.photopicker.features.search

import android.util.Log
import com.android.photopicker.features.search.SearchViewModel.Companion.ZERO_STATE_SEARCH_QUERY
import com.android.photopicker.features.search.model.SearchSuggestion
import com.android.photopicker.features.search.model.SearchSuggestionType
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Class that implements cache for SearchSuggestions that stores a list of suggestions for a given
 * prefix
 */
class SearchSuggestionCache {
    private val TAG = "SearchSuggestionCache"
    private var cachedSuggestions: MutableMap<String, MutableSet<SearchSuggestion>> =
        Collections.synchronizedMap(HashMap())

    /**
     * Retrieves suggestions for a given prefix from the cache.
     *
     * If you need to iterate over the returned collection, please wrap it with
     * [synchronized(collection) { ... }] to ensure thread safety.
     *
     * @param query The prefix to search for.
     * @return An ordered collection of suggestions for the prefix, or null if no suggestions are
     *   found.
     */
    fun getSuggestions(query: String): Collection<SearchSuggestion>? {
        return cachedSuggestions[query]
    }

    /**
     * Adds suggestions for a given prefix to the cache.
     *
     * @param query The prefix to add suggestions for.
     * @param suggestions [SearchSuggestions] object containing different types of suggestions.
     */
    fun addSuggestions(query: String, suggestions: SearchSuggestions) {
        val cachedSuggestionSet = Collections.synchronizedSet(LinkedHashSet<SearchSuggestion>())
        cachedSuggestionSet.addAll(suggestions.history)
        cachedSuggestionSet.addAll(suggestions.face)
        cachedSuggestionSet.addAll(suggestions.other)
        cachedSuggestions[query] = cachedSuggestionSet
    }

    /** Clears the cached suggestions from the map. */
    fun clearSuggestions() {
        Log.d(TAG, "Clearing search suggestions cache.")
        cachedSuggestions.clear()
    }

    /**
     * Update zero-state history suggestions in cache. It creates a HISTORY suggestion from the
     * input search text query and adds it to the top of the zero-state suggestions cache.
     *
     * @param query Input search text query.
     */
    fun updateHistorySuggestion(query: String) {
        val newHistorySuggestion =
            SearchSuggestion(
                mediaSetId = null,
                authority = null,
                displayText = query.trim(),
                type = SearchSuggestionType.HISTORY,
                icon = null,
            )

        updateHistorySuggestion(newHistorySuggestion)
    }

    /**
     * Update zero-state history suggestions in cache. It creates a HISTORY suggestion from the
     * input search suggestions query and adds it to the front of the zero-state suggestions cache.
     *
     * @param suggestion Input search suggestion query.
     */
    fun updateHistorySuggestion(suggestion: SearchSuggestion) {
        val historySuggestion =
            when (suggestion.type) {
                SearchSuggestionType.HISTORY -> suggestion
                else -> suggestion.copy(type = SearchSuggestionType.HISTORY)
            }

        val newCachedSuggestions = Collections.synchronizedSet(LinkedHashSet<SearchSuggestion>())
        newCachedSuggestions.add(historySuggestion)

        synchronized(cachedSuggestions) {
            val zeroStateSuggestions = cachedSuggestions[ZERO_STATE_SEARCH_QUERY]
            zeroStateSuggestions?.let { newCachedSuggestions.addAll(zeroStateSuggestions) }
        }

        cachedSuggestions[ZERO_STATE_SEARCH_QUERY] = newCachedSuggestions
    }
}
