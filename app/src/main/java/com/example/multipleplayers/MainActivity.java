package com.example.multipleplayers;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity {
    private final String TAG = "MultiplePlayers";
    private GridLayout mLayout;
    private final boolean mShareAllocator = true;
    private final List<DefaultAllocator> mAllocators = new ArrayList<>();
    private final Uri VIDEO_URI = Uri.parse("https://storage.googleapis.com/wvmedia/clear/hevc/30fps/llama/llama_hevc_480p_30fps_3000.mp4");
    private final long VIDEO_DURATION_MS = 90_000;
    private final int NUM_PLAYERS = 9;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Arrange players in a N x N grid (assume videos and display have the same aspect ratio)
        mLayout = new GridLayout(this);
        int NUM_COLUMNS = (int) Math.ceil(Math.sqrt(NUM_PLAYERS));
        mLayout.setColumnCount(NUM_COLUMNS);
        for (int i = 0; i < NUM_PLAYERS; i++) {
            PlayerView playerView = new PlayerView(this);
            playerView.setUseController(false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.height = 0;
            params.width = 0;
            playerView.setLayoutParams(params);
            mLayout.addView(playerView);
        }
        setContentView(mLayout);
        String title = NUM_PLAYERS + " players using " + MediaLibraryInfo.VERSION_SLASHY;
        setTitle(title);
        Log.d(TAG, title);
        if (mShareAllocator) {
            mAllocators.add(new DefaultAllocator(false, C.DEFAULT_BUFFER_SEGMENT_SIZE));
        } else for (int i = 0; i < NUM_PLAYERS; i++) {
            mAllocators.add(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE));
        }
        mHandler = new Handler(getMainLooper());
    }

    @Override
    public void onStart() {
        super.onStart();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            View view = mLayout.getChildAt(i);
            PlayerView playerView = (PlayerView) view;
            ExoPlayer player = new ExoPlayer.Builder(this)
                    // Share allocator to avoid multiple AVAILABLE_EXTRA_CAPACITY
                    .setLoadControl(new DefaultLoadControl.Builder()
                            .setAllocator(mAllocators.get(mShareAllocator ? 0 : i))
                            .build())
                    // Split the bandwidth
                    .setTrackSelector(new DefaultTrackSelector(this, new AdaptiveTrackSelection.Factory(
                            AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
                            AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                            AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                            AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION / (float) NUM_PLAYERS)))
                    .build();
            player.setMediaItem(MediaItem.fromUri(VIDEO_URI));
            player.setPlayWhenReady(true);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.seekTo(i * VIDEO_DURATION_MS / (NUM_PLAYERS+1));
            player.prepare();
            final int finalI = i;
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Player.Listener.super.onPlaybackStateChanged(playbackState);
                    String playbackStateString;
                    switch (player.getPlaybackState()) {
                        case Player.STATE_BUFFERING: playbackStateString = "buffering"; break;
                        case Player.STATE_ENDED: playbackStateString = "ended"; break;
                        case Player.STATE_IDLE: playbackStateString = "idle"; break;
                        case Player.STATE_READY: playbackStateString = "ready"; break;
                        default: playbackStateString = "unknown"; break;
                    }
                    logTotalBytesAllocated(String.format(Locale.US, "Player%d:%s", finalI, playbackStateString));
                }
            });
            playerView.setPlayer(player);
            playerView.onResume();
        }
        logTotalBytesAllocated("onStart");
        mHandler.postDelayed(mRunnable, 15_000);
    }

    private final Runnable mRunnable = () -> {
        Log.d(TAG, "About to stop a player in the middle");
        logTotalBytesAllocated("before stop");
        stopPlayer(4);
        logTotalBytesAllocated("after stop");
    };

    private void stopPlayer(int i) {
        PlayerView playerView = (PlayerView) mLayout.getChildAt(i);
        playerView.onPause();
        ExoPlayer player = (ExoPlayer) playerView.getPlayer();
        if (player != null) {
            player.release();
            playerView.setPlayer(null);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mRunnable);
        logTotalBytesAllocated("onStop");
        for (int i = 0; i < NUM_PLAYERS; i++) {
            stopPlayer(i);
        }
        logTotalBytesAllocated("before trim");
        if (mShareAllocator) {
            mAllocators.get(0).setTargetBufferSize(0);
        } else for (int i = 0; i < NUM_PLAYERS; i++) {
            mAllocators.get(i).setTargetBufferSize(0);
        }
        logTotalBytesAllocated("after trim");
    }

    private void logTotalBytesAllocated(String prefix) {
        int totalSize = 0;
        if (mShareAllocator) {
            totalSize = mAllocators.get(0).getTotalBytesAllocated();
        } else for (int i = 0; i < NUM_PLAYERS; i++) {
            totalSize += mAllocators.get(i).getTotalBytesAllocated();
        }
        Log.d(TAG, prefix + ": " + Formatter.formatShortFileSize(this, totalSize));
    }
}