/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.MediaStore;
import android.util.Log;

import com.android.providers.media.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for handling MIME type mappings.
 */
public final class MimeTypeFixHandler {

    private static final String TAG = "MimeTypeFixHandler";
    private static final Map<String, String> sExtToMimeType = new HashMap<>();
    private static final Map<String, String> sMimeTypeToExt = new HashMap<>();

    private static final Map<String, String> sCorruptedExtToMimeType = new HashMap<>();
    private static final Map<String, String> sCorruptedMimeTypeToExt = new HashMap<>();

    /**
     * Loads MIME type mappings from the classpath resource if not already loaded.
     * <p>
     * This method initializes both the standard and corrupted MIME type maps.
     * </p>
     */
    public static void loadMimeTypes(Context context) {
        if (context == null) {
            return;
        }

        if (sExtToMimeType.isEmpty()) {
            parseTypes(context, R.raw.mime_types, sExtToMimeType, sMimeTypeToExt);
            // this will add or override the extension to mime type mapping
            parseTypes(context, R.raw.android_mime_types, sExtToMimeType, sMimeTypeToExt);
            Log.v(TAG, "MIME types loaded");
        }
        if (sCorruptedExtToMimeType.isEmpty()) {
            parseTypes(context, R.raw.corrupted_mime_types, sCorruptedExtToMimeType,
                    sCorruptedMimeTypeToExt);
            Log.v(TAG, "Corrupted MIME types loaded");
        }

    }

    /**
     * Parses the specified mime types file and populates the provided mapping with file extension
     * to MIME type entries.
     *
     * @param resource the mime.type resource
     * @param mapping  the map to populate with file extension (key) to MIME type (value) mappings
     */
    private static void parseTypes(Context context, int resource, Map<String, String> extToMimeType,
            Map<String, String> mimeTypeToExt) {
        try (InputStream inputStream = context.getResources().openRawResource(resource)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Strip comments and normalize whitespace
                    line = line.replaceAll("#.*$", "").trim().replaceAll("\\s+", " ");
                    // Skip empty lines or lines without a space (i.e., no extension mapping)
                    if (line.isEmpty() || !line.contains(" ")) {
                        continue;
                    }
                    String[] tokens = line.split(" ");
                    if (tokens.length < 2) {
                        continue;
                    }
                    String mimeType = tokens[0].toLowerCase(Locale.ROOT);
                    String firstExt = tokens[1].toLowerCase(Locale.ROOT);
                    if (firstExt.startsWith("?")) {
                        firstExt = firstExt.substring(1);
                        if (firstExt.isEmpty()) {
                            continue;
                        }
                    }

                    // ?mime ext1 ?ext2 ext3
                    if (mimeType.toLowerCase(Locale.ROOT).startsWith("?")) {
                        mimeType = mimeType.substring(1); // Remove the "?"
                        if (mimeType.isEmpty()) {
                            continue;
                        }
                        mimeTypeToExt.putIfAbsent(mimeType, firstExt);
                    } else {
                        mimeTypeToExt.put(mimeType, firstExt);
                    }

                    for (int i = 1; i < tokens.length; i++) {
                        String extension = tokens[i].toLowerCase(Locale.ROOT);
                        boolean putIfAbsent = extension.startsWith("?");
                        if (putIfAbsent) {
                            extension = extension.substring(1); // Remove the "?"
                            extToMimeType.putIfAbsent(extension, mimeType);
                        } else {
                            extToMimeType.put(extension, mimeType);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Exception raised while parsing mime.types", e);
        }
    }

    /**
     * Returns the MIME type for the given file extension from our internal mappings.
     *
     * @param extension The file extension to look up.
     * @return The associated MIME type from the primary mapping if available, or
     * {@link android.content.ClipDescription#MIMETYPE_UNKNOWN} if the extension is marked
     * as corrupted
     * Returns {@link Optional#empty()}  if not found in either mapping.
     */
    static Optional<String> getMimeType(String extension) {
        String lowerExt = extension.toLowerCase(Locale.ROOT);
        if (sExtToMimeType.containsKey(lowerExt)) {
            return Optional.of(sExtToMimeType.get(lowerExt));
        }

        if (sCorruptedExtToMimeType.containsKey(lowerExt)) {
            return Optional.of(android.content.ClipDescription.MIMETYPE_UNKNOWN);
        }

        return Optional.empty();
    }

    /**
     * Gets file extension from MIME type.
     *
     * @param mimeType The MIME type.
     * @return Optional file extension, or empty.
     */
    static Optional<String> getExtFromMimeType(String mimeType) {
        if (mimeType == null) {
            return Optional.empty();
        }

        mimeType = mimeType.toLowerCase(Locale.ROOT);
        return Optional.ofNullable(sMimeTypeToExt.get(mimeType));
    }

    /**
     * Checks if a MIME type is corrupted.
     *
     * @param mimeType The MIME type.
     * @return {@code true} if corrupted, {@code false} otherwise.
     */
    static boolean isCorruptedMimeType(String mimeType) {
        if (sMimeTypeToExt.containsKey(mimeType)) {
            return false;
        }

        return sCorruptedMimeTypeToExt.containsKey(mimeType);
    }


    /**
     * Scans the database for files with unsupported or mismatched MIME types and updates them.
     *
     * @param db The SQLiteDatabase to update.
     * @return true if all intended updates were successfully applied (or if there were no files),
     * false otherwise.
     */
    public static boolean updateUnsupportedMimeTypes(SQLiteDatabase db) {
        class FileMimeTypeUpdate {
            final long mFileId;
            final String mNewMimeType;

            FileMimeTypeUpdate(long fileId, String newMimeType) {
                this.mFileId = fileId;
                this.mNewMimeType = newMimeType;
            }
        }

        List<FileMimeTypeUpdate> filesToUpdate = new ArrayList<>();
        String[] projections = new String[]{MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DISPLAY_NAME
        };
        try (Cursor cursor = db.query(MediaStore.Files.TABLE, projections,
                null, null, null, null, null)) {

            while (cursor != null && cursor.moveToNext()) {
                long fileId = cursor.getLong(cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns._ID));
                String data = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns.DATA));
                String currentMimeType = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE));
                String displayName = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns.DISPLAY_NAME));

                String extension = FileUtils.extractFileExtension(data);
                if (extension == null) {
                    continue;
                }
                String newMimeType = MimeUtils.resolveMimeType(new File(displayName));
                if (!newMimeType.equalsIgnoreCase(currentMimeType)) {
                    filesToUpdate.add(new FileMimeTypeUpdate(fileId, newMimeType));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch files for MIME type check", e);
            return false;
        }

        Log.v(TAG, "Identified " + filesToUpdate.size() + " files with incorrect MIME types.");
        int updatedRows = 0;
        for (FileMimeTypeUpdate fileUpdate : filesToUpdate) {
            try {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.Files.FileColumns.MIME_TYPE, fileUpdate.mNewMimeType);
                contentValues.put(MediaStore.Files.FileColumns.MEDIA_TYPE,
                        MimeUtils.resolveMediaType(fileUpdate.mNewMimeType));

                String whereClause = MediaStore.Files.FileColumns._ID + " = ?";
                String[] whereArgs = new String[]{String.valueOf(fileUpdate.mFileId)};
                updatedRows += db.update(MediaStore.Files.TABLE, contentValues, whereClause,
                        whereArgs);
            } catch (Exception e) {
                Log.e(TAG, "Error updating file with id: " + fileUpdate.mFileId, e);
            }
        }
        Log.v(TAG, "Updated MIME type and Media type for " + updatedRows + " rows");
        return updatedRows == filesToUpdate.size();
    }
}
