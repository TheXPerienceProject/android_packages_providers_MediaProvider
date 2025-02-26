/*
 * Copyright 2025 The Android Open Source Project
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

import android.net.Uri
import com.android.photopicker.data.model.Icon
import com.android.photopicker.data.model.MediaSource
import com.android.photopicker.features.search.SearchViewModel.Companion.ZERO_STATE_SEARCH_QUERY
import com.android.photopicker.features.search.model.SearchSuggestion
import com.android.photopicker.features.search.model.SearchSuggestionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchSuggestionCacheTest {
    @Test
    fun testUpdateHistorySuggestion() {
        val suggestionsCache: SearchSuggestionCache = SearchSuggestionCache()

        val searchSuggestion1 =
            SearchSuggestion(
                mediaSetId = null,
                authority = null,
                displayText = "Sunny",
                type = SearchSuggestionType.HISTORY,
                icon = null,
            )

        // Update history with a duplicate search text query
        var actualSuggestionsOrder = suggestionsCache.getSuggestions(ZERO_STATE_SEARCH_QUERY)
        suggestionsCache.updateHistorySuggestion("Sunny")
        actualSuggestionsOrder = suggestionsCache.getSuggestions(ZERO_STATE_SEARCH_QUERY)
        assertThat(actualSuggestionsOrder).isNotNull()
        assertThat(listOf(searchSuggestion1)).isEqualTo(actualSuggestionsOrder!!.toList())

        val searchSuggestion2 =
            SearchSuggestion(
                mediaSetId = "media-set-id-2",
                authority = "cloud.authority",
                displayText = "Vacation",
                type = SearchSuggestionType.ALBUM,
                icon = null,
            )

        // Update history with a duplicate search history suggestion query
        suggestionsCache.updateHistorySuggestion(searchSuggestion2)
        actualSuggestionsOrder = suggestionsCache.getSuggestions(ZERO_STATE_SEARCH_QUERY)
        assertThat(actualSuggestionsOrder).isNotNull()
        assertThat(actualSuggestionsOrder!!.toList())
            .isEqualTo(
                listOf(
                    searchSuggestion2.copy(type = SearchSuggestionType.HISTORY),
                    searchSuggestion1,
                )
            )
    }

    @Test
    fun testUpdateHistoryWithDuplicateSuggestion() {
        val suggestionsCache: SearchSuggestionCache = SearchSuggestionCache()

        val searchSuggestion1 =
            SearchSuggestion(
                mediaSetId = "media-set-id-1",
                authority = "cloud.authority",
                displayText = "Beach",
                type = SearchSuggestionType.HISTORY,
                icon = null,
            )
        val searchSuggestion2 =
            SearchSuggestion(
                mediaSetId = null,
                authority = null,
                displayText = "Sunny",
                type = SearchSuggestionType.HISTORY,
                icon = null,
            )
        val searchSuggestion3 =
            SearchSuggestion(
                mediaSetId = "media-set-id-3",
                authority = "cloud.authority",
                displayText = "Vacation",
                type = SearchSuggestionType.ALBUM,
                icon = null,
            )

        suggestionsCache.addSuggestions(
            ZERO_STATE_SEARCH_QUERY,
            SearchSuggestions(
                listOf(searchSuggestion1, searchSuggestion2),
                listOf(searchSuggestion3),
            ),
        )

        // Add search suggestions to cache
        var actualSuggestionsOrder = suggestionsCache.getSuggestions(ZERO_STATE_SEARCH_QUERY)
        assertThat(actualSuggestionsOrder).isNotNull()
        assertThat(actualSuggestionsOrder!!.toList())
            .isEqualTo(listOf(searchSuggestion1, searchSuggestion2, searchSuggestion3))

        // Update history with a duplicate search text query
        suggestionsCache.updateHistorySuggestion("Sunny")
        actualSuggestionsOrder = suggestionsCache.getSuggestions(ZERO_STATE_SEARCH_QUERY)
        assertThat(actualSuggestionsOrder).isNotNull()
        assertThat(actualSuggestionsOrder!!.toList())
            .isEqualTo(listOf(searchSuggestion2, searchSuggestion1, searchSuggestion3))

        // Update history with a duplicate search history suggestion query
        suggestionsCache.updateHistorySuggestion(searchSuggestion1)
        actualSuggestionsOrder = suggestionsCache.getSuggestions(ZERO_STATE_SEARCH_QUERY)
        assertThat(actualSuggestionsOrder).isNotNull()
        assertThat(actualSuggestionsOrder!!.toList())
            .isEqualTo(listOf(searchSuggestion1, searchSuggestion2, searchSuggestion3))

        // Update history with a duplicate search album suggestion query
        suggestionsCache.updateHistorySuggestion(searchSuggestion3)
        actualSuggestionsOrder = suggestionsCache.getSuggestions(ZERO_STATE_SEARCH_QUERY)
        assertThat(actualSuggestionsOrder).isNotNull()
        assertThat(actualSuggestionsOrder!!.toList())
            .isEqualTo(
                listOf(
                    searchSuggestion3.copy(type = SearchSuggestionType.HISTORY),
                    searchSuggestion1,
                    searchSuggestion2,
                    searchSuggestion3,
                )
            )
    }

    @Test
    fun testUpdateHistoryWithFaceSuggestion() {
        val suggestionsCache = SearchSuggestionCache()

        val faceSearchSuggestion =
            SearchSuggestion(
                mediaSetId = "media-set-id-1",
                authority = "cloud.authority",
                displayText = null,
                type = SearchSuggestionType.FACE,
                icon = Icon(uri = Uri.parse(""), mediaSource = MediaSource.LOCAL),
            )

        // Try to add history suggestion to cache
        suggestionsCache.updateHistorySuggestion(faceSearchSuggestion)

        // Check that is wasn't added because it has no display text
        var actualSuggestionsOrder = suggestionsCache.getSuggestions(ZERO_STATE_SEARCH_QUERY)
        assertThat(actualSuggestionsOrder).isNull()
    }
}
