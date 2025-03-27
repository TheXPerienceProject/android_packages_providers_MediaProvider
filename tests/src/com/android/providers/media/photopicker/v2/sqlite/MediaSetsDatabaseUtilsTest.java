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

package com.android.providers.media.photopicker.v2.sqlite;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.provider.CloudMediaProviderContract;
import android.util.Pair;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.providers.media.photopicker.data.PickerDatabaseHelper;
import com.android.providers.media.photopicker.util.exceptions.RequestObsoleteException;
import com.android.providers.media.photopicker.v2.model.MediaSetsSyncRequestParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MediaSetsDatabaseUtilsTest {
    private SQLiteDatabase mDatabase;
    private Context mContext;
    private final String mMediaSetId = "mediaSetId";
    private final String mCategoryId = "categoryId";
    private final String mAuthority = "auth";
    private final String mMimeType = "img";
    private final String mDisplayName = "name";
    private final String mCoverId = "id";

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File dbPath = mContext.getDatabasePath(PickerDatabaseHelper.PICKER_DATABASE_NAME);
        dbPath.delete();
        PickerDatabaseHelper helper = new PickerDatabaseHelper(mContext);
        mDatabase = helper.getWritableDatabase();
    }

    @After
    public void teardown() {
        mDatabase.close();
        File dbPath = mContext.getDatabasePath(PickerDatabaseHelper.PICKER_DATABASE_NAME);
        dbPath.delete();
    }

    @Test
    public void testInsertMediaSetMetadataIntoMediaSetsTable() throws RequestObsoleteException {
        Cursor c = getCursorForMediaSetInsertionTest();
        List<String> mimeTypes = new ArrayList<>();
        mimeTypes.add(mMimeType);

        int mediaSetsInserted = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, mCategoryId, mAuthority, mimeTypes);
        assertEquals("Count of inserted media sets should be equal to the cursor size",
                /*expected*/ c.getCount(), /*actual*/ mediaSetsInserted);
    }

    @Test
    public void testInsertMediaSetMetadataIntoMediaTableMimeTypeFilter()
            throws RequestObsoleteException {
        Cursor c = getCursorForMediaSetInsertionTest();
        List<String> firstMimeTypeFilter = new ArrayList<>();
        firstMimeTypeFilter.add("image/*");
        firstMimeTypeFilter.add("video/*");

        int firstInsertionCount = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, mCategoryId, mAuthority, firstMimeTypeFilter
               );
        assertEquals("Count of inserted media sets should be equal to the cursor size",
                /*expected*/ c.getCount(), /*actual*/ firstInsertionCount);

        // Reversing the order of the mimeTypeFilter.
        // It should still be treated the same and should not be reinserted
        List<String> secondMimeTypeFilter = new ArrayList<>();
        secondMimeTypeFilter.add("video/*");
        secondMimeTypeFilter.add("image/*");

        int secondInsertionCount = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, mCategoryId, mAuthority, secondMimeTypeFilter);
        assertEquals("MediaSet metadata with same mimetype filters should not be inserted "
                        + "again",
                /*expected*/ 0, /*actual*/ secondInsertionCount);

    }

    @Test
    public void testInsertMediaSetMetadataWhenMediaSetIdIsNull() throws RequestObsoleteException {
        List<String> mimeTypes = new ArrayList<>();
        mimeTypes.add(mMimeType);

        String[] columns = new String[]{
                CloudMediaProviderContract.MediaSetColumns.ID,
                CloudMediaProviderContract.MediaSetColumns.DISPLAY_NAME,
                CloudMediaProviderContract.MediaSetColumns.MEDIA_COVER_ID
        };

        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(new Object[] { null, mDisplayName, mCoverId });

        int mediaSetsInserted = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, cursor, mCategoryId, mAuthority, mimeTypes);
        assertEquals("Count of inserted media sets should be 0 when the mediaSetId is null",
                /*expected*/0, /*actual*/ mediaSetsInserted);
    }

    @Test
    public void testGetMediaSetMetadataForCategory() throws RequestObsoleteException {
        Cursor c = getCursorForMediaSetInsertionTest();
        List<String> mimeTypes = new ArrayList<>();
        mimeTypes.add(mMimeType);

        long insertResult = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, mCategoryId, mAuthority, mimeTypes);
        // Assert successful insertion
        assertWithMessage("MediaSet metadata insertion failed")
                .that(insertResult)
                .isAtLeast(/* expected min row id */ 0);
        Bundle extras = new Bundle();
        extras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_AUTHORITY, mAuthority);
        extras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_ID, mCategoryId);
        extras.putStringArrayList(
                MediaSetsSyncRequestParams.KEY_MIME_TYPES,
                new ArrayList<String>(mimeTypes));
        MediaSetsSyncRequestParams requestParams = new MediaSetsSyncRequestParams(extras);

        Cursor mediaSetCursor = MediaSetsDatabaseUtil.getMediaSetsForCategory(
                mDatabase, requestParams);
        assertNotNull(mediaSetCursor);
        assertWithMessage("Cursor size should be greater than 0. Expected size: 1")
                .that(mediaSetCursor.getCount())
                .isEqualTo(1);
        if (mediaSetCursor.moveToFirst()) {
            int mediaSetIdIndex = mediaSetCursor.getColumnIndex(PickerSQLConstants
                    .MediaSetsTableColumns.MEDIA_SET_ID.getColumnName());
            String retrievedMediaSetId = mediaSetCursor.getString(mediaSetIdIndex);
            assertEquals(mMediaSetId, retrievedMediaSetId);
        }
    }

    @Test
    public void testUpdateAndGetMediaInMediaSetResumeKey() throws RequestObsoleteException {
        Cursor c = getCursorForMediaSetInsertionTest();
        List<String> mimeTypes = new ArrayList<>();
        mimeTypes.add(mMimeType);

        long mediaSetsInserted = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, mCategoryId, mAuthority, mimeTypes);
        // Assert successful insertion
        assertEquals("Count of inserted media sets should be equal to the cursor size",
                /*expected*/ c.getCount(), /*actual*/ mediaSetsInserted);
        Bundle extras = new Bundle();
        extras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_AUTHORITY, mAuthority);
        extras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_ID, mCategoryId);
        extras.putStringArrayList(
                MediaSetsSyncRequestParams.KEY_MIME_TYPES,
                new ArrayList<String>(mimeTypes));
        MediaSetsSyncRequestParams requestParams = new MediaSetsSyncRequestParams(extras);
        Cursor fetchMediaSetCursor = MediaSetsDatabaseUtil.getMediaSetsForCategory(
                mDatabase, requestParams);
        Long mediaSetPickerId = 1L;
        if (fetchMediaSetCursor.moveToFirst()) {
            mediaSetPickerId = fetchMediaSetCursor.getLong(
                    fetchMediaSetCursor.getColumnIndexOrThrow(
                            PickerSQLConstants.MediaSetsTableColumns.PICKER_ID.getColumnName()));
        }

        String resumeKey = "resume";
        MediaSetsDatabaseUtil.updateMediaInMediaSetSyncResumeKey(
                mDatabase, mediaSetPickerId, resumeKey);
        String retrievedMediaSetResumeKey = MediaSetsDatabaseUtil.getMediaResumeKey(
                mDatabase, mediaSetPickerId);
        assertNotNull(retrievedMediaSetResumeKey);
        assertWithMessage("Retrieved mediaSetResumeKey did not match")
                .that(retrievedMediaSetResumeKey)
                .isEqualTo(resumeKey);
    }

    @Test
    public void testGetMediaSetIdAndMimeTypesUsingMediaSetPickerId()
            throws RequestObsoleteException {
        Cursor c = getCursorForMediaSetInsertionTest();
        List<String> mimeTypes = new ArrayList<>();
        mimeTypes.add(mMimeType);

        long mediaSetsInserted = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, mCategoryId, mAuthority, mimeTypes);
        // Assert successful insertion
        assertEquals("Count of inserted media sets should be equal to the cursor size",
                /*expected*/ c.getCount(), /*actual*/ mediaSetsInserted);
        Bundle extras = new Bundle();
        extras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_AUTHORITY, mAuthority);
        extras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_ID, mCategoryId);
        extras.putStringArrayList(
                MediaSetsSyncRequestParams.KEY_MIME_TYPES,
                new ArrayList<String>(mimeTypes));
        MediaSetsSyncRequestParams requestParams = new MediaSetsSyncRequestParams(extras);
        Cursor fetchMediaSetCursor = MediaSetsDatabaseUtil.getMediaSetsForCategory(
                mDatabase, requestParams);
        Long mediaSetPickerId = 1L;
        if (fetchMediaSetCursor.moveToFirst()) {
            mediaSetPickerId = fetchMediaSetCursor.getLong(
                    fetchMediaSetCursor.getColumnIndexOrThrow(
                            PickerSQLConstants.MediaSetsTableColumns.PICKER_ID.getColumnName()));
        }

        Pair<String, String[]> retrievedData = MediaSetsDatabaseUtil
                .getMediaSetIdAndMimeType(mDatabase, mediaSetPickerId);
        assertEquals(/*expected*/retrievedData.first, /*actual*/mMediaSetId);
        assertTrue(Arrays.toString(retrievedData.second).contains(mMimeType));
    }

    @Test
    public void testGetMediaSetPickerIdsForCategoryId() {
        Cursor c = getCursorForMediaSetInsertionTest();
        List<String> mimeTypes = new ArrayList<>();
        mimeTypes.add(mMimeType);

        long mediaSetsInserted = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, mCategoryId, mAuthority, mimeTypes);
        // Assert successful insertion
        assertEquals("Count of inserted media sets should be equal to the cursor size",
                /*expected*/ c.getCount(), /*actual*/ mediaSetsInserted);

        List<String> mediaSetPickerIds = MediaSetsDatabaseUtil
                .getMediaSetPickerIdsForGivenCategoryId(mDatabase, mCategoryId, mAuthority);
        // Assert that the list has some sqlite generated ids
        assertNotNull(mediaSetPickerIds);
        assertTrue(!mediaSetPickerIds.isEmpty());
    }

    @Test
    public void testClearMediaSetsCache() {
        // Insert metadata into the table
        Cursor c = getCursorForMediaSetInsertionTest();
        List<String> mimeTypes = new ArrayList<>();
        mimeTypes.add(mMimeType);

        int mediaSetsInserted = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, mCategoryId, mAuthority, mimeTypes);
        assertEquals("Count of inserted media sets should be equal to the cursor size",
                /*expected*/ c.getCount(), /*actual*/ mediaSetsInserted);

        String secondCategoryId = "secCategoryId";
        int mediaSetsInserted2 = MediaSetsDatabaseUtil.cacheMediaSets(
                mDatabase, c, secondCategoryId, mAuthority, mimeTypes);
        assertEquals("Count of inserted media sets should be equal to the cursor size",
                /*expected*/ c.getCount(), /*actual*/ mediaSetsInserted2);


        // Delete the inserted items
        MediaSetsDatabaseUtil.clearMediaSetsCache(mDatabase, mCategoryId, mAuthority);

        // Retrieved cursor should be empty for mCategoryId
        Bundle extras = new Bundle();
        extras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_AUTHORITY, mAuthority);
        extras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_ID, mCategoryId);
        extras.putStringArrayList(
                MediaSetsSyncRequestParams.KEY_MIME_TYPES,
                new ArrayList<String>(mimeTypes));
        MediaSetsSyncRequestParams requestParams = new MediaSetsSyncRequestParams(extras);

        Cursor mediaSetCursor = MediaSetsDatabaseUtil.getMediaSetsForCategory(
                mDatabase, requestParams);
        assertNotNull(mediaSetCursor);
        assertEquals(/*expected*/ 0, /*actual*/ mediaSetCursor.getCount());

        // Retrieved cursor should not be empty for secondCategoryId since only the media sets for
        // mCategoryId have been deleted in the previous call
        Bundle secondExtras = new Bundle();
        secondExtras.putString(
                MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_AUTHORITY, mAuthority);
        secondExtras.putString(MediaSetsSyncRequestParams.KEY_PARENT_CATEGORY_ID, secondCategoryId);
        secondExtras.putStringArrayList(
                MediaSetsSyncRequestParams.KEY_MIME_TYPES,
                new ArrayList<String>(mimeTypes));
        MediaSetsSyncRequestParams secondRequestParams =
                new MediaSetsSyncRequestParams(secondExtras);

        Cursor secondMediaSetCursor = MediaSetsDatabaseUtil.getMediaSetsForCategory(
                mDatabase, secondRequestParams);
        assertNotNull(secondMediaSetCursor);
        assertEquals(/*expected*/ 1, /*actual*/ secondMediaSetCursor.getCount());
    }

    private Cursor getCursorForMediaSetInsertionTest() {
        String[] columns = new String[]{
                CloudMediaProviderContract.MediaSetColumns.ID,
                CloudMediaProviderContract.MediaSetColumns.DISPLAY_NAME,
                CloudMediaProviderContract.MediaSetColumns.MEDIA_COVER_ID
        };

        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(new Object[] { mMediaSetId, mDisplayName, mCoverId });

        return cursor;
    }
}
