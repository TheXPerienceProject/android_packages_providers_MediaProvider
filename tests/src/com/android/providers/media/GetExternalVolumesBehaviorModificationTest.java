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
package com.android.providers.media;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Parcel;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.providers.media.flags.Flags;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({Flags.FLAG_EXCLUDE_UNRELIABLE_VOLUMES})
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
public class GetExternalVolumesBehaviorModificationTest {
    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock
    private Context mContext;

    private static final String RELIABLE_STORAGE = "reliable";
    private static final String UNRELIABLE_STORAGE = "unreliable";

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // create a testing storage volume which behaves as a reliable storage and hence have a
        // directory starting with storage/. Naming this volume as reliable.
        Parcel parcel = Parcel.obtain();
        parcel.writeString8("1"); // id
        parcel.writeString8("Storage/emulated/testDir"); // path
        parcel.writeString8("Storage/emulated/testDir"); // internalPath
        parcel.writeString8(""); // description
        parcel.writeInt(0); // removable (boolean)
        parcel.writeInt(1); // primary (boolean)
        parcel.writeInt(0); // emulated (boolean)
        parcel.writeInt(0); // allowMassStorage (boolean)
        parcel.writeInt(0); // allowFullBackup (boolean)
        parcel.writeLong(1000); // maxFileSize
        parcel.writeParcelable(UserHandle.CURRENT, 0); // owner (UserHandle)
        parcel.writeInt(0); // uuid
        parcel.writeString8(RELIABLE_STORAGE); // name
        parcel.writeString8(Environment.MEDIA_MOUNTED); // state

        parcel.setDataPosition(0);

        StorageVolume reliableStorage = StorageVolume.CREATOR.createFromParcel(parcel);

        // create a testing storage volume which behaves as a unreliable storage and hence have a
        // directory starting with mnt/. Naming this volume as unreliable.
        Parcel parcel2 = Parcel.obtain();
        parcel2.writeString8("2"); // id
        parcel2.writeString8("mnt/testDir"); // path
        parcel2.writeString8("mnt/testDir"); // internalPath
        parcel2.writeString8(""); // description
        parcel2.writeInt(0); // removable (boolean)
        parcel2.writeInt(1); // primary (boolean)
        parcel2.writeInt(0); // emulated (boolean)
        parcel2.writeInt(0); // allowMassStorage (boolean)
        parcel2.writeInt(0); // allowFullBackup (boolean)
        parcel2.writeLong(1000); // maxFileSize
        parcel2.writeParcelable(UserHandle.CURRENT, 0); // owner (UserHandle)
        parcel2.writeInt(0); // uuid
        parcel2.writeString8(UNRELIABLE_STORAGE); // name
        parcel2.writeString8(Environment.MEDIA_MOUNTED); // state

        parcel2.setDataPosition(0);

        StorageVolume unreliableStorage = StorageVolume.CREATOR.createFromParcel(parcel2);

        // Creating a mock storage manager which on being queried for storage volumes return the
        // list of both reliable and unreliable storage.
        StorageManager mockedStorageManager = Mockito.mock(StorageManager.class);
        Mockito.when(mockedStorageManager.getStorageVolumes()).thenReturn(new ArrayList<>(
                Arrays.asList(reliableStorage, unreliableStorage)));

        // Creating a mock for context so that it returns the mocked storage manager.
        mContext = Mockito.mock(Context.class);
        Mockito.when(mContext.getSystemServiceName(StorageManager.class)).thenReturn(
                Context.STORAGE_SERVICE);
        Mockito.when(mContext.getApplicationInfo()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getContext().getApplicationInfo());
        Mockito.when(mContext.getSystemService(StorageManager.class)).thenReturn(
                mockedStorageManager);
    }

    /**
     * This test verifies the behaviour of MediaStore.getExternalVolumeNames() before enabling the
     * EXCLUDE_UNRELIABLE_STORAGE_VOLUMES appcompat flag.
     */
    @Test
    @DisableCompatChanges({MediaProvider.EXCLUDE_UNRELIABLE_STORAGE_VOLUMES})
    public void test_getExternalVolumes_returnsAllVolumes() {
        Set<String> result = MediaStore.getExternalVolumeNames(mContext);

        // Verify result is not null and both unreliable and reliable storage is returned.
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains(RELIABLE_STORAGE));
        Assert.assertTrue(result.contains(UNRELIABLE_STORAGE));
    }

    /**
     * This test verifies the behaviour of MediaStore.getExternalVolumeNames() before enabling the
     * EXCLUDE_UNRELIABLE_STORAGE_VOLUMES appcompat flag.
     */
    @Test
    @EnableCompatChanges({MediaProvider.EXCLUDE_UNRELIABLE_STORAGE_VOLUMES})
    public void test_getExternalVolumes_returnsFilteredVolumes() {
        Set<String> result = MediaStore.getExternalVolumeNames(mContext);

        // Verify result is not null and only reliable storage is returned.
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());
        Assert.assertTrue(result.contains(RELIABLE_STORAGE));
        Assert.assertFalse(result.contains(UNRELIABLE_STORAGE));
    }
}

