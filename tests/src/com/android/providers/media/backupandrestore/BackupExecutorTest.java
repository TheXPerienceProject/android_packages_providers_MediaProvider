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

package com.android.providers.media.backupandrestore;

import static com.android.providers.media.backupandrestore.BackupAndRestoreTestUtils.deSerialiseValueString;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.BACKUP_COLUMNS;
import static com.android.providers.media.scan.MediaScanner.REASON_UNKNOWN;
import static com.android.providers.media.scan.MediaScannerTest.stage;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.MediaStore;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.providers.media.IsolatedContext;
import com.android.providers.media.R;
import com.android.providers.media.TestConfigStore;
import com.android.providers.media.leveldb.LevelDBInstance;
import com.android.providers.media.leveldb.LevelDBManager;
import com.android.providers.media.leveldb.LevelDBResult;
import com.android.providers.media.scan.ModernMediaScanner;
import com.android.providers.media.util.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(com.android.providers.media.flags.Flags.FLAG_ENABLE_BACKUP_AND_RESTORE)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
public final class BackupExecutorTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Set<File> mStagedFiles = new HashSet<>();

    private Context mIsolatedContext;

    private ContentResolver mIsolatedResolver;

    private ModernMediaScanner mModern;

    private File mDownloadsDir;

    private String mLevelDbPath;

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOG_COMPAT_CHANGE,
                        Manifest.permission.READ_COMPAT_CHANGE_CONFIG,
                        Manifest.permission.DUMP,
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.INTERACT_ACROSS_USERS);

        mIsolatedContext = new IsolatedContext(context, "modern", /*asFuseThread*/ false);
        mIsolatedResolver = mIsolatedContext.getContentResolver();
        mModern = new ModernMediaScanner(mIsolatedContext, new TestConfigStore());
        mDownloadsDir = new File(Environment.getExternalStorageDirectory(),
                Environment.DIRECTORY_DOWNLOADS);
        mLevelDbPath =
                mIsolatedContext.getFilesDir().getAbsolutePath() + "/backup/external_primary/";
        FileUtils.deleteContents(mDownloadsDir);
    }

    @After
    public void tearDown() {
        // Delete leveldb directory after test
        File levelDbDir = new File(mLevelDbPath);
        for (File f : levelDbDir.listFiles()) {
            f.delete();
        }
        levelDbDir.delete();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testBackup() throws Exception {
        try {
            // Add all files in Downloads directory
            File file = new File(mDownloadsDir, "a_" + SystemClock.elapsedRealtimeNanos() + ".jpg");
            stageNewFile(R.raw.test_image, file);
            file = new File(mDownloadsDir, "b_" + SystemClock.elapsedRealtimeNanos() + ".gif");
            stageNewFile(R.raw.test_gif, file);
            file = new File(mDownloadsDir, "c_" + SystemClock.elapsedRealtimeNanos() + ".mp3");
            stageNewFile(R.raw.test_audio, file);
            file = new File(mDownloadsDir, "d_" + SystemClock.elapsedRealtimeNanos() + ".jpg");
            stageNewFile(R.raw.test_motion_photo, file);
            file = new File(mDownloadsDir, "e_" + SystemClock.elapsedRealtimeNanos() + ".mp4");
            stageNewFile(R.raw.test_video, file);
            file = new File(mDownloadsDir, "f_" + SystemClock.elapsedRealtimeNanos() + ".mp4");
            stageNewFile(R.raw.test_video_gps, file);
            file = new File(mDownloadsDir, "g_" + SystemClock.elapsedRealtimeNanos() + ".mp4");
            stageNewFile(R.raw.test_video_xmp, file);
            file = new File(mDownloadsDir, "h_" + SystemClock.elapsedRealtimeNanos() + ".mp3");
            stageNewFile(R.raw.test_audio, file);
            file = new File(mDownloadsDir, "i_" + SystemClock.elapsedRealtimeNanos() + ".mp3");
            stageNewFile(R.raw.test_audio_empty_title, file);
            file = new File(mDownloadsDir, "j_" + SystemClock.elapsedRealtimeNanos() + ".xspf");
            stageNewFile(R.raw.test_xspf, file);
            file = new File(mDownloadsDir, "k_" + SystemClock.elapsedRealtimeNanos() + ".mp4");
            stageNewFile(R.raw.large_xmp, file);

            mModern.scanDirectory(mDownloadsDir, REASON_UNKNOWN);
            // Run idle maintenance to backup data
            MediaStore.runIdleMaintenance(mIsolatedResolver);

            // Stage another file to test incremental backup
            file = new File(mDownloadsDir, "l_" + SystemClock.elapsedRealtimeNanos() + ".mp4");
            stageNewFile(R.raw.large_xmp, file);
            // Run idle maintenance again for incremental backup
            MediaStore.runIdleMaintenance(mIsolatedResolver);

            Bundle bundle = new Bundle();
            bundle.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "_data LIKE ? AND is_pending=0 AND _modifier=3 AND volume_name=? AND "
                            + "mime_type IS NOT NULL");
            bundle.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    new String[]{mDownloadsDir.getAbsolutePath() + "/%",
                            MediaStore.VOLUME_EXTERNAL_PRIMARY});
            List<String> columns = new ArrayList<>(Arrays.asList(BACKUP_COLUMNS));
            columns.add(MediaStore.Files.FileColumns.DATA);
            String[] projection = columns.toArray(new String[0]);
            Set<File> scannedFiles = new HashSet<>();
            Map<String, Map<String, String>> pathToAttributesMap = new HashMap<>();
            try (Cursor c = mIsolatedResolver.query(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), projection,
                    bundle, null)) {
                assertThat(c).isNotNull();
                while (c.moveToNext()) {
                    Map<String, String> attributesMap = new HashMap<>();
                    for (String col : BACKUP_COLUMNS) {
                        assertWithMessage("Column is missing: " + col).that(
                                c.getColumnIndex(col)).isNotEqualTo(-1);
                        Optional<String> value = BackupExecutor.extractValue(c, col);
                        value.ifPresent(s -> attributesMap.put(col, s));
                    }
                    String path = c.getString(
                            c.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                    scannedFiles.add(new File(path));
                    pathToAttributesMap.put(path, attributesMap);
                }
            }

            assertThat(scannedFiles).containsAtLeastElementsIn(mStagedFiles);
            assertWithMessage("Database does not have entries for staged files").that(
                    pathToAttributesMap).isNotEmpty();
            LevelDBInstance levelDBInstance = LevelDBManager.getInstance(mLevelDbPath);
            for (String path : pathToAttributesMap.keySet()) {
                LevelDBResult levelDBResult = levelDBInstance.query(path);
                // Assert leveldb has entry for file path
                assertThat(levelDBResult.isSuccess()).isTrue();
                Map<String, String> actualResultMap = deSerialiseValueString(
                        levelDBResult.getValue());
                assertThat(actualResultMap.keySet()).isNotEmpty();
                assertThat(actualResultMap).isEqualTo(pathToAttributesMap.get(path));
            }
        } finally {
            FileUtils.deleteContents(mDownloadsDir);
            mStagedFiles.clear();
        }
    }

    private void stageNewFile(int resId, File file) throws IOException {
        file.createNewFile();
        mStagedFiles.add(file);
        stage(resId, file);
    }
}
