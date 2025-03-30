/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.providers.media.photopicker.data;

import static androidx.core.util.Preconditions.checkNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserProperties;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.providers.media.R;
import com.android.providers.media.photopicker.data.model.UserId;
import com.android.providers.media.photopicker.ui.TabFragment;
import com.android.providers.media.photopicker.util.CrossProfileUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresApi(Build.VERSION_CODES.S)
/*
 * Interface to query user ids {@link UserId}
 */
public interface UserManagerState {
    /** Whether there are more than 1 user profiles associated with the current user. */
    boolean isMultiUserProfiles();

    /**
     * @return if the given userId is a ManagedUserProfileId.
     */
    boolean isManagedUserProfile(UserId userId);

    /**
     * Returns the user profile selected in the photopicker. If the user does not have a
     * corresponding child profile, then this always returns the current user.
     */
    @Nullable
    UserId getCurrentUserProfileId();

    /**
     * A Map of all the profiles with their cross profile allowed status from current user.
     *
     * <p>key : userId of a profile Value : cross profile allowed status of a user profile
     * corresponding to user id with current user .
     */
    @NonNull
    Map<UserId, Boolean> getCrossProfileAllowedStatusForAll();

    /**
     * Get total number of profiles with {@link UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE}
     * available on the device
     */
    int getProfileCount();

    /** A {@link MutableLiveData} to check if cross profile interaction allowed or not. */
    @NonNull
    MutableLiveData<Map<UserId, Boolean>> getCrossProfileAllowed();

    /**
     * A list of all user profile ids including current user that need to be shown separately in
     * PhotoPicker
     */
    @NonNull
    List<UserId> getAllUserProfileIds();

    /**
     * Updates on/off values of all the user profiles and post cross profile status of all profiles
     */
    void updateProfileOffValuesAndPostCrossProfileStatus();

    /** Updates on/off values of all the user profiles */
    void updateProfileOffValues();

    /** Waits for Media Provider of the user profile corresponding to userId to be available. */
    void waitForMediaProviderToBeAvailable(UserId userId);

    /**
     * Get if it is allowed to access the otherUser profile from current user ( current user : the
     * user profile that started the photo picker activity)
     */
    @NonNull
    boolean isCrossProfileAllowedToUser(UserId otherUser);

    /** A {@link MutableLiveData} to check if there are multiple user profiles or not */
    @NonNull
    MutableLiveData<Boolean> getIsMultiUserProfiles();

    /**
     * Resets the user ids. This is usually called as a result of receiving broadcast that any
     * profile has been added or removed.
     */
    void resetUserIds();

    /**
     * Resets the user ids and set their cross profile values. This is usually called as a result of
     * receiving broadcast that any profile has been added or removed.
     */
    void resetUserIdsAndSetCrossProfileValues(Intent intent);

    /**
     * @return true if current user is the current user profile selected
     */
    boolean isCurrentUserSelected();

    /**
     * Checks device admin policy for cross-profile sharing for intent. It Also updates profile off
     * and blocked by admin status for all user profiles present on the device.
     */
    void setIntentAndCheckRestrictions(Intent intent);

    /**
     * Whether cross profile access corresponding to the userID is blocked by admin for the current
     * user.
     */
    boolean isBlockedByAdmin(UserId userId);

    /** Whether profile corresponding to the userID is on or off. */
    boolean isProfileOff(UserId userId);

    /** A map of all user profile labels corresponding to all profile userIds */
    Map<UserId, String> getProfileLabelsForAll();

    /**
     * Returns whether a user should be shown in the PhotoPicker depending on its quite mode status.
     *
     * @return One of {@link UserProperties.SHOW_IN_QUIET_MODE_PAUSED}, {@link
     *     UserProperties.SHOW_IN_QUIET_MODE_HIDDEN}, or {@link
     *     UserProperties.SHOW_IN_QUIET_MODE_DEFAULT} depending on whether the profile should be
     *     shown in quiet mode or not.
     */
    int getShowInQuietMode(UserId userId);

    /** A map of all user profile Icon ids corresponding to all profile userIds */
    Map<UserId, Drawable> getProfileBadgeForAll();

    /** Set a user as a current user profile */
    void setUserAsCurrentUserProfile(UserId userId);

    /**
     * @return true if provided user is the current user profile selected
     */
    boolean isUserSelectedAsCurrentUserProfile(UserId userId);

    /**
     * Creates an implementation of {@link UserManagerState}. Todo(b/319067964): make this singleton
     */
    static UserManagerState create(Context context) {
        return new RuntimeUserManagerState(context);
    }

    /**
     * Implementation of {@link UserManagerState}. The class assumes that all its public methods are
     * called from main thread only.
     */
    final class RuntimeUserManagerState implements UserManagerState {

        private static final String TAG = "UserManagerState";
        private static final int PROVIDER_AVAILABILITY_MAX_RETRIES = 10;
        private static final long PROVIDER_AVAILABILITY_CHECK_DELAY = 4000;
        private static final int SHOW_IN_QUIET_MODE_DEFAULT = -1;

        private final Context mContext;
        // This is the user profile that started the photo picker activity. That's why
        // it cannot change in a UserIdManager instance.
        private final UserId mCurrentUser;
        private final Handler mHandler;
        private Map<UserId, Runnable> mIsProviderAvailableRunnableMap = new HashMap<>();

        // This is the user profile selected in the photo picker. Photo picker will
        // display media for this user. It could be different from mCurrentUser.
        private UserId mCurrentUserProfile = null;

        // A map of user profile ids (Except current user) with a Boolean value that
        // represents whether corresponding user profile is blocked by admin or not.
        private Map<UserId, Boolean> mIsProfileBlockedByAdminMap = new HashMap<>();

        // A map of user profile ids (Except current user) with a Boolean value that
        // represents whether corresponding user profile is on or off.
        private Map<UserId, Boolean> mProfileOffStatus = new HashMap<>();
        private final MutableLiveData<Boolean> mIsMultiUserProfiles = new MutableLiveData<>();

        // A list of all user profile Ids present on the device that require a separate
        // tab to show in PhotoPicker. It also includes currentUser/contextUser.
        private List<UserId> mUserProfileIds = new ArrayList<>();
        private UserManager mUserManager;

        /**
         * This live data will be posted every time when a user profile change occurs in the
         * background such as turning on/off/adding/removing a user profile. The complete map will
         * be reinitiated again in {@link #getCrossProfileAllowedStatusForAll()} and will be posted
         * into the below mutable live data. This live data will be observed later in {@link
         * TabFragment}.
         */
        private final MutableLiveData<Map<UserId, Boolean>> mCrossProfileAllowedStatus =
                new MutableLiveData<>();

        private RuntimeUserManagerState(Context context) {
            this(context, UserId.CURRENT_USER);
        }

        @VisibleForTesting
        RuntimeUserManagerState(Context context, UserId currentUser) {
            mContext = context.getApplicationContext();
            mCurrentUser = checkNotNull(currentUser);
            mCurrentUserProfile = mCurrentUser;
            mHandler = new Handler(Looper.getMainLooper());
            mUserManager = mContext.getSystemService(UserManager.class);
            setUserIds();
        }

        private void setUserIds() {
            mUserProfileIds.clear();

            mUserProfileIds.add(mCurrentUser);
             boolean currentUserIsManaged =
                    mUserManager.isManagedProfile(mCurrentUser.getIdentifier());

            for (UserHandle handle : mUserManager.getUserProfiles()) {

                // For >= Android V, check if the profile wants to be shown
                if (SdkLevel.isAtLeastV()) {

                    UserProperties properties = mUserManager.getUserProperties(handle);
                    if (properties.getShowInSharingSurfaces()
                            != UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE) {
                        continue;
                    }
                } else {
                     // Only allow managed profiles + the parent user on lower than V.
                    if (currentUserIsManaged
                            && mUserManager.getProfileParent(mCurrentUser.getUserHandle())
                                    == handle) {
                        // Intentionally empty so that this profile gets added.
                    } else if (!mUserManager.isManagedProfile(handle.getIdentifier())) {
                        continue;
                    }
                }

                // Ensure the system user doesn't get added twice.
                if (mUserProfileIds.contains(UserId.of(handle))) continue;
                mUserProfileIds.add(UserId.of(handle));
            }

            mIsMultiUserProfiles.postValue(isMultiUserProfiles());
        }

        @Override
        public boolean isMultiUserProfiles() {
            assertMainThread();
            return mUserProfileIds.size() > 1;
        }

        @Override
        public int getProfileCount() {
            return mUserProfileIds.size();
        }

        @Override
        public MutableLiveData<Map<UserId, Boolean>> getCrossProfileAllowed() {
            return mCrossProfileAllowedStatus;
        }

        @Override
        public Map<UserId, Boolean> getCrossProfileAllowedStatusForAll() {
            assertMainThread();
            Map<UserId, Boolean> crossProfileAllowedStatusForAll = new HashMap<>();
            for (UserId userId : mUserProfileIds) {
                crossProfileAllowedStatusForAll.put(userId, isCrossProfileAllowedToUser(userId));
            }
            return crossProfileAllowedStatusForAll;
        }


        /**
         * External method that allows quick checking from the current user to a target user.
         *
         * Takes into account the On/Off state of the profile, as well as cross profile content
         * sharing policies.
         *
         * @param targetUser the target of the access. Current User is the "from" user.
         * @return If the target user currently is eligible for cross profile content sharing.
         */
        @Override
        public boolean isCrossProfileAllowedToUser(UserId targetUser) {
            assertMainThread();
            return !isProfileOff(targetUser) && !isBlockedByAdmin(targetUser);
        }

        /**
         * Determines if the provided UserIds support CrossProfile content sharing.
         *
         * <p>This method accepts a pair of user handles (from/to) and determines if CrossProfile
         * access is permitted between those two profiles.
         *
         * <p>There are differences is on how the access is determined based on the platform SDK:
         *
         * <p>For Platform SDK < V:
         *
         * <p>A check for CrossProfileIntentForwarders in the origin (from) profile that target the
         * destination (to) profile. If such a forwarder exists, then access is allowed, and denied
         * otherwise.
         *
         * <p>For Platform SDK >= V:
         *
         * <p>The method now takes into account access delegation, which was first added in Android
         * V.
         *
         * <p>For profiles that set the [CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT]
         * property in its [UserProperties], its parent profile will be substituted in for its side
         * of the check.
         *
         * <p>ex. For access checks between a Managed (from) and Private (to) profile, where: -
         * Managed does not delegate to its parent - Private delegates to its parent
         *
         * <p>The following logic is performed: Managed -> parent(Private)
         *
         * <p>The same check in the other direction would yield: parent(Private) -> Managed
         *
         * <p>Note how the private profile is never actually used for either side of the check,
         * since it is delegating its access check to the parent. And thus, if Managed can access
         * the parent, it can also access the private.
         *
         * @param context Current context object, for switching user contexts.
         * @param intent The current intent the Photopicker is running under.
         * @param fromUser The Origin profile, where the user is coming from
         * @param toUser The destination profile, where the user is attempting to go to.
         * @return Whether CrossProfile content sharing is supported in this handle.
         */
        private boolean isCrossProfileAllowedToUser(
                Context context, Intent intent, UserId fromUser, UserId toUser) {

            // Early exit conditions, accessing self.
            // NOTE: It is also possible to reach this state if this method is recursively checking
            // from: parent(A) to:parent(B) where A and B are both children of the same parent.
            if (fromUser.getIdentifier() == toUser.getIdentifier()) {
                return true;
            }

            // Decide if we should use actual from or parent(from)
            UserHandle currentFromUser =
                    getProfileToCheckCrossProfileAccess(fromUser.getUserHandle());

            // Decide if we should use actual to or parent(to)
            UserHandle currentToUser = getProfileToCheckCrossProfileAccess(toUser.getUserHandle());

            // When the from/to has changed from the original parameters, recursively restart the
            // checks with the new from/to handles.
            if (fromUser.getIdentifier() != currentFromUser.getIdentifier()
                    || toUser.getIdentifier() != currentToUser.getIdentifier()) {
                return isCrossProfileAllowedToUser(
                        context, intent, UserId.of(currentFromUser), UserId.of(currentToUser));
            }

            return doesCrossProfileIntentForwarderExist(
                    intent,
                    mContext.getPackageManager(),
                    fromUser.getUserHandle(),
                    toUser.getUserHandle());
        }

        /**
         * Checks the Intent to see if it can be resolved as a CrossProfileIntentForwarderActivity
         * for the target user.
         *
         * @param intent The current intent the photopicker is running under.
         * @param pm the PM which will be used for querying.
         * @param fromUser the [UserHandle] of the origin user
         * @param targetUserHandle the [UserHandle] of the target user
         * @return Whether the current Intent Photopicker may be running under has a matching
         *     CrossProfileIntentForwarderActivity
         */
        private boolean doesCrossProfileIntentForwarderExist(
                Intent intent,
                PackageManager pm,
                UserHandle fromUser,
                UserHandle targetUserHandle) {

            // Clear out the component & package before attempting to match
            Intent intentToCheck = (Intent) intent.clone();
            intentToCheck.setComponent(null);
            intentToCheck.setPackage(null);

            for (ResolveInfo resolveInfo :
                    pm.queryIntentActivitiesAsUser(
                            intentToCheck, PackageManager.MATCH_DEFAULT_ONLY, fromUser)) {

                // If the activity is a CrossProfileIntentForwardingActivity, inspect its
                // targetUserId to see if it targets the user we are currently checking for.
                if (resolveInfo.isCrossProfileIntentForwarderActivity()) {

                    /*
                     * IMPORTANT: This is a reflection based hack to ensure the profile is
                     * actually the installer of the CrossProfileIntentForwardingActivity.
                     *
                     * ResolveInfo.targetUserId exists, but is a hidden API not available to
                     * mainline modules, and no such API exists, so it is accessed via
                     * reflection below. All exceptions are caught to protect against
                     * reflection related issues such as:
                     * NoSuchFieldException / IllegalAccessException / SecurityException.
                     *
                     * In the event of an exception, the code fails "closed" for the current
                     * profile to avoid showing content that should not be visible.
                     */
                    try {
                        Field targetUserIdField =
                                resolveInfo.getClass().getDeclaredField("targetUserId");
                        targetUserIdField.setAccessible(true);
                        int targetUserId = (int) targetUserIdField.get(resolveInfo);

                        if (targetUserId == targetUserHandle.getIdentifier()) {
                            // Don't need to look further, exit the loop.
                            return true;
                        }

                    } catch (NoSuchFieldException | IllegalAccessException | SecurityException ex) {
                        // Couldn't check the targetUserId via reflection, so fail without
                        // further iterations.
                        Log.e(TAG, "Could not access targetUserId via reflection.", ex);
                        return false;
                    } catch (Exception ex) {
                        Log.e(TAG, "Exception occurred during cross profile checks", ex);
                    }
                }
            }
            return false;
        }


        @Override
        public MutableLiveData<Boolean> getIsMultiUserProfiles() {
            return mIsMultiUserProfiles;
        }

        @Override
        public void resetUserIds() {
            assertMainThread();
            setUserIds();
        }

        @Override
        public void resetUserIdsAndSetCrossProfileValues(Intent intent) {
            resetUserIds();
            setCrossProfileValues(intent);
            mIsMultiUserProfiles.postValue(isMultiUserProfiles());
        }

        @Override
        public boolean isCurrentUserSelected() {
            assertMainThread();
            return mCurrentUserProfile.equals(UserId.CURRENT_USER);
        }

        @Override
        public void setIntentAndCheckRestrictions(Intent intent) {
            assertMainThread();
            // The below method should be called even if only one profile is present on the
            // device because we want to have current profile off value and blocked by admin
            // values
            // in the corresponding maps
            updateCrossProfileValues(intent);
        }

        @Override
        public UserId getCurrentUserProfileId() {
            assertMainThread();
            return mCurrentUserProfile;
        }

        /**
         * we need the information of personal and managed user to get the pre-exiting label and
         * icon of user profile ids in case while working with pre-v version.
         */
        @Override
        public boolean isManagedUserProfile(UserId userId) {
            assertMainThread();
            if (mUserManager == null) {
                Log.e(TAG, "Cannot obtain user manager");
                return false;
            }
            return userId.isManagedProfile(mUserManager);
        }

        @Override
        public void setUserAsCurrentUserProfile(UserId userId) {
            assertMainThread();
            if (!mUserProfileIds.contains(userId)) {
                Log.e(TAG, userId + " is not a valid user profile");
                return;
            }
            setCurrentUserProfileId(userId);
        }

        @Override
        public boolean isUserSelectedAsCurrentUserProfile(UserId userId) {
            assertMainThread();
            return mCurrentUserProfile.equals(userId);
        }

        private void setCurrentUserProfileId(UserId userId) {
            mCurrentUserProfile = userId;
        }

        private void updateCrossProfileValues(Intent intent) {
            setCrossProfileValues(intent);
            updateAndPostCrossProfileStatus();
        }

        private void setCrossProfileValues(Intent intent) {
            // 1. Check if PICK_IMAGES intent is allowed by admin to show cross user content
            setBlockedByAdminValue(intent);

            // 2. Check if work profile is off
            updateProfileOffValuesAndPostCrossProfileStatus();

            // 3. For first initial setup, wait for MediaProvider to be on.
            // (This is not blocking)
            for (UserId userId : mUserProfileIds) {
                if (mProfileOffStatus.get(userId)) {
                    waitForMediaProviderToBeAvailable(userId);
                }
            }
        }

        @Override
        public void waitForMediaProviderToBeAvailable(UserId userId) {
            assertMainThread();
            // Remove callbacks if any pre-available callbacks are present in the message
            // queue for given user
            stopWaitingForProviderToBeAvailableForUser(userId);
            if (CrossProfileUtils.isMediaProviderAvailable(userId, mContext)) {
                mProfileOffStatus.put(userId, false);
                updateAndPostCrossProfileStatus();
                return;
            }
            waitForProviderToBeAvailable(userId, /* numOfTries */ 1);
        }

        private void waitForProviderToBeAvailable(UserId userId, int numOfTries) {
            // The runnable should make sure to post update on the live data if it is
            // changed.
            Runnable runnable =
                    () -> {
                        try {
                            // We stop the recursive check when
                            // 1. the provider is available
                            // 2. the profile is in quiet mode, i.e. provider will not be available
                            // 3. after maximum retries
                            if (CrossProfileUtils.isMediaProviderAvailable(userId, mContext)) {
                                mProfileOffStatus.put(userId, false);
                                updateAndPostCrossProfileStatus();
                                return;
                            }

                            if (CrossProfileUtils.isQuietModeEnabled(userId, mContext)) {
                                return;
                            }

                            if (numOfTries <= PROVIDER_AVAILABILITY_MAX_RETRIES) {
                                Log.d(
                                        TAG,
                                        "MediaProvider is not available. Retry after "
                                                + PROVIDER_AVAILABILITY_CHECK_DELAY);
                                waitForProviderToBeAvailable(userId, numOfTries + 1);
                                return;
                            }

                            Log.w(
                                    TAG,
                                    "Failed waiting for MediaProvider for user:"
                                            + userId
                                            + " to be available");
                        } catch (Exception e) {
                            Log.e(
                                    TAG,
                                    "An error occurred in runnable while waiting for "
                                            + "MediaProvider for user:"
                                            + userId
                                            + " to be available",
                                    e);
                        }
                    };
            mIsProviderAvailableRunnableMap.put(userId, runnable);
            mHandler.postDelayed(runnable, PROVIDER_AVAILABILITY_CHECK_DELAY);
        }

        // Todo(b/319561515): Modify method to remove callbacks only for specified user
        private void stopWaitingForProviderToBeAvailableForUser(UserId userId) {
            Runnable runnable = mIsProviderAvailableRunnableMap.get(userId);
            if (runnable == null) {
                return;
            }
            mHandler.removeCallbacks(runnable);
            mIsProviderAvailableRunnableMap.put(userId, null);
        }

        @Override
        public void updateProfileOffValuesAndPostCrossProfileStatus() {
            updateProfileOffValues();
            updateAndPostCrossProfileStatus();
        }

        @Override
        public void updateProfileOffValues() {
            assertMainThread();
            mProfileOffStatus.clear();
            for (UserId userId : mUserProfileIds) {
                mProfileOffStatus.put(userId, isProfileOffInternal(userId));
            }
        }

        private void updateAndPostCrossProfileStatus() {
            mCrossProfileAllowedStatus.postValue(getCrossProfileAllowedStatusForAll());
        }

        private Boolean isProfileOffInternal(UserId userId) {
            return CrossProfileUtils.isQuietModeEnabled(userId, mContext)
                    || !CrossProfileUtils.isMediaProviderAvailable(userId, mContext);
        }

        /**
         * Determines if the target UserHandle delegates its content sharing to its parent.
         *
         * @param userHandle The target handle to check delegation for.
         * @return TRUE if V+ and the handle delegates to parent. False otherwise.
         */
        private boolean isCrossProfileStrategyDelegatedToParent(UserHandle userHandle) {
            if (SdkLevel.isAtLeastV()) {
                if (mUserManager == null) {
                    Log.e(TAG, "Cannot obtain user manager");
                    return false;
                }
                UserProperties userProperties = mUserManager.getUserProperties(userHandle);
                if (userProperties.getCrossProfileContentSharingStrategy()
                        == userProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Acquires the correct {@link UserHandle} which should be used for CrossProfile access
         * checks.
         *
         * @param userHandle the origin handle.
         * @return The UserHandle that should be used for cross profile access checks. In the event
         *     the origin handle delegates its access, this may not be the same handle as the origin
         *     handle.
         */
        private UserHandle getProfileToCheckCrossProfileAccess(UserHandle userHandle) {
            if (mUserManager == null) {
                Log.e(TAG, "Cannot obtain user manager");
                return null;
            }
            return isCrossProfileStrategyDelegatedToParent(userHandle)
                    ? mUserManager.getProfileParent(userHandle)
                    : userHandle;
        }

        /**
         * Updates Cross Profile access for all UserProfiles in {@code mUserProfileIds}
         *
         * <p>This method looks at a variety of situations for each Profile and decides if the
         * profile's content is accessible by the current process owner user id.
         *
         * <p>- UserProperties attributes for CrossProfileDelegation are checked first -
         * CrossProfileIntentForwardingActivitys are resolved via the process owner's
         * PackageManager, and are considered when evaluating cross profile to the target profile.
         *
         * <p>- In the event none of the above checks succeeds, the profile is considered to be
         * inaccessible to the current process user, and is thus marked as "BlockedByAdmin".
         *
         * @param intent The intent Photopicker is currently running under, for
         *     CrossProfileForwardActivity checking.
         */
        private void setBlockedByAdminValue(Intent intent) {
            if (intent == null) {
                Log.e(
                        TAG,
                        "No intent specified to check if cross profile forwarding is"
                                + " allowed.");
                return;
            }

            mIsProfileBlockedByAdminMap.clear();
            for (UserId userId : mUserProfileIds) {
                mIsProfileBlockedByAdminMap.put(
                        // Boolean inversion seems strange, but this map is the opposite of what was
                        // calculated, (which are blocked, rather than which are accessible) so the
                        // boolean needs to be inverted.
                        userId,
                        !isCrossProfileAllowedToUser(mContext, intent, UserId.CURRENT_USER, userId)
                );
            }
        }

        @Override
        public Map<UserId, String> getProfileLabelsForAll() {
            assertMainThread();
            Map<UserId, String> profileLabels = new HashMap<>();
            for (UserId userId : mUserProfileIds) {
                UserHandle userHandle = userId.getUserHandle();
                profileLabels.put(userId, getProfileLabel(userHandle));
            }

            return profileLabels;
        }

        private String getProfileLabel(UserHandle userHandle) {
            if (SdkLevel.isAtLeastV()) {
                try {
                    Context userContext = mContext.createContextAsUser(userHandle, 0 /* flags */);
                    UserManager userManager = userContext.getSystemService(UserManager.class);
                    if (userManager == null) {
                        Log.e(TAG, "Cannot obtain user manager");
                        return null;
                    }
                    return userManager.getProfileLabel();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "could not create user context for user.", e);
                } catch (Exception e) {
                    Log.e(TAG, "Exception while fetching profile badge", e);
                }
            }

            // Fall back case if not V, or an error encountered above, return hard coded strings.
            boolean isPrimaryProfile = mUserManager.getProfileParent(userHandle) == null;
            boolean isManagedProfile = mUserManager.isManagedProfile(userHandle.getIdentifier());

            int resId;
            if (isPrimaryProfile) {
                resId = R.string.photopicker_profile_primary_label;
            } else if (isManagedProfile) {
                resId = R.string.photopicker_profile_managed_label;
            } else {
                resId = R.string.photopicker_profile_unknown_label;
            }

            return mContext.getString(resId);
        }

        @Override
        public Map<UserId, Drawable> getProfileBadgeForAll() {
            assertMainThread();
            Map<UserId, Drawable> profileBadges = new HashMap<>();
            for (UserId userId : mUserProfileIds) {
                profileBadges.put(userId, getProfileBadge(userId.getUserHandle()));
            }
            return profileBadges;
        }

        private Drawable getProfileBadge(UserHandle userHandle) {
            if (SdkLevel.isAtLeastV()) {
                try {
                    Context userContext = mContext.createContextAsUser(userHandle, 0 /* flags */);
                    UserManager userManager = userContext.getSystemService(UserManager.class);
                    if (userManager == null) {
                        Log.e(TAG, "Cannot obtain user manager");
                        return null;
                    }
                    return userManager.getUserBadge();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "could not create user context for user.", e);
                } catch (Exception e) {
                    Log.e(TAG, "Exception while fetching profile badge", e);
                }
            }

            // Fall back case if not V, or an error encountered above, return hard coded icons.
            boolean isManagedProfile = mUserManager.isManagedProfile(userHandle.getIdentifier());
            int drawable =
                    isManagedProfile ? R.drawable.ic_work_outline : R.drawable.ic_personal_mode;
            return mContext.getDrawable(drawable);
        }

        @Override
        public int getShowInQuietMode(UserId userId) {
            assertMainThread();
            if (SdkLevel.isAtLeastV()) {
                if (mUserManager == null) {
                    Log.e(TAG, "Cannot obtain user manager");
                    return UserProperties.SHOW_IN_QUIET_MODE_DEFAULT;
                }
                UserProperties userProperties =
                        mUserManager.getUserProperties(userId.getUserHandle());
                return userProperties.getShowInQuietMode();
            }
            return SHOW_IN_QUIET_MODE_DEFAULT;
        }

        @Override
        public List<UserId> getAllUserProfileIds() {
            assertMainThread();
            return mUserProfileIds;
        }

        @Override
        public boolean isBlockedByAdmin(UserId userId) {
            assertMainThread();
            return mIsProfileBlockedByAdminMap.get(userId);
        }

        @Override
        public boolean isProfileOff(UserId userId) {
            assertMainThread();
            return mProfileOffStatus.get(userId);
        }

        private void assertMainThread() {
            if (Looper.getMainLooper().isCurrentThread()) return;

            throw new IllegalStateException(
                    "UserManagerState methods are expected to be called"
                            + "from main thread. "
                            + (Looper.myLooper() == null
                                    ? ""
                                    : "Current thread "
                                            + Looper.myLooper().getThread()
                                            + ", Main thread "
                                            + Looper.getMainLooper().getThread()));
        }
    }
}
