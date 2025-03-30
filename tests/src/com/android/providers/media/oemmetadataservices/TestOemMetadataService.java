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

package com.android.providers.media.oemmetadataservices;

import android.os.ParcelFileDescriptor;
import android.provider.OemMetadataService;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TestOemMetadataService extends OemMetadataService {

    static Map<String, String> sOemMetadata = new HashMap<>();

    static {
        sOemMetadata.put("a", "1");
        sOemMetadata.put("b", "2");
        sOemMetadata.put("c", "3");
        sOemMetadata.put("d", "4");
        sOemMetadata.put("e", "5");
    }

    @Override
    public Set<String> onGetSupportedMimeTypes() {
        return Set.of("audio/mpeg", "audio/3gpp", "audio/flac");
    }

    @Override
    public Map<String, String> onGetOemCustomData(@NonNull ParcelFileDescriptor pfd) {
        return sOemMetadata;
    }

    public static void updateOemMetadataServiceData() {
        sOemMetadata.put("f", "6");
    }

    public static void resetOemMetadataServiceData() {
        sOemMetadata.remove("f");
    }
}
