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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ClipDescription;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.providers.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.Optional;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
public class MimeTypeFixHandlerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private SQLiteDatabase mDatabase;
    private static final String FILES_TABLE_NAME = MediaStore.Files.TABLE;
    private static final String DATABASE_FILE = "mime_type_fix.db";

    @Before
    public void setUp() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT == Build.VERSION_CODES.VANILLA_ICE_CREAM);
        final Context context = InstrumentationRegistry.getTargetContext();

        context.deleteDatabase(DATABASE_FILE);
        mDatabase = Objects.requireNonNull(
                context.openOrCreateDatabase(DATABASE_FILE, Context.MODE_PRIVATE, null));
        MimeTypeFixHandler.loadMimeTypes(context);
        createFilesTable();
    }

    @After
    public void tearDown() throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();

        if (mDatabase != null) {
            mDatabase.close();
        }
        context.deleteDatabase(DATABASE_FILE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MIME_TYPE_FIX_FOR_ANDROID_15)
    public void testGetMimeType() {
        Optional<String> dwgMimeType = MimeTypeFixHandler.getMimeType("dwg");
        assertTrue(dwgMimeType.isPresent());
        assertEquals(ClipDescription.MIMETYPE_UNKNOWN, dwgMimeType.get());


        Optional<String> avifMimeType = MimeTypeFixHandler.getMimeType("avif");
        assertTrue(avifMimeType.isPresent());
        assertEquals("image/avif", avifMimeType.get());


        Optional<String> ecmascriptMimeType = MimeTypeFixHandler.getMimeType("es");
        assertTrue(ecmascriptMimeType.isPresent());
        assertEquals("application/ecmascript", ecmascriptMimeType.get());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MIME_TYPE_FIX_FOR_ANDROID_15)
    public void testIsCorruptedMimeType() {
        // jpeg present in mime.types mapping
        assertFalse(MimeTypeFixHandler.isCorruptedMimeType("image/jpeg"));

        // avif present in android.mime.types mapping
        assertFalse(MimeTypeFixHandler.isCorruptedMimeType("image/avif"));

        // dwg in corrupted mapping
        assertTrue(MimeTypeFixHandler.isCorruptedMimeType("image/vnd.dwg"));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MIME_TYPE_FIX_FOR_ANDROID_15)
    public void testGetExtFromMimeType() {
        // jpeg present in mime.types mapping
        Optional<String> jpegExtension = MimeTypeFixHandler.getExtFromMimeType("image/jpeg");
        assertTrue(jpegExtension.isPresent());

        // avif present in android.mime.types mapping
        Optional<String> avifExtension = MimeTypeFixHandler.getExtFromMimeType("image/avif");
        assertTrue(avifExtension.isPresent());

        // dwg in corrupted mapping
        Optional<String> dwgExtension = MimeTypeFixHandler.getExtFromMimeType("image/vnd.dwg");
        assertFalse(dwgExtension.isPresent());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MIME_TYPE_FIX_FOR_ANDROID_15)
    public void testUpdateUnsupportedMimeTypesForWrongEntries() {
        createEntriesInFilesTable();

        // Assert incorrect MIME types before the fix
        try (Cursor cursor = getCursorFilesTable()) {
            assertNotNull(cursor);

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(FileColumns._ID));
                String mimeType = cursor.getString(
                        cursor.getColumnIndexOrThrow(FileColumns.MIME_TYPE));
                int mediaType = cursor.getInt(cursor.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE));

                switch (id) {
                    case 1: // dwg
                        assertEquals("image/vnd.dwg", mimeType);
                        assertEquals(FileColumns.MEDIA_TYPE_IMAGE, mediaType);
                        break;
                    case 2: // avif
                        assertEquals("image/avif", mimeType);
                        assertEquals(FileColumns.MEDIA_TYPE_IMAGE, mediaType);
                        break;
                    case 3: // ecmascript
                        assertEquals("text/javascript", mimeType);
                        assertEquals(FileColumns.MEDIA_TYPE_DOCUMENT, mediaType);
                        break;
                    default:
                        fail("Unexpected _ID: " + id);
                }
            }
        }

        // fix the data
        MimeTypeFixHandler.updateUnsupportedMimeTypes(mDatabase);

        // Assert correct MIME types after the fix
        try (Cursor cursor = getCursorFilesTable()) {
            assertNotNull(cursor);

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(FileColumns._ID));
                String mimeType = cursor.getString(
                        cursor.getColumnIndexOrThrow(FileColumns.MIME_TYPE));
                int mediaType = cursor.getInt(cursor.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE));

                switch (id) {
                    case 1: // dwg
                        assertEquals(ClipDescription.MIMETYPE_UNKNOWN, mimeType);
                        assertEquals(FileColumns.MEDIA_TYPE_NONE, mediaType);
                        break;
                    case 2: // avif
                        assertEquals("image/avif", mimeType);
                        assertEquals(FileColumns.MEDIA_TYPE_IMAGE, mediaType);
                        break;
                    case 3: // ecmascript
                        assertEquals("application/ecmascript", mimeType);
                        assertEquals(FileColumns.MEDIA_TYPE_NONE, mediaType);
                        break;
                    default:
                        fail("Unexpected _ID: " + id);
                }
            }
        }
    }

    private void createFilesTable() {
        mDatabase.execSQL("DROP TABLE IF EXISTS " + FILES_TABLE_NAME + ";");
        mDatabase.execSQL("CREATE TABLE " + FILES_TABLE_NAME + " ("
                + FileColumns._ID + " INTEGER PRIMARY KEY, "
                + FileColumns.DATA + " TEXT, "
                + FileColumns.DISPLAY_NAME + " TEXT, "
                + FileColumns.MIME_TYPE + " TEXT, "
                + FileColumns.MEDIA_TYPE + " INTEGER);");
    }

    private Cursor getCursorFilesTable() {
        String[] projections = new String[]{
                FileColumns._ID,
                FileColumns.DATA,
                FileColumns.DISPLAY_NAME,
                FileColumns.MIME_TYPE,
                FileColumns.MEDIA_TYPE,
        };

        return mDatabase.query(
                FILES_TABLE_NAME,
                projections,
                null,
                null,
                null,
                null,
                FileColumns._ID
        );
    }

    private void createEntriesInFilesTable() {
        // dwg in corrupted mime types
        String dwgFileName = "image1.dwg";
        insertFileRecord("/path/" + dwgFileName, "image/vnd.dwg",
                FileColumns.MEDIA_TYPE_IMAGE, dwgFileName);

        // avif in corrupted mime types but also in android_mime_types
        String avifFileName = "image2.avif";
        insertFileRecord("/path/" + avifFileName, "image/avif",
                FileColumns.MEDIA_TYPE_IMAGE, avifFileName);

        String ecamascriptFileName = "file1.es";
        insertFileRecord("/path/" + ecamascriptFileName, "text/javascript",
                FileColumns.MEDIA_TYPE_DOCUMENT, ecamascriptFileName);
    }

    private void insertFileRecord(String data, String mimeType, int mediaType, String displayName) {
        String sql = "INSERT INTO " + FILES_TABLE_NAME + " ("
                + FileColumns.DATA + ", "
                + FileColumns.MIME_TYPE + ", "
                + FileColumns.MEDIA_TYPE + ", "
                + FileColumns.DISPLAY_NAME + ") "
                + "VALUES (?, ?, ?, ?);";

        mDatabase.execSQL(sql, new Object[]{data, mimeType, mediaType, displayName});
    }
}
