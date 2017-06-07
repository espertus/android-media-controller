/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.mediacontroller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class connects to a {@link android.support.v4.media.MediaBrowserServiceCompat}
 * in order to get a {@link MediaControllerCompat}.
 * Buttons are displayed on screen so that the user can exercise
 * the {@link android.support.v4.media.session.MediaSessionCompat.Callback}
 * methods of the media app.
 * <p>
 * Example: If you install the UAMP app and this Monkey Test app, you will be able
 * to test UAMP media controls.
 */
public class MediaAppControllerActivity extends AppCompatActivity {
    private static final String TAG = MediaAppControllerActivity.class.getSimpleName();

    // Key names used for saving/restoring instance state.
    private static final String STATE_APP_DETAILS_KEY =
            "com.example.android.mediacontroller.STATE_APP_DETAILS_KEY";
    private static final String STATE_URI_KEY =
            "com.example.android.mediacontroller.STATE_URI_KEY";

    // Key name for Intent extras.
    private static final String APP_DETAILS_EXTRA =
            "com.example.android.mediacontroller.APP_DETAILS_EXTRA";

    private MediaAppDetails mMediaAppDetails;
    private MediaControllerCompat mController;
    private MediaBrowserCompat mBrowser;

    private View mRootView;
    private Spinner mInputTypeView;
    private EditText mUriInput;
    private TextView mMediaInfoText;

    /**
     * Builds an {@link Intent} to launch this Activity with a set of extras.
     *
     * @param activity   The Activity building the Intent.
     * @param appDetails The app details about the media app to connect to.
     * @return An Intent that can be used to start the Activity.
     */
    public static Intent buildIntent(final Activity activity,
                                     final MediaAppDetails appDetails) {
        final Intent intent = new Intent(activity, MediaAppControllerActivity.class);
        intent.putExtra(APP_DETAILS_EXTRA, appDetails);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_app_controller);
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRootView = findViewById(R.id.root_view);
        mInputTypeView = (Spinner) findViewById(R.id.input_type);
        mUriInput = (EditText) findViewById(R.id.uri_id_query);
        mMediaInfoText = (TextView) findViewById(R.id.media_info);

        if (savedInstanceState != null) {
            mMediaAppDetails = savedInstanceState.getParcelable(STATE_APP_DETAILS_KEY);
            mUriInput.setText(savedInstanceState.getString(STATE_URI_KEY));
        }

        handleIntent(getIntent());
        setupButtons();
        setupMediaController();

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            final Bitmap toolbarIcon =
                    BitmapUtils.createToolbarIcon(getResources(), mMediaAppDetails.icon);
            actionBar.setIcon(new BitmapDrawable(getResources(), toolbarIcon));
            actionBar.setTitle(mMediaAppDetails.appName);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        if (intent.getData() != null) {
            String uri = intent.getData().toString();
            mUriInput.setText(uri);
        }

        final Bundle extras = intent.getExtras();
        if (extras != null && extras.containsKey(APP_DETAILS_EXTRA)) {
            mMediaAppDetails = extras.getParcelable(APP_DETAILS_EXTRA);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putParcelable(STATE_APP_DETAILS_KEY, mMediaAppDetails);
        out.putString(STATE_URI_KEY, mUriInput.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mMediaAppDetails = savedInstanceState.getParcelable(STATE_APP_DETAILS_KEY);
        mUriInput.setText(savedInstanceState.getString(STATE_URI_KEY));
    }

    private void setupMediaController() {
        if (mBrowser != null) {
            mBrowser.disconnect();
            mBrowser = null;
            mController = null;
        }

        mBrowser = new MediaBrowserCompat(this, mMediaAppDetails.mediaServiceComponentName,
                new MyConnectionCallback(), null);
        mBrowser.connect();
    }

    private void setupButtons() {

        final PreparePlayHandler preparePlayHandler = new PreparePlayHandler(this);
        findViewById(R.id.action_prepare).setOnClickListener(preparePlayHandler);
        findViewById(R.id.action_play).setOnClickListener(preparePlayHandler);

        LinearLayout buttonList = (LinearLayout) findViewById(R.id.activity_main);
        for (final Action action : Action.createActions(this)) {
            Button button = new Button(this);
            button.setText(action.getName());
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mController != null) {
                        String id = mUriInput.getText().toString();
                        action.getMediaControllerAction().run(mController, id, null);
                    }
                }
            });
            buttonList.addView(button);
        }
    }

    @Nullable
    private String fetchMediaInfo() {
        if (mController == null) {
            Log.e(TAG, "Failed to update media info, null MediaController.");
            return null;
        }

        PlaybackStateCompat playbackState = mController.getPlaybackState();
        if (playbackState == null) {
            Log.e(TAG, "Failed to update media info, null PlaybackState.");
            return null;
        }

        Map<String, String> mediaInfos = new HashMap<>();
        mediaInfos.put(getString(R.string.info_state_string),
                String.valueOf(playbackState.getState()));

        MediaMetadataCompat mediaMetadata = mController.getMetadata();
        if (mediaMetadata != null) {
            addMediaInfo(
                    mediaInfos,
                    getString(R.string.info_title_string),
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            addMediaInfo(
                    mediaInfos,
                    getString(R.string.info_artist_string),
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            addMediaInfo(
                    mediaInfos,
                    getString(R.string.info_album_string),
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
        }
        return mediaInfos.toString();
    }

    private void addMediaInfo(Map<String, String> mediaInfos, String key, String value) {
        if (!TextUtils.isEmpty(value)) {
            mediaInfos.put(key, value);
        }
    }

    private class PreparePlayHandler implements View.OnClickListener {
        /*
         * Indexes of the values in the "input_options" string array.
         */
        private static final int INDEX_SEARCH = 0;
        private static final int INDEX_MEDIA_ID = 1;
        private static final int INDEX_URI = 2;
        private static final int INDEX_NO_PARAM = 3;

        /*
         * Indexes to the Actions returned by Action.createPreparePlayActions(Context).
         */
        private static final int ACTION_INDEX_SEARCH = INDEX_SEARCH * 2;
        private static final int ACTION_INDEX_MEDIA_ID = INDEX_MEDIA_ID * 2;
        private static final int ACTION_INDEX_URI = INDEX_URI * 2;
        private static final int ACTION_INDEX_NO_PARAM = INDEX_NO_PARAM * 2;

        private final List<Action> mPreparePlayActions;

        private PreparePlayHandler(final Context context) {
            mPreparePlayActions = Action.createPreparePlayActions(context);
        }

        @Override
        public void onClick(final View button) {
            final int prepareOrPlay = button.getId() == R.id.action_prepare ? 0 : 1;

            final Action action;
            switch (mInputTypeView.getSelectedItemPosition()) {
                case INDEX_NO_PARAM:
                    action = mPreparePlayActions.get(ACTION_INDEX_NO_PARAM + prepareOrPlay);
                    break;
                case INDEX_MEDIA_ID:
                    action = mPreparePlayActions.get(ACTION_INDEX_MEDIA_ID + prepareOrPlay);
                    break;
                case INDEX_SEARCH:
                    action = mPreparePlayActions.get(ACTION_INDEX_SEARCH + prepareOrPlay);
                    break;
                case INDEX_URI:
                    action = mPreparePlayActions.get(ACTION_INDEX_URI + prepareOrPlay);
                    break;
                default:
                    throw new IllegalStateException("Unknown input type: " +
                            mInputTypeView.getSelectedItemPosition());
            }

            final String data = mUriInput.getText().toString();
            action.getMediaControllerAction().run(mController, data, null);
        }
    }

    private class MyConnectionCallback extends MediaBrowserCompat.ConnectionCallback {

        @Override
        public void onConnected() {
            try {
                mController = new MediaControllerCompat(
                        MediaAppControllerActivity.this,
                        mBrowser.getSessionToken());

                mController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
                        onUpdate();
                    }

                    @Override
                    public void onMetadataChanged(MediaMetadataCompat metadata) {
                        onUpdate();
                    }

                    private void onUpdate() {
                        String newText = "PlaybackState changed!";
                        String mediaInfoStr = fetchMediaInfo();
                        if (mediaInfoStr != null) {
                            mMediaInfoText.setText(newText + "\n" + mediaInfoStr);
                        }
                    }
                });
                Log.d(TAG, "MediaControllerCompat created");
            } catch (RemoteException remoteException) {
                Log.e(TAG, "Failed to connect with session token: " + remoteException);
                showDisconnected(R.string.media_controller_failed_msg);
            }
        }

        @Override
        public void onConnectionSuspended() {
            Log.d(TAG, "MediaBrowser connection suspended");
            showDisconnected(R.string.connection_suspended_msg);
        }

        @Override
        public void onConnectionFailed() {
            Log.e(TAG, "MediaBrowser connection failed");
            showDisconnected(R.string.connection_failed_msg);
        }

        private void showDisconnected(@StringRes final int stringResource) {
            final Snackbar snackbar =
                    Snackbar.make(mRootView, stringResource, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.reconnect, new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    setupMediaController();
                }
            });
            snackbar.show();
        }
    }
}
