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

import static com.android.providers.media.util.LegacyLogging.TAG;

import android.content.ContentValues;
import android.os.Environment;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.modules.utils.build.SdkLevel;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegacyFileUtils {

    /**
     * Recursively walk the contents of the given {@link Path}, invoking the
     * given {@link Consumer} for every file and directory encountered. This is
     * typically used for recursively deleting a directory tree.
     * <p>
     * Gracefully attempts to process as much as possible in the face of any
     * failures.
     */
    public static void walkFileTreeContents(@NonNull Path path, @NonNull Consumer<Path> operation) {
        try {
            Files.walkFileTree(path, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!Objects.equals(path, file)) {
                        operation.accept(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    Log.w(TAG, "Failed to visit " + file, e);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    if (!Objects.equals(path, dir)) {
                        operation.accept(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Log.w(TAG, "Failed to walk " + path, e);
        }
    }

    /**
     * Recursively delete all contents inside the given directory. Gracefully
     * attempts to delete as much as possible in the face of any failures.
     *
     * @deprecated if you're calling this from inside {@code MediaProvider}, you
     * likely want to call {@link #forEach} with a separate
     * invocation to invalidate FUSE entries.
     */
    @Deprecated
    public static void deleteContents(@NonNull File dir) {
        walkFileTreeContents(dir.toPath(), (path) -> {
            path.toFile().delete();
        });
    }

    public static @Nullable String extractDisplayName(@Nullable String data) {
        if (data == null) return null;
        if (data.indexOf('/') == -1) {
            return data;
        }
        if (data.endsWith("/")) {
            data = data.substring(0, data.length() - 1);
        }
        return data.substring(data.lastIndexOf('/') + 1);
    }

    public static @Nullable String extractFileExtension(@Nullable String data) {
        if (data == null) return null;
        data = extractDisplayName(data);

        final int lastDot = data.lastIndexOf('.');
        if (lastDot == -1) {
            return null;
        } else {
            return data.substring(lastDot + 1);
        }
    }

    public static final Pattern PATTERN_DOWNLOADS_FILE = Pattern.compile(
            "(?i)^/storage/[^/]+/(?:[0-9]+/)?Download/.+");
    public static final Pattern PATTERN_EXPIRES_FILE = Pattern.compile(
            "(?i)^\\.(pending|trashed)-(\\d+)-([^/]+)$");

    /**
     * File prefix indicating that the file {@link MediaColumns#IS_PENDING}.
     */
    public static final String PREFIX_PENDING = "pending";

    /**
     * File prefix indicating that the file {@link MediaColumns#IS_TRASHED}.
     */
    public static final String PREFIX_TRASHED = "trashed";

    private static final boolean PROP_CROSS_USER_ALLOWED =
            SystemProperties.getBoolean("external_storage.cross_user.enabled", false);

    private static final String PROP_CROSS_USER_ROOT = isCrossUserEnabled()
            ? SystemProperties.get("external_storage.cross_user.root", "") : "";

    private static final String PROP_CROSS_USER_ROOT_PATTERN = ((PROP_CROSS_USER_ROOT.isEmpty())
            ? "" : "(?:" + PROP_CROSS_USER_ROOT + "/)?");

    /**
     * Regex that matches paths in all well-known package-specific directories,
     * and which captures the package name as the first group.
     */
    public static final Pattern PATTERN_OWNED_PATH = Pattern.compile(
            "(?i)^/storage/[^/]+/(?:[0-9]+/)?"
                    + PROP_CROSS_USER_ROOT_PATTERN
                    + "Android/(?:data|media|obb)/([^/]+)(/?.*)?");

    /**
     * The recordings directory. This is used for R OS. For S OS or later,
     * we use {@link Environment#DIRECTORY_RECORDINGS} directly.
     */
    public static final String DIRECTORY_RECORDINGS = "Recordings";

    /**
     * Regex that matches paths for {@link MediaColumns#RELATIVE_PATH}
     */
    private static final Pattern PATTERN_RELATIVE_PATH = Pattern.compile(
            "(?i)^/storage/(?:emulated/[0-9]+/|[^/]+/)");

    /**
     * Regex that matches paths under well-known storage paths.
     */
    private static final Pattern PATTERN_VOLUME_NAME = Pattern.compile(
            "(?i)^/storage/([^/]+)");

    public static boolean isCrossUserEnabled() {
        return PROP_CROSS_USER_ALLOWED || SdkLevel.isAtLeastS();
    }

    private static @Nullable String normalizeUuid(@Nullable String fsUuid) {
        return fsUuid != null ? fsUuid.toLowerCase(Locale.ROOT) : null;
    }

    public static @Nullable String extractVolumeName(@Nullable String data) {
        if (data == null) return null;
        final Matcher matcher = PATTERN_VOLUME_NAME.matcher(data);
        if (matcher.find()) {
            final String volumeName = matcher.group(1);
            if (volumeName.equals("emulated")) {
                return MediaStore.VOLUME_EXTERNAL_PRIMARY;
            } else {
                return normalizeUuid(volumeName);
            }
        } else {
            return MediaStore.VOLUME_INTERNAL;
        }
    }

    public static @Nullable String extractRelativePath(@Nullable String data) {
        if (data == null) return null;

        final String path;
        try {
            path = getCanonicalPath(data);
        } catch (IOException e) {
            Log.d(TAG, "Unable to get canonical path from invalid data path: " + data, e);
            return null;
        }

        final Matcher matcher = PATTERN_RELATIVE_PATH.matcher(path);
        if (matcher.find()) {
            final int lastSlash = path.lastIndexOf('/');
            if (lastSlash == -1 || lastSlash < matcher.end()) {
                // This is a file in the top-level directory, so relative path is "/"
                // which is different than null, which means unknown path
                return "/";
            } else {
                return path.substring(matcher.end(), lastSlash + 1);
            }
        } else {
            return null;
        }
    }

    /**
     * Compute several scattered {@link MediaColumns} values from
     * {@link MediaColumns#DATA}. This method performs no enforcement of
     * argument validity.
     */
    public static void computeValuesFromData(@NonNull ContentValues values, boolean isForFuse) {
        // Worst case we have to assume no bucket details
        values.remove(MediaColumns.VOLUME_NAME);
        values.remove(MediaColumns.RELATIVE_PATH);
        values.remove(MediaColumns.IS_TRASHED);
        values.remove(MediaColumns.DATE_EXPIRES);
        values.remove(MediaColumns.DISPLAY_NAME);
        values.remove(MediaColumns.BUCKET_ID);
        values.remove(MediaColumns.BUCKET_DISPLAY_NAME);

        String data = values.getAsString(MediaColumns.DATA);
        if (TextUtils.isEmpty(data)) return;

        try {
            data = new File(data).getCanonicalPath();
            values.put(MediaColumns.DATA, data);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "Invalid file path:%s in request.", data));
        }

        final File file = new File(data);
        final File fileLower = new File(data.toLowerCase(Locale.ROOT));

        values.put(MediaColumns.VOLUME_NAME, extractVolumeName(data));
        values.put(MediaColumns.RELATIVE_PATH, extractRelativePath(data));
        final String displayName = extractDisplayName(data);
        final Matcher matcher = LegacyFileUtils.PATTERN_EXPIRES_FILE.matcher(displayName);
        if (matcher.matches()) {
            values.put(MediaColumns.IS_PENDING,
                    matcher.group(1).equals(LegacyFileUtils.PREFIX_PENDING) ? 1 : 0);
            values.put(MediaColumns.IS_TRASHED,
                    matcher.group(1).equals(LegacyFileUtils.PREFIX_TRASHED) ? 1 : 0);
            values.put(MediaColumns.DATE_EXPIRES, Long.parseLong(matcher.group(2)));
            values.put(MediaColumns.DISPLAY_NAME, matcher.group(3));
        } else {
            if (isForFuse) {
                // Allow Fuse thread to set IS_PENDING when using DATA column.
                // TODO(b/156867379) Unset IS_PENDING when Fuse thread doesn't explicitly specify
                // IS_PENDING. It can't be done now because we scan after create. Scan doesn't
                // explicitly specify the value of IS_PENDING.
            } else {
                values.put(MediaColumns.IS_PENDING, 0);
            }
            values.put(MediaColumns.IS_TRASHED, 0);
            values.putNull(MediaColumns.DATE_EXPIRES);
            values.put(MediaColumns.DISPLAY_NAME, displayName);
        }

        // Buckets are the parent directory
        final String parent = fileLower.getParent();
        if (parent != null) {
            values.put(MediaColumns.BUCKET_ID, parent.hashCode());
            // The relative path for files in the top directory is "/"
            if (!"/".equals(values.getAsString(MediaColumns.RELATIVE_PATH))) {
                values.put(MediaColumns.BUCKET_DISPLAY_NAME, file.getParentFile().getName());
            } else {
                values.putNull(MediaColumns.BUCKET_DISPLAY_NAME);
            }
        }
    }

    @VisibleForTesting
    static ArrayMap<String, String> sAudioTypes = new ArrayMap<>();

    static {
        sAudioTypes.put(Environment.DIRECTORY_RINGTONES, AudioColumns.IS_RINGTONE);
        sAudioTypes.put(Environment.DIRECTORY_NOTIFICATIONS, AudioColumns.IS_NOTIFICATION);
        sAudioTypes.put(Environment.DIRECTORY_ALARMS, AudioColumns.IS_ALARM);
        sAudioTypes.put(Environment.DIRECTORY_PODCASTS, AudioColumns.IS_PODCAST);
        sAudioTypes.put(Environment.DIRECTORY_AUDIOBOOKS, AudioColumns.IS_AUDIOBOOK);
        sAudioTypes.put(Environment.DIRECTORY_MUSIC, AudioColumns.IS_MUSIC);
        if (SdkLevel.isAtLeastS()) {
            sAudioTypes.put(Environment.DIRECTORY_RECORDINGS, AudioColumns.IS_RECORDING);
        } else {
            sAudioTypes.put(LegacyFileUtils.DIRECTORY_RECORDINGS, AudioColumns.IS_RECORDING);
        }
    }

    /**
     * Returns the canonical pathname string of the provided abstract pathname.
     *
     * @return The canonical pathname string denoting the same file or directory as this abstract
     * pathname.
     * @see File#getCanonicalPath()
     */
    @NonNull
    public static String getCanonicalPath(@NonNull String path) throws IOException {
        Objects.requireNonNull(path);
        return new File(path).getCanonicalPath();
    }

}
