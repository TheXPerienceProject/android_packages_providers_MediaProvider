/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.providers.media.util;

import androidx.annotation.Nullable;

public class LegacyStringUtils {

    /**
     * Variant of {@link String#startsWith(String)} but which tests with
     * case-insensitivity.
     */
    public static boolean startsWithIgnoreCase(@Nullable String target, @Nullable String other) {
        if (target == null || other == null) return false;
        if (other.length() > target.length()) return false;
        return target.regionMatches(true, 0, other, 0, other.length());
    }

}
