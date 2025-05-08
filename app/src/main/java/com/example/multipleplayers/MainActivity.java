package com.example.multipleplayers;

import android.app.Activity;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Process;
import android.widget.GridLayout;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.PlayerView;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends Activity {
    private static final int NUM_PLAYERS = 9;
    private static final boolean REQUEST_TUNNELING = true;
    // Any of these should be divided by NUM_PLAYERS?
    private static final int LOAD_CONTROL_MIN_BUFFER_MS = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
    private static final int LOAD_CONTROL_MAX_BUFFER_MS = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
    private static final int LOAD_CONTROL_BUFFER_FOR_PLAYBACK_MS = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
    private static final int LOAD_CONTROL_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
    public static final int TRACK_SELECTION_MIN_DURATION_FOR_QUALITY_INCREASE_MS = AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS;
    public static final int TRACK_SELECTION_MAX_DURATION_FOR_QUALITY_DECREASE_MS = AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS;
    public static final int TRACK_SELECTION_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS;
    public static final float TRACK_SELECTION_BANDWIDTH_FRACTION = AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION / NUM_PLAYERS;
    private static final String URI = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd";
    private static final String L1_LICENSE_URI = "https://proxy.uat.widevine.com/proxy?video_id=GTS_HW_SECURE_ALL&provider=widevine_test";
    private static final String L3_LICENSE_URI = "https://proxy.uat.widevine.com/proxy?video_id=GTS_SW_SECURE_CRYPTO&provider=widevine_test";
    private final String LICENSE_URI = Util.isRunningOnEmulator() ? L3_LICENSE_URI : L1_LICENSE_URI;
    private final PlayerView[] mPlayerViews = new PlayerView[NUM_PLAYERS];
    // Is this shared playback thread able to handle messages within 10ms? (as mentioned in ExoPlayer.setPlaybackLooper)
    private HandlerThread mPlaybackThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GridLayout gridLayout = new GridLayout(this);
        // NxN grid: 2 to 4 players => 2x2 grid, 5 to 9 players => 3x3 grid, etc.
        int NUM_COLUMNS = (int) Math.ceil(Math.sqrt(NUM_PLAYERS));
        gridLayout.setColumnCount(NUM_COLUMNS);
        for (int i = 0; i < NUM_PLAYERS; i++) {
            mPlayerViews[i] = new PlayerView(this);
            mPlayerViews[i].setUseController(false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.height = 0;
            params.width = 0;
            mPlayerViews[i].setLayoutParams(params);
            gridLayout.addView(mPlayerViews[i]);
        }
        setContentView(gridLayout);
        setTitle(NUM_PLAYERS + " players using " + MediaLibraryInfo.VERSION_SLASHY);
    }

    @Override
    public void onStart() {
        super.onStart();
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(URI)
                .setDrmConfiguration(
                        new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                .setLicenseUri(LICENSE_URI)
                                .build()
                )
                .build();
        // Do not trim allocator on reset of individual players
        DefaultAllocator allocator = new DefaultAllocator(false, C.DEFAULT_BUFFER_SEGMENT_SIZE);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setAllocator(allocator)
                .setBufferDurationsMs(
                        LOAD_CONTROL_MIN_BUFFER_MS,
                        LOAD_CONTROL_MAX_BUFFER_MS,
                        LOAD_CONTROL_BUFFER_FOR_PLAYBACK_MS,
                        LOAD_CONTROL_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        mPlaybackThread = new HandlerThread("ExoPlayer:Playback", Process.THREAD_PRIORITY_AUDIO);
        mPlaybackThread.start();
        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(
                TRACK_SELECTION_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
                TRACK_SELECTION_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                TRACK_SELECTION_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                TRACK_SELECTION_BANDWIDTH_FRACTION);
        BandwidthMeter bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(this);
        for (int i = 0; i < NUM_PLAYERS; i++) {
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(this, trackSelectionFactory);
            DefaultTrackSelector.Parameters.Builder trackSelectorParameterBuilder = trackSelector.buildUponParameters();
            if (REQUEST_TUNNELING) {
                trackSelectorParameterBuilder.setTunnelingEnabled(true);
            } else if (i != 0) {
                // Ignore the audio track entirely
                trackSelectorParameterBuilder.setRendererDisabled(C.TRACK_TYPE_AUDIO, true);
            }
            trackSelector.setParameters(trackSelectorParameterBuilder);
            ExoPlayer player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setPlaybackLooper(mPlaybackThread.getLooper())
                .setTrackSelector(trackSelector)
                .setBandwidthMeter(bandwidthMeter)
                .build();
            player.setMediaItem(mediaItem);
            player.setPlayWhenReady(true);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.prepare();
            if (REQUEST_TUNNELING && i != 0) {
                player.setVolume(0f); // Need the audio track for tunneling
            }
            mPlayerViews[i].setPlayer(player);
            mPlayerViews[i].onResume();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        for (PlayerView playerView: mPlayerViews) {
            playerView.onPause();
            ExoPlayer player = (ExoPlayer) playerView.getPlayer();
            if (player != null) {
                player.release();
                playerView.setPlayer(null);
            }
        }
        mPlaybackThread.quitSafely();
    }
}
