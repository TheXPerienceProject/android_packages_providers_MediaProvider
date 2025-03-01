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

import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.BACKUP_DIRECTORY_NAME;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.RESTORE_DIRECTORY_NAME;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.deleteBackupDirectory;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.deleteRestoreDirectory;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.enableRestoreFromRecentBackup;
import static com.android.providers.media.backupandrestore.BackupAndRestoreUtils.isBackupAndRestoreSupported;

import android.annotation.NonNull;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.content.ContentProviderClient;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import com.android.providers.media.MediaProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * This custom BackupAgent is used for backing up and restoring MediaProvider's metadata.
 * <p>
 * It implements {@link BackupAgent#onFullBackup} and {@link BackupAgent#onRestoreFinished}
 * to handle pre-processing tasks before the backup is initiated and post-processing tasks after
 * the restore is completed, respectively.
 * </p>
 */
public final class MediaBackupAgent extends BackupAgent {

    private static final String TAG = MediaBackupAgent.class.getSimpleName();

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     *     Checks if media provider's backup and restore is supported for the device. If supported,
     *     triggers a backup
     * </p>
     */
    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        if ((data.getTransportFlags() & FLAG_DEVICE_TO_DEVICE_TRANSFER) == 0) {
            Log.v(TAG, "Skip cloud backup for media provider");
            return;
        }

        Context context = getApplicationContext();
        if (isBackupAndRestoreSupported(context)) {
            try (ContentProviderClient cpc = context.getContentResolver()
                    .acquireContentProviderClient(MediaStore.AUTHORITY)) {
                final MediaProvider provider = ((MediaProvider) cpc.getLocalContentProvider());
                provider.triggerBackup();
            } catch (Exception e) {
                Log.e(TAG, "Failed to trigger backup", e);
            }
        }

        super.onFullBackup(data);
    }

    /**
     * This method is called on the target device as a part of restore process after files transfer
     * is completed and before the first media scan is triggered by restore apk. Checks if media
     * provider's backup and restore is supported for the device. If supported,
     *  1. Copies over files from backup directory to restore directory & deletes backup directory
     *  2. Sets shared preference to true
     */
    @Override
    public void onRestoreFinished() {
        super.onRestoreFinished();

        Context context = getApplicationContext();
        if (isBackupAndRestoreSupported(context)) {
            // The backed-up data from the source device will be read from the restore directory,
            // while the device will create its own backup directory.
            copyContentsFromBackupToRestoreDirectory(context);

            // Delete the copied over backup directory
            deleteBackupDirectory(context);

            // Indicates restore is completed and metadata can be read from restore directory
            enableRestoreFromRecentBackup(context);
        }
    }

    /**
     * Copies the contents of the backup directory to the restore directory.
     *
     * @param context The application context, used to retrieve the files directory.
     */
    private static void copyContentsFromBackupToRestoreDirectory(@NonNull Context context) {
        deleteRestoreDirectory(context);

        File filesDir = context.getFilesDir();
        File backupDir = new File(filesDir, BACKUP_DIRECTORY_NAME);
        File restoreDir = new File(filesDir, RESTORE_DIRECTORY_NAME);

        try {
            if (backupDir.exists() && backupDir.isDirectory()) {
                copyDirectory(backupDir.toPath(), restoreDir.toPath());
            } else {
                Log.e(TAG, "Backup directory does not exist.");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to copy backup directory to restore directory", ex);
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(path -> {
                try {
                    Path destination = target.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        if (!Files.exists(destination)) {
                            Files.createDirectories(destination);
                        }
                    } else {
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to copy contents of backup "
                            + "directory to restore directory", e);
                }
            });
        }
    }
}
