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

package com.android.providers.media.backupandrestore;

import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.FIELD_SEPARATOR;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.KEY_VALUE_SEPARATOR;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.RESTORE_COMPLETED;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.SHARED_PREFERENCE_NAME;

import android.content.Context;
import android.provider.MediaStore;

import java.util.HashMap;
import java.util.Map;

public class BackupAndRestoreTestUtils {

    /**
     * Map used to store column name for given key id.
     */
    private static Map<String, String> sColumnIdToKeyMap;

    /**
     * Map used to store key id for given column name.
     */
    private static Map<String, String> sColumnNameToIdMap;

    static void createKeyToColumnNameMap() {
        sColumnIdToKeyMap = new HashMap<>();
        sColumnIdToKeyMap.put("0", MediaStore.Files.FileColumns.IS_FAVORITE);
        sColumnIdToKeyMap.put("1", MediaStore.Files.FileColumns.MEDIA_TYPE);
        sColumnIdToKeyMap.put("2", MediaStore.Files.FileColumns.MIME_TYPE);
        sColumnIdToKeyMap.put("3", MediaStore.Files.FileColumns._USER_ID);
        sColumnIdToKeyMap.put("4", MediaStore.Files.FileColumns.SIZE);
        sColumnIdToKeyMap.put("5", MediaStore.MediaColumns.DATE_TAKEN);
        sColumnIdToKeyMap.put("6", MediaStore.MediaColumns.CD_TRACK_NUMBER);
        sColumnIdToKeyMap.put("7", MediaStore.MediaColumns.ALBUM);
        sColumnIdToKeyMap.put("8", MediaStore.MediaColumns.ARTIST);
        sColumnIdToKeyMap.put("9", MediaStore.MediaColumns.AUTHOR);
        sColumnIdToKeyMap.put("10", MediaStore.MediaColumns.COMPOSER);
        sColumnIdToKeyMap.put("11", MediaStore.MediaColumns.GENRE);
        sColumnIdToKeyMap.put("12", MediaStore.MediaColumns.TITLE);
        sColumnIdToKeyMap.put("13", MediaStore.MediaColumns.YEAR);
        sColumnIdToKeyMap.put("14", MediaStore.MediaColumns.DURATION);
        sColumnIdToKeyMap.put("15", MediaStore.MediaColumns.NUM_TRACKS);
        sColumnIdToKeyMap.put("16", MediaStore.MediaColumns.WRITER);
        sColumnIdToKeyMap.put("17", MediaStore.MediaColumns.ALBUM_ARTIST);
        sColumnIdToKeyMap.put("18", MediaStore.MediaColumns.DISC_NUMBER);
        sColumnIdToKeyMap.put("19", MediaStore.MediaColumns.COMPILATION);
        sColumnIdToKeyMap.put("20", MediaStore.MediaColumns.BITRATE);
        sColumnIdToKeyMap.put("21", MediaStore.MediaColumns.CAPTURE_FRAMERATE);
        sColumnIdToKeyMap.put("22", MediaStore.Audio.AudioColumns.TRACK);
        sColumnIdToKeyMap.put("23", MediaStore.MediaColumns.DOCUMENT_ID);
        sColumnIdToKeyMap.put("24", MediaStore.MediaColumns.INSTANCE_ID);
        sColumnIdToKeyMap.put("25", MediaStore.MediaColumns.ORIGINAL_DOCUMENT_ID);
        sColumnIdToKeyMap.put("26", MediaStore.MediaColumns.RESOLUTION);
        sColumnIdToKeyMap.put("27", MediaStore.MediaColumns.ORIENTATION);
        sColumnIdToKeyMap.put("28", MediaStore.Video.VideoColumns.COLOR_STANDARD);
        sColumnIdToKeyMap.put("29", MediaStore.Video.VideoColumns.COLOR_TRANSFER);
        sColumnIdToKeyMap.put("30", MediaStore.Video.VideoColumns.COLOR_RANGE);
        sColumnIdToKeyMap.put("31", MediaStore.Files.FileColumns._VIDEO_CODEC_TYPE);
        sColumnIdToKeyMap.put("32", MediaStore.MediaColumns.WIDTH);
        sColumnIdToKeyMap.put("33", MediaStore.MediaColumns.HEIGHT);
        sColumnIdToKeyMap.put("34", MediaStore.Images.ImageColumns.DESCRIPTION);
        sColumnIdToKeyMap.put("35", MediaStore.Images.ImageColumns.EXPOSURE_TIME);
        sColumnIdToKeyMap.put("36", MediaStore.Images.ImageColumns.F_NUMBER);
        sColumnIdToKeyMap.put("37", MediaStore.Images.ImageColumns.ISO);
        sColumnIdToKeyMap.put("38", MediaStore.Images.ImageColumns.SCENE_CAPTURE_TYPE);
        sColumnIdToKeyMap.put("39", MediaStore.Files.FileColumns._SPECIAL_FORMAT);
        sColumnIdToKeyMap.put("40", MediaStore.Files.FileColumns.OWNER_PACKAGE_NAME);
        // Adding number gap to allow addition of new values
        sColumnIdToKeyMap.put("80", MediaStore.MediaColumns.XMP);
    }

    static void createColumnNameToKeyMap() {
        sColumnNameToIdMap = new HashMap<>();
        sColumnNameToIdMap.put(MediaStore.Files.FileColumns.IS_FAVORITE, "0");
        sColumnNameToIdMap.put(MediaStore.Files.FileColumns.MEDIA_TYPE, "1");
        sColumnNameToIdMap.put(MediaStore.Files.FileColumns.MIME_TYPE, "2");
        sColumnNameToIdMap.put(MediaStore.Files.FileColumns._USER_ID, "3");
        sColumnNameToIdMap.put(MediaStore.Files.FileColumns.SIZE, "4");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.DATE_TAKEN, "5");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.CD_TRACK_NUMBER, "6");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.ALBUM, "7");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.ARTIST, "8");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.AUTHOR, "9");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.COMPOSER, "10");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.GENRE, "11");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.TITLE, "12");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.YEAR, "13");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.DURATION, "14");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.NUM_TRACKS, "15");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.WRITER, "16");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.ALBUM_ARTIST, "17");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.DISC_NUMBER, "18");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.COMPILATION, "19");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.BITRATE, "20");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.CAPTURE_FRAMERATE, "21");
        sColumnNameToIdMap.put(MediaStore.Audio.AudioColumns.TRACK, "22");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.DOCUMENT_ID, "23");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.INSTANCE_ID, "24");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.ORIGINAL_DOCUMENT_ID, "25");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.RESOLUTION, "26");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.ORIENTATION, "27");
        sColumnNameToIdMap.put(MediaStore.Video.VideoColumns.COLOR_STANDARD, "28");
        sColumnNameToIdMap.put(MediaStore.Video.VideoColumns.COLOR_TRANSFER, "29");
        sColumnNameToIdMap.put(MediaStore.Video.VideoColumns.COLOR_RANGE, "30");
        sColumnNameToIdMap.put(MediaStore.Files.FileColumns._VIDEO_CODEC_TYPE, "31");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.WIDTH, "32");
        sColumnNameToIdMap.put(MediaStore.MediaColumns.HEIGHT, "33");
        sColumnNameToIdMap.put(MediaStore.Images.ImageColumns.DESCRIPTION, "34");
        sColumnNameToIdMap.put(MediaStore.Images.ImageColumns.EXPOSURE_TIME, "35");
        sColumnNameToIdMap.put(MediaStore.Images.ImageColumns.F_NUMBER, "36");
        sColumnNameToIdMap.put(MediaStore.Images.ImageColumns.ISO, "37");
        sColumnNameToIdMap.put(MediaStore.Images.ImageColumns.SCENE_CAPTURE_TYPE, "38");
        sColumnNameToIdMap.put(MediaStore.Files.FileColumns._SPECIAL_FORMAT, "39");
        sColumnNameToIdMap.put(MediaStore.Files.FileColumns.OWNER_PACKAGE_NAME, "40");
        // Adding number gap to allow addition of new values
        sColumnNameToIdMap.put(MediaStore.MediaColumns.XMP, "80");
    }

    static Map<String, String> deSerialiseValueString(String valueString) {
        if (sColumnIdToKeyMap == null) {
            createKeyToColumnNameMap();
        }

        String[] values = valueString.split(":::");
        Map<String, String> map = new HashMap<>();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }

            String[] keyValue = value.split("=", 2);
            map.put(sColumnIdToKeyMap.get(keyValue[0]), keyValue[1]);
        }

        return map;
    }

    static String createSerialisedValue(Map<String, String> entries) {
        if (sColumnNameToIdMap == null) {
            createColumnNameToKeyMap();
        }

        StringBuilder sb = new StringBuilder();
        for (String backupColumn : sColumnNameToIdMap.keySet()) {
            if (entries.containsKey(backupColumn)) {
                sb.append(sColumnNameToIdMap.get(backupColumn)).append(KEY_VALUE_SEPARATOR).append(
                        entries.get(backupColumn));
                sb.append(FIELD_SEPARATOR);
            }
        }
        return sb.toString();
    }

    static boolean getSharedPreferenceValue(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCE_NAME,
                Context.MODE_PRIVATE).getBoolean(RESTORE_COMPLETED, false);
    }
}
