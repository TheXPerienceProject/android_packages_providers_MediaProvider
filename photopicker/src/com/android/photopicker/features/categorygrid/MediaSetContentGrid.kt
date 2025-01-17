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

package com.android.photopicker.features.categorygrid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.android.modules.utils.build.SdkLevel
import com.android.photopicker.R
import com.android.photopicker.core.components.EmptyState
import com.android.photopicker.core.components.MediaGridItem
import com.android.photopicker.core.components.mediaGrid
import com.android.photopicker.core.configuration.LocalPhotopickerConfiguration
import com.android.photopicker.core.configuration.PhotopickerRuntimeEnv
import com.android.photopicker.core.embedded.LocalEmbeddedState
import com.android.photopicker.core.features.LocalFeatureManager
import com.android.photopicker.core.navigation.LocalNavController
import com.android.photopicker.core.navigation.PhotopickerDestinations
import com.android.photopicker.core.obtainViewModel
import com.android.photopicker.core.selection.LocalSelection
import com.android.photopicker.core.theme.LocalWindowSizeClass
import com.android.photopicker.data.model.Group
import com.android.photopicker.extensions.navigateToPreviewMedia
import com.android.photopicker.features.preview.PreviewFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Primary composable for drawing the Mediaset content grid on
 * [PhotopickerDestinations.MEDIASET_CONTENT_GRID]
 *
 * @param viewModel - A viewModel override for the composable. Normally, this is fetched via hilt
 *   from the backstack entry by using obtainViewModel()
 * @param flow - stateflow holding the mediaset for which the media needs to be presented.
 */
@Composable
fun MediaSetContentGrid(
    flow: StateFlow<Group.MediaSet?>,
    viewModel: CategoryGridViewModel = obtainViewModel(),
) {
    val mediasetState by flow.collectAsStateWithLifecycle(initialValue = null)
    val mediaset = mediasetState
    Column(modifier = Modifier.fillMaxSize()) {
        when (mediaset) {
            null -> {}
            else -> {
                val mediasetItems = remember(mediaset) { viewModel.getMediaSetContent(mediaset) }
                MediasetContentGrid(mediasetItems = mediasetItems)
            }
        }
    }
}

/** Initialises all the states and media source required to load media for the input [mediaset]. */
@Composable
private fun MediasetContentGrid(
    mediasetItems: Flow<PagingData<MediaGridItem>>,
    viewModel: CategoryGridViewModel = obtainViewModel(),
) {
    val featureManager = LocalFeatureManager.current
    val isPreviewEnabled = remember { featureManager.isFeatureEnabled(PreviewFeature::class.java) }
    val navController = LocalNavController.current
    val items = mediasetItems.collectAsLazyPagingItems()
    // Collect the selection to notify the mediaGrid of selection changes.
    val selection by LocalSelection.current.flow.collectAsStateWithLifecycle()
    val selectionLimit = LocalPhotopickerConfiguration.current.selectionLimit
    val selectionLimitExceededMessage =
        stringResource(R.string.photopicker_selection_limit_exceeded_snackbar, selectionLimit)
    // Use the expanded layout any time the Width is Medium or larger.
    val isExpandedScreen: Boolean =
        when (LocalWindowSizeClass.current.widthSizeClass) {
            WindowWidthSizeClass.Medium -> true
            WindowWidthSizeClass.Expanded -> true
            else -> false
        }
    val state = rememberLazyGridState()
    val isEmbedded =
        LocalPhotopickerConfiguration.current.runtimeEnv == PhotopickerRuntimeEnv.EMBEDDED
    val host = LocalEmbeddedState.current?.host
    // Container encapsulating the mediaset title followed by its content in the form of a
    // grid, the content also includes date and month separators.
    Column(modifier = Modifier.fillMaxSize()) {
        val isEmptyAndNoMorePages =
            items.itemCount == 0 &&
                items.loadState.source.append is LoadState.NotLoading &&
                items.loadState.source.append.endOfPaginationReached
        when {
            isEmptyAndNoMorePages -> {
                val localConfig = LocalConfiguration.current
                val emptyStatePadding =
                    remember(localConfig) { (localConfig.screenHeightDp * .20).dp }
                val (title, body, icon) = getEmptyStateContentForMediaset()
                EmptyState(
                    modifier =
                        if (SdkLevel.isAtLeastU() && isEmbedded && host != null) {
                            // In embedded no need to give extra top padding to make empty
                            // state title and body clearly visible in collapse mode (small view)
                            Modifier.fillMaxWidth()
                        } else {
                            // Provide 20% of screen height as empty space above
                            Modifier.fillMaxWidth().padding(top = emptyStatePadding)
                        },
                    icon = icon,
                    title = title,
                    body = body,
                )
            }
            else -> {
                mediaGrid(
                    items = items,
                    isExpandedScreen = isExpandedScreen,
                    selection = selection,
                    onItemClick = { item ->
                        if (item is MediaGridItem.MediaItem) {
                            viewModel.handleMediaSetItemSelection(
                                item.media,
                                selectionLimitExceededMessage,
                            )
                        }
                    },
                    onItemLongPress = { item ->
                        // If the [PreviewFeature] is enabled, launch the preview route.
                        if (isPreviewEnabled && item is MediaGridItem.MediaItem) {
                            navController.navigateToPreviewMedia(item.media)
                        }
                    },
                    state = state,
                )
                // TODO : (b/383977279) Dispatch UI event to log loading of mediaset contents
            }
        }
    }
}

/**
 * Return a generic content for the empty state.
 *
 * @return a [Triple] that contains the [Title, Body, Icon] for the empty state.
 */
@Composable
private fun getEmptyStateContentForMediaset(): Triple<String, String, ImageVector> {
    return Triple(
        stringResource(R.string.photopicker_photos_empty_state_title),
        stringResource(R.string.photopicker_photos_empty_state_body),
        Icons.Outlined.Image,
    )
}
