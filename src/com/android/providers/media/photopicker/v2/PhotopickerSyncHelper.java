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

package com.android.providers.media.photopicker.v2;

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.providers.media.photopicker.PickerSyncController;

import java.util.Objects;

/**
 * Helper methods required across the picker search sync workers are extracted out into this
 * utility class.
 */
public class PhotopickerSyncHelper {

    /**
     * Checks if the authority in the parameter is that of the local provider
     */
    public boolean isAuthorityLocal(@NonNull String authority) {
        Objects.requireNonNull(authority);
        return getLocalProviderAuthority().equals(authority);
    }

    /**
     * Returns local provider authority
     */
    @Nullable
    public String getLocalProviderAuthority() {
        return PickerSyncController.getInstanceOrThrow().getLocalProvider();
    }

    /**
     * Returns the authority of the current cloud provider
     */
    @Nullable
    public String getCurrentCloudProviderAuthority() {
        return PickerSyncController.getInstanceOrThrow()
                .getCloudProviderOrDefault(/* defaultValue */ null);
    }

    /**
     * Returns a database object to be used for required database operations
     */
    public SQLiteDatabase getDatabase() {
        return PickerSyncController.getInstanceOrThrow().getDbFacade().getDatabase();
    }
}
