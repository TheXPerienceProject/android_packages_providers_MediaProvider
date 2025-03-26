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

package com.android.providers.media.photopicker.sync;

import static com.android.providers.media.photopicker.sync.PickerSyncManager.SYNC_WORKER_INPUT_AUTHORITY;
import static com.android.providers.media.photopicker.sync.PickerSyncManager.SYNC_WORKER_INPUT_CATEGORY_ID;
import static com.android.providers.media.photopicker.sync.PickerSyncManager.SYNC_WORKER_INPUT_SYNC_SOURCE;
import static com.android.providers.media.photopicker.sync.SyncTrackerRegistry.markMediaSetsSyncAsComplete;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.providers.media.photopicker.PickerSyncController;
import com.android.providers.media.photopicker.util.exceptions.RequestObsoleteException;
import com.android.providers.media.photopicker.v2.sqlite.MediaInMediaSetsDatabaseUtil;
import com.android.providers.media.photopicker.v2.sqlite.MediaSetsDatabaseUtil;

import java.util.List;

/**
 * This worker is responsible for cleaning up the cached media sets or media sets content for the
 * given categoryId
 */
public class MediaSetsResetWorker extends Worker {
    private static final String TAG = "MediaSetsResetWorker";

    public MediaSetsResetWorker(
            @NonNull Context context, @NonNull WorkerParameters workerParameters) {
        super(context, workerParameters);
    }

    @Override
    public ListenableWorker.Result doWork() {

        final int syncSource = getInputData().getInt(SYNC_WORKER_INPUT_SYNC_SOURCE,
                /* defaultValue */ -1);
        final String categoryId = getInputData().getString(SYNC_WORKER_INPUT_CATEGORY_ID);
        final String authority = getInputData().getString(SYNC_WORKER_INPUT_AUTHORITY);

        // Do not allow endless re-runs of this worker, if this isn't the original run,
        // just fail and wait until the next scheduled run.
        if (getRunAttemptCount() > 0) {
            Log.w(TAG, "Worker retry was detected, ending this run in failure.");
            return ListenableWorker.Result.failure();
        }

        boolean isMediaInMediaSetsCacheDeleted = clearMediaSetsContentCache(categoryId, authority);
        boolean isMediaSetsCacheDeleted = clearMediaSetsCache(syncSource, categoryId, authority);

        // Both the tables were cleared. Mark the worker's run as success
        if (isMediaSetsCacheDeleted && isMediaInMediaSetsCacheDeleted) {
            return ListenableWorker.Result.success();
        }

        return ListenableWorker.Result.failure();
    }

    private boolean clearMediaSetsCache(int syncSource,
                                        @NonNull String categoryId,
                                        @NonNull String authority) {

        requireNonNull(categoryId);
        requireNonNull(authority);

        SQLiteDatabase database = getDatabase();

        try {
            checkIfWorkerHasStopped();

            database.beginTransaction();

            MediaSetsDatabaseUtil.clearMediaSetsCache(database, categoryId, authority);

            if (database.inTransaction()) {
                database.setTransactionSuccessful();
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not clear media sets cache", e);
            return false;
        } finally {
            // Ensure that the transaction ends and the DB lock is released.
            if (database.inTransaction()) {
                database.endTransaction();
            }
            markMediaSetsSyncAsComplete(syncSource, getId());
        }
    }

    private boolean clearMediaSetsContentCache(
            @NonNull String categoryId,
            @NonNull String authority) {

        requireNonNull(categoryId);
        requireNonNull(authority);

        SQLiteDatabase database = getDatabase();

        try {
            checkIfWorkerHasStopped();

            database.beginTransaction();

            List<String> mediaSetPickerIds = MediaSetsDatabaseUtil
                    .getMediaSetPickerIdsForGivenCategoryId(database, categoryId, authority);
            MediaInMediaSetsDatabaseUtil.clearMediaInMediaSetsCache(
                    database, mediaSetPickerIds);

            if (database.inTransaction()) {
                database.setTransactionSuccessful();
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not clear media sets content cache", e);
            return false;
        } finally {
            // Ensure that the transaction ends and the DB lock is released.
            if (database.inTransaction()) {
                database.endTransaction();
            }
        }
    }

    private void checkIfWorkerHasStopped() throws RequestObsoleteException {
        if (isStopped()) {
            throw new RequestObsoleteException("CategoriesResetWorker has stopped.");
        }
    }

    @NonNull
    private SQLiteDatabase getDatabase() {
        return PickerSyncController.getInstanceOrThrow().getDbFacade().getDatabase();
    }
}
