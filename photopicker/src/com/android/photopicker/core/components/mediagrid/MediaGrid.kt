/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.photopicker.core.components

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.CloudMediaProviderContract.AlbumColumns.ALBUM_ID_CAMERA
import android.provider.CloudMediaProviderContract.AlbumColumns.ALBUM_ID_FAVORITES
import android.provider.CloudMediaProviderContract.AlbumColumns.ALBUM_ID_VIDEOS
import android.provider.MediaStore.Files.FileColumns._SPECIAL_FORMAT_ANIMATED_WEBP
import android.provider.MediaStore.Files.FileColumns._SPECIAL_FORMAT_GIF
import android.provider.MediaStore.Files.FileColumns._SPECIAL_FORMAT_MOTION_PHOTO
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.android.modules.utils.build.SdkLevel
import com.android.photopicker.R
import com.android.photopicker.core.animations.emphasizedAccelerateFloat
import com.android.photopicker.core.animations.springDefaultEffectFloat
import com.android.photopicker.core.components.MediaGridItem.Companion.defaultBuildContentType
import com.android.photopicker.core.configuration.LocalPhotopickerConfiguration
import com.android.photopicker.core.configuration.PhotopickerRuntimeEnv
import com.android.photopicker.core.embedded.LocalEmbeddedState
import com.android.photopicker.core.glide.Resolution
import com.android.photopicker.core.glide.loadMedia
import com.android.photopicker.core.theme.CustomAccentColorScheme
import com.android.photopicker.data.model.Group.Album
import com.android.photopicker.data.model.Media
import com.android.photopicker.extensions.circleBackground
import com.android.photopicker.extensions.insertMonthSeparators
import com.android.photopicker.extensions.itemIndexAtPosition
import com.android.photopicker.extensions.toMediaGridItemFromAlbum
import com.android.photopicker.extensions.toMediaGridItemFromMedia
import com.android.photopicker.extensions.transferScrollableTouchesToHostInEmbedded
import com.android.photopicker.features.categorygrid.categoryIcon.IconGrid
import com.android.photopicker.util.LocalLocalizationHelper
import com.android.photopicker.util.applyChoice
import com.android.photopicker.util.applyWhen
import com.android.photopicker.util.calculateWindowRect
import com.android.photopicker.util.getMediaContentDescription
import java.text.DateFormat
import java.text.NumberFormat
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The number of grid cells per row for Phone / narrow layouts */
private val CELLS_PER_ROW: Int = 3

/** The number of grid cells per row for Tablet / expanded layouts */
private val CELLS_PER_ROW_EXPANDED: Int = 4

/** The default (if not overridden) amount of content padding below the grid */
private val MEASUREMENT_DEFAULT_CONTENT_PADDING = 150.dp

/** The amount of padding to use around each cell in the grid. */
private val MEASUREMENT_CELL_SPACING = 1.dp

/** The size of the "push in" when an item in the grid is selected */
private val MEASUREMENT_SELECTED_INTERNAL_PADDING = 12.dp

/** The distance the mimetype icon is away from the edge */
private val MEASUREMENT_MIMETYPE_ICON_EDGE_PADDING = 4.dp

/** The size of the spacer between the duration text and the mimetype icon */
private val MEASUREMENT_DURATION_TEXT_SPACER_SIZE = 2.dp

/** The size of the "push in" when an item in the grid is not selected */
private val MEASUREMENT_NOT_SELECTED_INTERNAL_PADDING = 0.dp

/** The font size of the selected position number */
private val MEASUREMENT_SELECTED_POSITION_FONT_SIZE = 14.sp

/** The offset to apply to the selected icon to shift it over the corner of the image */
private val MEASUREMENT_SELECTED_ICON_OFFSET = 8.dp
/**
 * The offset to apply to the selected icon to shift it over the corner of the image for a
 * highlighted item
 */
private val MEASUREMENT_SELECTED_ICON_HIGHLIGHT_ITEM_OFFSET = 4.dp

/**
 * The offset to apply to the badge icon to shift it over the corner of the image for a badged item
 */
private val MEASUREMENT_BADGE_ICON_OFFSET = 8.dp

/** Border width for the selected icon */
private val MEASUREMENT_SELECTED_ICON_BORDER = 2.dp

/** The radius to use for the corners of grid cells that are selected */
private val MEASUREMENT_SELECTED_CORNER_RADIUS = 16.dp

/** The radius to use for the corners of grid cells in the highlight grid */
private val MEASUREMENT_HIGHLIGHT_GRID_CELLS_RADIUS = 28.dp

/** The height of the unselected cells in the highlight grid */
private val MEASUREMENT_HIGHLIGHT_GRID_UNSELECTED_CELL_HEIGHT = 176.dp

/** The width of the unselected cells in the highlight grid */
private val MEASUREMENT_HIGHLIGHT_GRID_UNSELECTED_CELL_WIDTH = 154.dp

/** The height of the selected cells in the highlight grid */
private val MEASUREMENT_HIGHLIGHT_GRID_SELECTED_CELL_HEIGHT = 152.dp

/** The height of the unselected cells in the highlight grid */
private val MEASUREMENT_HIGHLIGHT_GRID_SELECTED_CELL_WIDTH = 130.dp

/** The padding to use around the default separator's content. */
private val MEASUREMENT_SEPARATOR_PADDING = 16.dp

/** The radius to use for the corners of grid cells that are selected */
val MEASUREMENT_SELECTED_CORNER_RADIUS_FOR_ALBUMS = 16.dp

/** The size for the icon used inside the default album thumbnails */
val MEASUREMENT_DEFAULT_ALBUM_THUMBNAIL_ICON_SIZE = 56.dp

/** The padding for the icon for the default album thumbnails */
val MEASUREMENT_DEFAULT_ALBUM_THUMBNAIL_ICON_PADDING = 16.dp

/** Additional padding between album items */
val MEASUREMENT_DEFAULT_ALBUM_BOTTOM_PADDING = 16.dp

/** Size of the spacer between the album icon and the album display label */
val MEASUREMENT_DEFAULT_ALBUM_LABEL_SPACER_SIZE = 12.dp

/**
 * Composable for creating a MediaItemGrid from a [LazyPagingItems] source of data that implements
 * [Media] or [Album].
 *
 * The mediaGrid uses a custom wrapper class [MediaGridItem] to distinguish between individual grid
 * cells (like media or albums) and horizontal separators. To convert a [Media] into a
 * [MediaGridItem], use the flow extension method [toMediaGridItemFromMedia]. To convert an [Album]
 * into a [MediaGridItem], use the flow extension method [toMediaGridItemFromAlbum]. Additionally,
 * to insert month-based separators, the [kotlinx.coroutines.flow.Flow] extension method
 * [insertMonthSeparators] can be used.
 *
 * @param items The [LazyPagingItems] that have been collected, representing the data to display.
 *   See [collectAsLazyPagingItems] to transform a PagingData flow into this format.
 * @param focusItem Optional [MediaGridItem] that should request focus when the media grid is drawn.
 * @param selection The set of currently selected [Media] items. Used to highlight selected items.
 * @param onItemClick Callback invoked when a grid item (e.g., media, album) is clicked.
 * @param onItemLongPress Callback invoked when a grid item is long-pressed. Defaults to no-op.
 * @param isExpandedScreen Whether the device is using an expanded screen size. This impacts the
 *   default number of cells shown per row if `initialColumns` is not set directly.
 * @param initialColumns The initial number of cells per row. Defaults based on [isExpandedScreen].
 * @param gridCellPadding Padding between grid cells. Defaults to [MEASUREMENT_CELL_SPACING].
 * @param modifier A [Modifier] to apply to the top-level [LazyVerticalGrid] this composable
 *   creates.
 * @param state The [LazyGridState] for observing and controlling the grid's scroll state. Defaults
 *   to a remembered state.
 * @param contentPadding [PaddingValues] that will be applied to the [LazyVerticalGrid]. Defaults to
 *   padding at the bottom.
 * @param userScrollEnabled Whether the user is able to scroll the grid. Defaults to true.
 * @param spanFactory Optional factory to determine the [GridItemSpan] for each item, based on the
 *   item and current column count. Defaults to [defaultBuildSpan].
 * @param contentTypeFactory Optional factory to determine the content type for each item, used for
 *   efficient item recycling. Defaults to [defaultBuildContentType].
 * @param contentItemFactory Optional factory to compose individual [MediaGridItem]s (media, albums,
 *   categories, etc.). Receives the item, its selection state, click/long-press handlers, and a
 *   date formatter. Defaults to a factory providing default item rendering.
 * @param contentSeparatorFactory Optional factory to compose [MediaGridItem.SeparatorItem]s.
 *   Defaults to [defaultBuildSeparator].
 * @param contentPlaceholderFactory Optional factory to compose placeholders . Defaults to
 *   [defaultBuildPlaceholder]
 * @param bannerContent Optional composable content to be displayed as a banner at the top of the
 *   grid.
 * @param highlightMediaContent Optional custom implementation for highlight media content to be
 *   displayed at the top of the photogrid.
 * @Param arePlaceholdersEnabled Whether placeholders are enabled in the grid. Defaults to false.
 *   When enabled, every item that has not yet been loaded in the grid will display a placeholder.
 *   This placeholder occupies the same width, height, and aspect ratio as the actual item
 *   ([MediaGridItem.MediaItem]) it represents. Until the item is loaded, the grid will receive a
 *   null item in its place.
 */
@Composable
fun mediaGrid(
    items: LazyPagingItems<MediaGridItem>,
    focusItem: MediaGridItem? = null,
    selection: Set<Media>,
    onItemClick: (item: MediaGridItem) -> Unit,
    onItemLongPress: (item: MediaGridItem) -> Unit = {},
    isExpandedScreen: Boolean = false,
    pinchToZoomEnabled: Boolean = false,
    pinchToZoomMaxColumns: Int = 5,
    pinchToZoomMinColumns: Int = 2,
    onZoomAtMaxZoom: (MediaGridItem) -> Unit = {},
    initialColumns: Int = getCellsPerRow(isExpandedScreen),
    gridCellPadding: Dp = MEASUREMENT_CELL_SPACING,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(bottom = MEASUREMENT_DEFAULT_CONTENT_PADDING),
    userScrollEnabled: Boolean = true,
    arePlaceholdersEnabled: Boolean = false,
    spanFactory: (item: MediaGridItem?, currentColumns: Int) -> GridItemSpan = ::defaultBuildSpan,
    contentTypeFactory: (item: MediaGridItem?) -> Int = ::defaultBuildContentType,
    contentItemFactory:
        @Composable
        (
            item: MediaGridItem,
            isSelected: Boolean,
            onClick: ((item: MediaGridItem) -> Unit)?,
            onLongPress: ((item: MediaGridItem) -> Unit)?,
            dateFormat: DateFormat,
        ) -> Unit =
        { item, isSelected, onClick, onLongPress, dateFormat ->
            defaultContentItemFactory(
                item = item,
                isSelected = isSelected,
                onClick = onClick,
                onLongPress = onLongPress,
                dragSelectionEnabled = false,
                dateFormat = dateFormat,
                focusItem = focusItem,
                selection = selection,
            )
        },
    contentSeparatorFactory: @Composable (item: MediaGridItem.SeparatorItem) -> Unit = { item ->
        defaultBuildSeparator(item)
    },
    contentPlaceholderFactory: @Composable () -> Unit = { defaultBuildPlaceholder() },
    bannerContent: (@Composable () -> Unit)? = null,
    highlightMediaContent: (@Composable () -> Unit)? = null,
) {
    mediaGrid(
        items = items,
        focusItem = focusItem,
        selection = selection,
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        dragSelectionEnabled = false,
        isExpandedScreen = isExpandedScreen,
        initialColumns = initialColumns,
        gridCellPadding = gridCellPadding,
        pinchToZoomEnabled = pinchToZoomEnabled,
        pinchToZoomMaxColumns = pinchToZoomMaxColumns,
        pinchToZoomMinColumns = pinchToZoomMinColumns,
        onZoomAtMaxZoom = onZoomAtMaxZoom,
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
        arePlaceholdersEnabled = arePlaceholdersEnabled,
        spanFactory = spanFactory,
        contentTypeFactory = contentTypeFactory,
        contentItemFactory = contentItemFactory,
        contentSeparatorFactory = contentSeparatorFactory,
        contentPlaceholderFactory = contentPlaceholderFactory,
        bannerContent = bannerContent,
        highlightMediaContent = highlightMediaContent,
    )
}

/**
 * Composable for creating a MediaItemGrid from a [LazyPagingItems] source of data that implements
 * [Media] or [Album].
 *
 * The mediaGrid uses a custom wrapper class [MediaGridItem] to distinguish between individual grid
 * cells and horizontal separators. To convert [Media] or [Album] to [MediaGridItem], use
 * [toMediaGridItemFromMedia] or [toMediaGridItemFromAlbum] respectively. The
 * [insertMonthSeparators] extension can add month-based separators.
 *
 * This overload simplifies usage by providing a no-op long press handler to the default item
 * factory and introduces drag-to-select and pinch-to-zoom capabilities.
 *
 * @param items The [LazyPagingItems] collected for display. See [collectAsLazyPagingItems].
 * @param focusItem Optional [MediaGridItem] to request focus when the grid is drawn.
 * @param selection Set of currently selected [Media] items, used for highlighting.
 * @param onItemClick Callback invoked when a grid item is clicked.
 * @param dragSelectionEnabled Whether drag-to-select functionality is enabled. Defaults to false.
 * @param dragSelectState State for managing drag selection. Defaults to a remembered
 *   [GridDragSelectState].
 * @param dragSelectIndexOffset Offset for indices reported to drag selection, useful if the grid is
 *   part of a larger list. Defaults to 0.
 * @param selectionTransform Function to transform a [Media] item during selection. Defaults to an
 *   identity function.
 * @param pinchToZoomEnabled Whether pinch-to-zoom functionality for changing column count is
 *   enabled. Defaults to false.
 * @param pinchToZoomMaxColumns Maximum number of columns achievable via pinch-to-zoom. Defaults
 *   to 5.
 * @param pinchToZoomMinColumns Minimum number of columns achievable via pinch-to-zoom. Defaults
 *   to 2.
 * @param onZoomAtMaxZoom Callback invoked when a pinch-zoom gesture attempts to zoom in further
 *   while already at the `pinchToZoomMinColumns` (maximum zoom level). The [MediaGridItem] under
 *   the gesture's focal point is provided. Defaults to no-op.
 * @param isExpandedScreen Whether the device uses an expanded screen size, affecting default column
 *   count if `initialColumns` isn't set.
 * @param initialColumns Initial number of cells per row. Defaults based on [isExpandedScreen].
 * @param gridCellPadding Padding between grid cells. Defaults to [MEASUREMENT_CELL_SPACING].
 * @param modifier A [Modifier] for the top-level [LazyVerticalGrid].
 * @param contentPadding [PaddingValues] for the [LazyVerticalGrid]. Defaults to bottom padding.
 * @param userScrollEnabled Whether user scrolling is enabled. Defaults to true.
 * @param spanFactory Optional factory for item [GridItemSpan]. Defaults to [defaultBuildSpan].
 * @param contentTypeFactory Optional factory for item content type. Defaults to
 *   [MediaGridItem.Companion.defaultBuildContentType].
 * @param contentItemFactory Optional factory for [MediaGridItem] composition. Receives item,
 *   selection state, click handler, a no-op long-press handler for this overload, and date format.
 *   Defaults to a factory providing default item rendering.
 * @param contentSeparatorFactory Optional factory for [MediaGridItem.SeparatorItem] composition.
 *   Defaults to [defaultBuildSeparator].
 * @param contentPlaceholderFactory Optional factory to compose placeholders . Defaults to
 *   [defaultBuildPlaceholder]
 * @param bannerContent Optional composable banner content at the top of the grid.
 * @param highlightMediaContent Optional custom implementation for highlight media content to be
 *   displayed at the top of the photogrid
 * @Param arePlaceholdersEnabled Whether placeholders are enabled in the grid. Defaults to false.
 *   When enabled, every item that has not yet been loaded in the grid will display a placeholder.
 *   This placeholder occupies the same width, height, and aspect ratio as the actual item
 *   ([MediaGridItem.MediaItem]) it represents. Until the item is loaded, the grid will receive a
 *   null item in its place.
 */
@Composable
fun mediaGrid(
    items: LazyPagingItems<MediaGridItem>,
    focusItem: MediaGridItem? = null,
    selection: Set<Media>,
    onItemClick: (item: MediaGridItem) -> Unit,
    dragSelectionEnabled: Boolean = false,
    dragSelectState: GridDragSelectState = rememberGridDragSelectState(),
    dragSelectIndexOffset: Int = 0,
    selectionTransform: (Media) -> Media = { it },
    pinchToZoomEnabled: Boolean = false,
    pinchToZoomMaxColumns: Int = 5,
    pinchToZoomMinColumns: Int = 2,
    onZoomAtMaxZoom: (MediaGridItem) -> Unit = {},
    isExpandedScreen: Boolean = false,
    initialColumns: Int = getCellsPerRow(isExpandedScreen),
    gridCellPadding: Dp = MEASUREMENT_CELL_SPACING,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = MEASUREMENT_DEFAULT_CONTENT_PADDING),
    userScrollEnabled: Boolean = true,
    arePlaceholdersEnabled: Boolean = false,
    spanFactory: (item: MediaGridItem?, currentColumns: Int) -> GridItemSpan = ::defaultBuildSpan,
    contentTypeFactory: (item: MediaGridItem?) -> Int = ::defaultBuildContentType,
    contentItemFactory:
        @Composable
        (
            item: MediaGridItem,
            isSelected: Boolean,
            onClick: ((item: MediaGridItem) -> Unit)?,
            onLongPress: ((item: MediaGridItem) -> Unit)?,
            dateFormat: DateFormat,
        ) -> Unit =
        { item, isSelected, onClick, onLongPress, dateFormat ->
            defaultContentItemFactory(
                item = item,
                isSelected = isSelected,
                onClick = onClick,
                onLongPress = {}, // Explicitly no-op for this overload
                dragSelectionEnabled = dragSelectionEnabled,
                dateFormat = dateFormat,
                focusItem = focusItem,
                selection = selection,
            )
        },
    contentSeparatorFactory: @Composable (item: MediaGridItem.SeparatorItem) -> Unit = { item ->
        defaultBuildSeparator(item)
    },
    contentPlaceholderFactory: @Composable () -> Unit = { defaultBuildPlaceholder() },
    bannerContent: (@Composable () -> Unit)? = null,
    highlightMediaContent: (@Composable () -> Unit)? = null,
) {
    mediaGrid(
        items = items,
        focusItem = focusItem,
        selection = selection,
        onItemClick = onItemClick,
        onItemLongPress = {}, // This overload doesn't handle long press; passes no-op
        dragSelectionEnabled = dragSelectionEnabled,
        dragSelectState = dragSelectState,
        dragSelectIndexOffset = dragSelectIndexOffset,
        selectionTransform = selectionTransform,
        pinchToZoomEnabled = pinchToZoomEnabled,
        pinchToZoomMaxColumns = pinchToZoomMaxColumns,
        pinchToZoomMinColumns = pinchToZoomMinColumns,
        onZoomAtMaxZoom = onZoomAtMaxZoom,
        isExpandedScreen = isExpandedScreen,
        initialColumns = initialColumns,
        gridCellPadding = gridCellPadding,
        modifier = modifier,
        state = dragSelectState.gridState,
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
        arePlaceholdersEnabled = arePlaceholdersEnabled,
        spanFactory = spanFactory,
        contentTypeFactory = contentTypeFactory,
        contentItemFactory = contentItemFactory,
        contentSeparatorFactory = contentSeparatorFactory,
        contentPlaceholderFactory = contentPlaceholderFactory,
        bannerContent = bannerContent,
        highlightMediaContent = highlightMediaContent,
    )
}

/**
 * Core composable implementation for creating a MediaItemGrid from a [LazyPagingItems] source.
 *
 * This function underpins the public `mediaGrid` composables, providing the main layout and
 * interaction logic. It handles item rendering, selection, drag-to-select, pinch-to-zoom, and other
 * grid behaviors.
 *
 * @param items The [LazyPagingItems] representing the data to display.
 * @param focusItem Optional [MediaGridItem] that should request focus.
 * @param selection The current set of selected [Media] items.
 * @param dragSelectionEnabled Whether drag-to-select functionality is enabled.
 * @param dragSelectState The state object for managing drag-to-select behavior. Required if
 *   [dragSelectionEnabled] is true.
 * @param dragSelectIndexOffset An offset applied to indices reported by [dragSelectState].
 * @param pinchToZoomEnabled Whether pinch-to-zoom functionality for changing column count is
 *   enabled.
 * @param pinchToZoomMaxColumns Maximum number of columns achievable via pinch-to-zoom.
 * @param pinchToZoomMinColumns Minimum number of columns achievable via pinch-to-zoom.
 * @param onZoomAtMaxZoom Callback for zoom attempts beyond maximum zoom.
 * @param selectionTransform A function to transform a [Media] item during selection.
 * @param onItemClick Callback triggered when a grid item is clicked.
 * @param onItemLongPress Callback triggered when a grid item is long-pressed.
 * @param isExpandedScreen Whether the device is using an expanded screen size.
 * @param initialColumns Initial number of cells per row.
 * @param gridCellPadding Padding between grid cells.
 * @param modifier A [Modifier] to apply to the [LazyVerticalGrid].
 * @param contentPadding [PaddingValues] for the [LazyVerticalGrid].
 * @param userScrollEnabled Whether the user can scroll the grid.
 * @param spanFactory Factory to determine [GridItemSpan] for items.
 * @param contentTypeFactory Factory to determine content type for items.
 * @param contentItemFactory Factory to compose individual [MediaGridItem]s.
 * @param contentSeparatorFactory Factory to compose [MediaGridItem.SeparatorItem]s.
 * @param contentPlaceholderFactory Factory to compose placeholders.
 * @param bannerContent Optional composable banner content.
 * @param highlightMediaContent Optional custom implementation for highlight media content to be
 *   displayed at the top of the photogrid
 * @param state The [LazyGridState] to use with the [LazyVerticalGrid].
 * @Param arePlaceholdersEnabled Whether placeholders are enabled in the grid.
 */
@Composable
private fun mediaGrid(
    items: LazyPagingItems<MediaGridItem>,
    focusItem: MediaGridItem? = null,
    selection: Set<Media>,
    dragSelectionEnabled: Boolean = false,
    dragSelectState: GridDragSelectState? = null,
    dragSelectIndexOffset: Int = 0,
    pinchToZoomEnabled: Boolean = false,
    pinchToZoomMaxColumns: Int = 5,
    pinchToZoomMinColumns: Int = 2,
    onZoomAtMaxZoom: (MediaGridItem) -> Unit = {},
    selectionTransform: (Media) -> Media = { it },
    onItemClick: (item: MediaGridItem) -> Unit,
    onItemLongPress: (item: MediaGridItem) -> Unit = {},
    isExpandedScreen: Boolean = false,
    initialColumns: Int = getCellsPerRow(isExpandedScreen),
    gridCellPadding: Dp = MEASUREMENT_CELL_SPACING,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = MEASUREMENT_DEFAULT_CONTENT_PADDING),
    userScrollEnabled: Boolean = true,
    arePlaceholdersEnabled: Boolean = false,
    spanFactory: (item: MediaGridItem?, currentColumns: Int) -> GridItemSpan,
    contentTypeFactory: (item: MediaGridItem?) -> Int,
    contentItemFactory:
        @Composable
        (
            item: MediaGridItem,
            isSelected: Boolean,
            onClick: ((item: MediaGridItem) -> Unit)?,
            onLongPress: ((item: MediaGridItem) -> Unit)?,
            dateFormat: DateFormat,
        ) -> Unit,
    contentSeparatorFactory: @Composable (item: MediaGridItem.SeparatorItem) -> Unit,
    contentPlaceholderFactory: @Composable () -> Unit,
    bannerContent: (@Composable () -> Unit)? = null,
    highlightMediaContent: (@Composable () -> Unit)? = null,
    state: LazyGridState,
) {
    // To know whether the request in coming from Embedded or PhotoPicker
    val isEmbedded =
        LocalPhotopickerConfiguration.current.runtimeEnv == PhotopickerRuntimeEnv.EMBEDDED
    val host = LocalEmbeddedState.current?.host
    val dateFormat =
        LocalLocalizationHelper.current.getLocalizedDateTimeFormatter(
            dateStyle = DateFormat.MEDIUM,
            timeStyle = DateFormat.SHORT,
        )

    var zoom by rememberSaveable(initialColumns) { mutableStateOf(1f) }
    var currentColumns by rememberSaveable(initialColumns) { mutableStateOf(initialColumns) }
    var zoomCanPreview by remember { mutableStateOf(currentColumns == pinchToZoomMinColumns) }

    val minZoomFactor =
        remember(initialColumns) { initialColumns / pinchToZoomMaxColumns.toFloat() }
    val maxZoomFactor =
        remember(initialColumns) { initialColumns / pinchToZoomMinColumns.toFloat() }

    val pinchToZoomHandler: (PinchToZoomEvent) -> Boolean = pinchToZoomHandler@{ event ->
        when (event) {
            is PinchToZoomEvent.Started -> {
                // Only allow "zooming to preview" for this gesture if the zoom level is already at
                // the minimum zoom factor. This prevents a disruptive experience where zooming in
                // too much navigates you away from the current screen, the user can change the zoom
                // level in the first gesture, and then zoom to preview in a second gesture.
                zoomCanPreview = currentColumns == pinchToZoomMinColumns

                // When a Pinch gesture begins, ensure that it is not part of the header elements.
                // If it is, then return true here to cancel the gesture immediately.
                val headerElementCount =
                    listOf(bannerContent, highlightMediaContent).count { it != null }
                val pinchIndex =
                    state.itemIndexAtPosition(event.offset)?.minus(headerElementCount) ?: 0
                return@pinchToZoomHandler !(pinchIndex >= 0)
            }
            is PinchToZoomEvent.Changed -> {
                zoom = (zoom * event.value).coerceIn(minZoomFactor, maxZoomFactor)
                currentColumns =
                    (initialColumns / zoom)
                        .roundToInt()
                        .coerceIn(pinchToZoomMinColumns, pinchToZoomMaxColumns)
                if (event.value < 1f) {
                    zoomCanPreview = false
                }
                if (zoomCanPreview && event.value > 1f) {
                    // positive zoom
                    if (currentColumns == pinchToZoomMinColumns) {
                        state
                            .itemIndexAtPosition(event.offset)
                            ?.minus(dragSelectIndexOffset)
                            ?.let { index ->
                                val item =
                                    try {
                                        items.peek(index)
                                    } catch (e: Exception) {
                                        // Prevent crashes when the item cannot
                                        // be accessed at the requested index,
                                        // and instead do nothing.
                                        Log.w("Could not find item to preview at index: $index", e)
                                        null
                                    }
                                item?.let {
                                    onZoomAtMaxZoom(it)
                                    return@pinchToZoomHandler true
                                }
                            }
                    }
                }
            }
            is PinchToZoomEvent.Ended -> {}
        }
        return@pinchToZoomHandler false
    }

    /**
     * Bottom sheet current state in runtime Embedded Photopicker. This assignment is necessary to
     * get the regular updates of bottom sheet current state inside [LazyVerticalGrid]
     */
    val isExpanded = rememberUpdatedState(LocalEmbeddedState.current?.isExpanded ?: false)

    // PinchToZoom is handled above the Grid so that it can intercept touch events above the
    // LazyVerticalGrid in the "initial" touch event handling pass so that when handling pinch
    // gestures the pinch gestures themselves don't also scroll the grid.
    val boxModifier: Modifier = Modifier
    Box(
        modifier =
            boxModifier.applyWhen(
                pinchToZoomEnabled,
                { pinchToZoom(PointerEventPass.Initial, pinchToZoomHandler) },
            )
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(currentColumns),
            modifier =
                // Modifier order here matters greatly. Since both of these modifiers
                // register pointerInput handlers, ensure that embedded transfer
                // gestures are evaluated first, before evaluating any drag-to-select
                // input.
                modifier
                    .applyWhen(
                        SdkLevel.isAtLeastU() && isEmbedded && host != null,
                        {
                            // This can be safely suppressed as the condition includes the SdkLevel
                            // check, but the Linter doesn't understand the precondition to this
                            // block being run.
                            @SuppressLint("NewApi")
                            transferScrollableTouchesToHostInEmbedded(
                                state,
                                isExpanded,
                                checkNotNull(host) { "surfaceHost cannot be null" },
                            )
                        },
                    )
                    .applyWhen(
                        dragSelectionEnabled,
                        {
                            onGridDragSelect(
                                config = LocalPhotopickerConfiguration.current,
                                items = items,
                                state =
                                    checkNotNull(dragSelectState) {
                                        "GridDragSelectState cannot be null"
                                    },
                                windowRect = if (isEmbedded) null else calculateWindowRect(),
                                indexOffset = dragSelectIndexOffset,
                                autoScrollThreshold = GridDragSelectDefaults.autoScrollThreshold,
                                hapticFeedback = GridDragSelectDefaults.hapticsFeedback,
                                selectionTransform = selectionTransform,
                            )
                        },
                    ),
            state = state,
            contentPadding = contentPadding,
            userScrollEnabled = userScrollEnabled,
            horizontalArrangement = Arrangement.spacedBy(gridCellPadding),
            verticalArrangement = Arrangement.spacedBy(gridCellPadding),
        ) {

            // If banner content was passed add it to the grid as a full span item
            // so that it appears inside the scroll container.
            bannerContent?.let { item(span = { GridItemSpan(currentColumns) }) { it() } }
            // If highlight content was passed, add it to the grid as a full span item
            // so that it appears inside the scroll container.
            highlightMediaContent?.let { item(span = { GridItemSpan(currentColumns) }) { it() } }

            // Add the media items from the LazyPagingItems
            items(
                count = items.itemCount,
                key = { index -> MediaGridItem.keyFactory(items.peek(index), index) },
                span = { index -> spanFactory(items.peek(index), currentColumns) },
                contentType = { index -> contentTypeFactory(items.peek(index)) },
            ) { index ->
                val item: MediaGridItem? = items.get(index)
                when (item) {
                    is MediaGridItem.MediaItem ->
                        contentItemFactory(
                            item,
                            selection.contains(item.media),
                            onItemClick,
                            onItemLongPress,
                            dateFormat,
                        )

                    is MediaGridItem.AlbumItem,
                    is MediaGridItem.CategoryItem,
                    is MediaGridItem.MediaSetItem,
                    is MediaGridItem.PersonMediaSetItem ->
                        contentItemFactory(
                            item,
                            /* isSelected */ false,
                            onItemClick,
                            onItemLongPress,
                            dateFormat,
                        )
                    is MediaGridItem.SeparatorItem -> contentSeparatorFactory(item)
                    null -> {
                        if (arePlaceholdersEnabled) {
                            contentPlaceholderFactory()
                        }
                    }
                }
            }
        }
        if (isEmbedded) {
            // Remember the previous value of isExpanded
            val wasPreviouslyExpanded = remember { mutableStateOf(!isExpanded.value) }

            // Any time isExpanded changes, check if grid animation is required.
            LaunchedEffect(isExpanded.value) {
                val isCollapsed = !isExpanded.value

                // Only animate if going from Expanded -> Collapsed
                if (wasPreviouslyExpanded.value && isCollapsed) {
                    if (state.firstVisibleItemScrollOffset > 0) {
                        state.animateScrollBy(
                            value = -state.firstVisibleItemScrollOffset.toFloat(),
                            animationSpec = tween(durationMillis = 500),
                        )
                    }
                }
                // Update the previous state as the current state
                wasPreviouslyExpanded.value = isExpanded.value
            }
        }
    }
}

@Composable
private fun defaultContentItemFactory(
    item: MediaGridItem,
    isSelected: Boolean,
    onClick: ((item: MediaGridItem) -> Unit)?,
    onLongPress: ((item: MediaGridItem) -> Unit)?,
    dragSelectionEnabled: Boolean = false,
    dateFormat: DateFormat,
    focusItem: MediaGridItem? = null,
    selection: Set<Media>,
) {
    when (item) {
        is MediaGridItem.MediaItem ->
            defaultBuildMediaItem(
                item = item,
                isSelected = isSelected,
                selectedPosition = selection.indexOf(item.media),
                onClick = onClick,
                onLongPress = onLongPress,
                dragSelectionEnabled = dragSelectionEnabled,
                dateFormat = dateFormat,
                focusItem = focusItem,
            )

        is MediaGridItem.AlbumItem -> defaultBuildAlbumItem(item, onClick, focusItem)
        is MediaGridItem.CategoryItem -> defaultBuildCategoryItem(item, onClick, focusItem)
        is MediaGridItem.PersonMediaSetItem -> defaultBuildPersonMediaSetItem(item, onClick)
        else -> {}
    }
}

/** Default builder for calculating the [GridItemSpan] of the provided [MediaGridItem]. */
private fun defaultBuildSpan(item: MediaGridItem?, currentColumns: Int): GridItemSpan {
    return when (item) {
        is MediaGridItem.MediaItem,
        null ->
            GridItemSpan(1) // Placeholder should take up the same number of columns as a media item
        is MediaGridItem.SeparatorItem -> GridItemSpan(currentColumns)
        is MediaGridItem.AlbumItem -> GridItemSpan(1)
        else -> GridItemSpan(1)
    }
}

/**
 * Return the number of cells in a row based on whether the current configuration has expanded
 * screen or not.
 */
public fun getCellsPerRow(isExpandedScreen: Boolean): Int {
    return if (isExpandedScreen) CELLS_PER_ROW_EXPANDED else CELLS_PER_ROW
}

/** Default Placeholder builder that loads placeholder into a square (1:1) aspect ratio GridCell */
@Composable
private fun defaultBuildPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .aspectRatio(
                    1f
                ) // Ensure it maintains a 1:1 aspect ratio, like [MediaGridItem.MediaItem]
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {}
}

/**
 * Default [MediaGridItem.MediaItem] builder that loads media into a square (1:1) aspect ratio
 * GridCell, and provides animations and an icon for the selected state.
 */
@Composable
fun defaultBuildMediaItem(
    item: MediaGridItem,
    isHighlightMediaItem: Boolean = false,
    isSelected: Boolean,
    selectedPosition: Int,
    onClick: ((item: MediaGridItem) -> Unit)?,
    onLongPress: ((item: MediaGridItem) -> Unit)?,
    dragSelectionEnabled: Boolean = false,
    dateFormat: DateFormat,
    focusItem: MediaGridItem?,
) {
    when (item) {
        is MediaGridItem.MediaItem -> {
            // Padding is animated based on the selected state of the item. When the item is
            // selected, it should shrink in the cell and provide a surface background.

            val padding by
                animateDpAsState(
                    if (isSelected) {
                        MEASUREMENT_SELECTED_INTERNAL_PADDING
                    } else {
                        MEASUREMENT_NOT_SELECTED_INTERNAL_PADDING
                    }
                )

            // Modifier for the image itself, which uses the animated padding defined above.
            var baseModifier = Modifier.fillMaxSize().padding(padding)

            // If the caller has specified an item to receive focus,
            // apply the focus requester modifier to it.
            if (focusItem != null) {
                val focusRequester = remember { FocusRequester() }
                baseModifier = baseModifier.focusRequester(focusRequester).focusable(true)
                LaunchedEffect(Unit) {
                    if (item == focusItem) {
                        delay(150)
                        focusRequester.requestFocus()
                    }
                }
            }

            // Additionally, selected items get rounded corners, so that is added to the
            // baseModifier
            val selectedModifier =
                baseModifier.applyChoice(
                    condition = isHighlightMediaItem,
                    trueBlock = {
                        width(MEASUREMENT_HIGHLIGHT_GRID_SELECTED_CELL_WIDTH)
                            .height(MEASUREMENT_HIGHLIGHT_GRID_SELECTED_CELL_HEIGHT)
                            .clip(RoundedCornerShape(MEASUREMENT_HIGHLIGHT_GRID_CELLS_RADIUS))
                    },
                    falseBlock = { clip(RoundedCornerShape(MEASUREMENT_SELECTED_CORNER_RADIUS)) },
                )

            val mediaDescription = getMediaContentDescription(item.media, dateFormat)

            // Wrap the entire Grid cell in a box for handling aspectRatio and clicks.
            Box(
                // Apply semantics for the click handlers
                Modifier.semantics(mergeDescendants = true) {
                        contentDescription = mediaDescription
                        onClick(
                            action = {
                                onClick?.invoke(item)
                                /* eventHandled= */ true
                            }
                        )
                        if (!dragSelectionEnabled) {
                            onLongClick(
                                action = {
                                    onLongPress?.invoke(item)
                                    /* eventHandled= */ true
                                }
                            )
                        }
                    }
                    .applyChoice(
                        condition = isHighlightMediaItem,
                        trueBlock = {
                            width(MEASUREMENT_HIGHLIGHT_GRID_UNSELECTED_CELL_WIDTH)
                                .height(MEASUREMENT_HIGHLIGHT_GRID_UNSELECTED_CELL_HEIGHT)
                        },
                        falseBlock = { aspectRatio(1f).fillMaxSize() },
                    )
                    .pointerInput(Unit) {
                        if (dragSelectionEnabled) {
                            detectTapGestures(onTap = { onClick?.invoke(item) })
                        } else {
                            detectTapGestures(
                                onTap = { onClick?.invoke(item) },
                                onLongPress = { onLongPress?.invoke(item) },
                            )
                        }
                    }
            ) {
                // A background surface that is shown behind selected images.
                Surface(
                    modifier =
                        Modifier.fillMaxSize()
                            .applyWhen(
                                condition = isHighlightMediaItem,
                                block = {
                                    clip(
                                        RoundedCornerShape(MEASUREMENT_HIGHLIGHT_GRID_CELLS_RADIUS)
                                    )
                                },
                            ),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    val boxModifier: Modifier = Modifier
                    // Container for the image and it's mimetype icon
                    Box(
                        // Switch which modifier is getting applied based on if the item is
                        // selected or not.
                        modifier =
                            boxModifier.applyChoice(
                                condition = isSelected,
                                trueBlock = { selectedModifier },
                                falseBlock = {
                                    applyChoice(
                                        condition = isHighlightMediaItem,
                                        trueBlock = {
                                            Modifier.clip(
                                                RoundedCornerShape(
                                                    MEASUREMENT_HIGHLIGHT_GRID_CELLS_RADIUS
                                                )
                                            )
                                        },
                                        falseBlock = { baseModifier },
                                    )
                                },
                            )
                    ) {

                        // Load the media item through the Glide entrypoint.
                        loadMedia(
                            media = item.media,
                            resolution = Resolution.THUMBNAIL,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Scrim to separate the text and mimetypes from the image behind them.
                        val scrimGradient =
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.1f), Color.Transparent)
                            )

                        Surface(
                            modifier = Modifier.background(scrimGradient),
                            color = Color.Transparent,
                            contentColor = Color.White,
                        ) {
                            MimeTypeOverlay(item)
                        }
                    }

                    // This is outside the box that wraps the image so it doesn't get clipped
                    // by the shape. Internally, it positions itself with similar padding.
                    SelectedIconOverlay(isSelected, selectedPosition, isHighlightMediaItem)
                } // Surface
            } // Grid cell box
        } // when MediaItem branch
        else -> {}
    } // when
}

/**
 * Generates a mimetype overlay for media items, if the mimetype is supported.
 *
 * @param item The MediaGridItem.MediaItem for the current grid cell.
 */
@Composable
private fun MimeTypeOverlay(item: MediaGridItem.MediaItem) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            Modifier.align(AbsoluteAlignment.TopRight)
                .padding(MEASUREMENT_MIMETYPE_ICON_EDGE_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.media is Media.Video) {
                Text(
                    text = DateUtils.formatElapsedTime(item.media.duration / 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.size(MEASUREMENT_DURATION_TEXT_SPACER_SIZE))
                Icon(Icons.Filled.PlayCircle, contentDescription = null)
            } else {
                when (item.media.standardMimeTypeExtension) {
                    _SPECIAL_FORMAT_GIF -> {
                        Icon(Icons.Filled.Gif, contentDescription = null)
                    }

                    _SPECIAL_FORMAT_MOTION_PHOTO,
                    _SPECIAL_FORMAT_ANIMATED_WEBP -> {
                        Icon(Icons.Filled.MotionPhotosOn, contentDescription = null)
                    }

                    else -> {}
                }
            }
        }
    }
}

/**
 * Generates a Icon that will show and hide itself based on the [isSelected] property.
 *
 * @param isSelected if the current item is currently selected by the user.
 * @param selectedIndex the index of the item in the selection set.
 */
@Composable
private fun SelectedIconOverlay(
    isSelected: Boolean,
    selectedIndex: Int,
    isHighlightMediaItem: Boolean = false,
) {

    Box(modifier = Modifier.fillMaxSize().padding(MEASUREMENT_SELECTED_INTERNAL_PADDING)) {
        // Animate the visibility of the selected icon based on the [isSelected]
        // attribute.
        AnimatedVisibility(
            modifier =
                Modifier.align(AbsoluteAlignment.TopLeft)
                    // This offset moves the icon in each axis from the corner
                    // origin. (So that the center of the icon is closer to the
                    // actual visual corner). The offset is applied to the animation
                    // wrapper so the animation origin moves with the icon itself.
                    .applyChoice(
                        condition = isHighlightMediaItem,
                        trueBlock = {
                            offset(
                                x = -MEASUREMENT_SELECTED_ICON_HIGHLIGHT_ITEM_OFFSET,
                                y = -MEASUREMENT_SELECTED_ICON_HIGHLIGHT_ITEM_OFFSET,
                            )
                        },
                        falseBlock = {
                            offset(
                                x = -MEASUREMENT_SELECTED_ICON_OFFSET,
                                y = -MEASUREMENT_SELECTED_ICON_OFFSET,
                            )
                        },
                    ),
            visible = isSelected,
            enter = scaleIn(animationSpec = springDefaultEffectFloat),
            exit = scaleOut(animationSpec = emphasizedAccelerateFloat),
        ) {
            val configuration = LocalPhotopickerConfiguration.current
            when (configuration.pickImagesInOrder) {
                true -> {
                    val numberFormatter = remember { NumberFormat.getInstance() }
                    var rememberedIndex by remember { mutableStateOf(selectedIndex) }

                    LaunchedEffect(isSelected, selectedIndex) {
                        if (isSelected) {
                            rememberedIndex = selectedIndex
                        }
                    }
                    Text(
                        // Since this is a 0-based index, increment it by 1 for displaying
                        // to the user.
                        text = numberFormatter.format(rememberedIndex + 1),
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier.circleBackground(
                                color =
                                    CustomAccentColorScheme.current.getAccentColorIfDefinedOrElse(
                                        /* fallback */ MaterialTheme.colorScheme.primary
                                    ),
                                padding = 1.dp,
                                borderColor = MaterialTheme.colorScheme.surfaceVariant,
                                borderWidth = MEASUREMENT_SELECTED_ICON_BORDER,
                            ),
                        style =
                            LocalTextStyle.current.copy(
                                fontSize = MEASUREMENT_SELECTED_POSITION_FONT_SIZE
                            ),
                        color =
                            CustomAccentColorScheme.current
                                .getTextColorForAccentComponentsIfDefinedOrElse(
                                    MaterialTheme.colorScheme.onPrimary
                                ),
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                false ->
                    Icon(
                        ImageVector.vectorResource(R.drawable.photopicker_selected_media),
                        modifier =
                            Modifier
                                // Background is necessary because the icon has negative
                                // space.
                                .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                                // Border color should match the surface that is behind
                                // the image.
                                .border(
                                    MEASUREMENT_SELECTED_ICON_BORDER,
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    CircleShape,
                                ),
                        contentDescription = stringResource(R.string.photopicker_item_selected),
                        tint =
                            CustomAccentColorScheme.current.getAccentColorIfDefinedOrElse(
                                /* fallback */ MaterialTheme.colorScheme.primary
                            ),
                    )
            }
        } // Image + Icon Container
    }
}

/**
 * Default [MediaGridItem.AlbumItem] builder that loads album into a square (1:1) aspect ratio
 * GridCell, and provides a text title for it just below the thumbnail.
 */
@Composable
private fun defaultBuildAlbumItem(
    item: MediaGridItem,
    onClick: ((item: MediaGridItem) -> Unit)?,
    focusItem: MediaGridItem? = null,
) {
    when (item) {
        is MediaGridItem.AlbumItem -> {
            // Apply semantics for the click handlers
            var baseModifier =
                Modifier.semantics(mergeDescendants = true) {
                        onClick(
                            action = {
                                onClick?.invoke(item)
                                /* eventHandled= */ true
                            }
                        )
                    }
                    .pointerInput(Unit) { detectTapGestures(onTap = { onClick?.invoke(item) }) }
                    .padding(bottom = MEASUREMENT_DEFAULT_ALBUM_BOTTOM_PADDING)

            // If the caller has specified an item to receive focus,
            // apply the focus requester modifier to it.
            if (focusItem != null) {
                val focusRequester = remember { FocusRequester() }
                baseModifier = baseModifier.focusRequester(focusRequester).focusable(true)
                LaunchedEffect(Unit) {
                    if (item == focusItem) {
                        delay(150)
                        focusRequester.requestFocus()
                    }
                }
            }

            Column(modifier = baseModifier) {
                // In the current implementation for AlbumsGrid, favourites and videos are
                // 2 mandatory albums and are shown even when they contain no data. For this
                // case they have special thumbnails associated with them.
                with(item.album) {
                    val modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(MEASUREMENT_SELECTED_CORNER_RADIUS_FOR_ALBUMS))
                            .aspectRatio(1f)
                    when {
                        id.equals(ALBUM_ID_FAVORITES) && coverUri.equals(Uri.EMPTY) -> {
                            DefaultAlbumIcon(/* icon */ Icons.Outlined.StarOutline, modifier)
                        }

                        id.equals(ALBUM_ID_VIDEOS) && coverUri.equals(Uri.EMPTY) -> {
                            DefaultAlbumIcon(/* icon */ Icons.Outlined.Videocam, modifier)
                        }

                        id.equals(ALBUM_ID_CAMERA) && coverUri.equals(Uri.EMPTY) -> {
                            DefaultAlbumIcon(/* icon */ Icons.Outlined.PhotoCamera, modifier)
                        }
                        // Load the media item through the Glide entrypoint.
                        else -> {
                            loadMedia(
                                media = item.album,
                                resolution = Resolution.THUMBNAIL,
                                // Modifier for album thumbnail
                                modifier = modifier,
                            )
                        }
                    }
                }

                Spacer(Modifier.size(MEASUREMENT_DEFAULT_ALBUM_LABEL_SPACER_SIZE))
                // Album title shown below the album thumbnail.
                Text(
                    text = item.album.displayName,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } // Album cell column
        }
        else -> {}
    }
}

/** Default [MediaGridItem.PersonMediaSetItem] builder that loads People and pets mediaset. */
@Composable
fun defaultBuildPersonMediaSetItem(
    item: MediaGridItem.PersonMediaSetItem,
    onClick: ((item: MediaGridItem) -> Unit)?,
) {
    Box(
        // Apply semantics for the click handlers
        Modifier.semantics(mergeDescendants = true) {
                contentDescription = item.mediaSet.displayName ?: ""
                onClick(
                    action = {
                        onClick?.invoke(item)
                        /* eventHandled= */ true
                    }
                )
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick?.invoke(item) }) }
    ) {
        with(item.mediaSet) {
            val modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            loadMedia(media = icon, resolution = Resolution.THUMBNAIL, modifier = modifier)
            Surface(color = Color.Black.copy(alpha = 0.2f), contentColor = Color.White) {
                Box(modifier = modifier) {
                    Text(
                        text = displayName ?: "",
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Default [MediaGridItem.CategoryItem] builder that loads category into a square (1:1) aspect ratio
 * GridCell with icons in square grid and provides a text title below it.
 */
@Composable
private fun defaultBuildCategoryItem(
    item: MediaGridItem.CategoryItem,
    onClick: ((item: MediaGridItem) -> Unit)?,
    focusItem: MediaGridItem?,
) {
    // Apply semantics for the click handlers
    var baseModifier =
        Modifier.semantics(mergeDescendants = true) {
                contentDescription = item.category.displayName ?: ""
                onClick(
                    action = {
                        onClick?.invoke(item)
                        /* eventHandled */ true
                    }
                )
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick?.invoke(item) }) }
            .padding(bottom = MEASUREMENT_DEFAULT_ALBUM_BOTTOM_PADDING)

    // If the caller has specified an item to receive focus,
    // apply the focus requester modifier to it.
    if (focusItem != null) {
        val focusRequester = remember { FocusRequester() }
        baseModifier = baseModifier.focusRequester(focusRequester).focusable(true)
        LaunchedEffect(Unit) {
            if (item == focusItem) {
                delay(150)
                focusRequester.requestFocus()
            }
        }
    }

    Column(modifier = baseModifier) {
        with(item.category) {
            val modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(MEASUREMENT_SELECTED_CORNER_RADIUS_FOR_ALBUMS))
                    .aspectRatio(1f)
            val badgeIconModifier = Modifier.padding(MEASUREMENT_BADGE_ICON_OFFSET)
            IconGrid(icons, modifier = modifier, categoryType, badge, badgeIconModifier)
            Spacer(Modifier.size(MEASUREMENT_DEFAULT_ALBUM_LABEL_SPACER_SIZE))
            // Category title shown below the category grid.
            Text(
                text = displayName ?: "",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Default [MediaGridItem.SeparatorItem] that creates a full width divider using the provided text
 * label.
 */
@Composable
fun defaultBuildSeparator(item: MediaGridItem.SeparatorItem) {
    Box(Modifier.padding(MEASUREMENT_SEPARATOR_PADDING).semantics(mergeDescendants = true) {}) {
        Text(item.label, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Creates an image which can be used as a default thumbnail, this image is creates using the
 * provided [ImageVector].
 *
 * These image vectors a part of androidx androidx.compose.material.icons library.
 */
@Composable
private fun DefaultAlbumIcon(icon: ImageVector, modifier: Modifier) {

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(MEASUREMENT_SELECTED_CORNER_RADIUS_FOR_ALBUMS),
    ) {
        Box(
            // Modifier for album thumbnail
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Or provide a suitable content description
                modifier =
                    Modifier
                        // Equivalent to layout_width and layout_height
                        .size(MEASUREMENT_DEFAULT_ALBUM_THUMBNAIL_ICON_SIZE)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer, // Background color
                            shape = CircleShape, // Circular background
                        )
                        // Padding inside the circle
                        .padding(MEASUREMENT_DEFAULT_ALBUM_THUMBNAIL_ICON_PADDING)
                        .clip(CircleShape), // Clip the image to a circle
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
