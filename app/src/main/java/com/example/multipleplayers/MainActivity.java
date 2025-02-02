package com.example.multipleplayers;

import android.net.Uri;
import android.os.Bundle;
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

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity {
    private final String TAG = "MultiplePlayers";
    private GridLayout mLayout;
    private DefaultAllocator mAllocator;
    private final Uri VIDEO_URI = Uri.parse("https://storage.googleapis.com/wvmedia/clear/hevc/30fps/llama/llama_hevc_480p_30fps_3000.mp4");
    private final int NUM_PLAYERS = 9;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Arrange players in a N x N grid (assume videos and display have the same aspect ratio)
        mLayout = new GridLayout(this);
        int NUM_COLUMNS = (int) Math.ceil(Math.sqrt(NUM_PLAYERS));
        mLayout.setColumnCount(NUM_COLUMNS);
        for (int i = 0; i < NUM_PLAYERS; i++) {
            View view;
            PlayerView playerView = new PlayerView(this);
            playerView.setUseController(false);
            view = playerView;
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.height = 0;
            params.width = 0;
            view.setLayoutParams(params);
            mLayout.addView(view);
        }
        setContentView(mLayout);
        String title = NUM_PLAYERS + " players using " + MediaLibraryInfo.VERSION_SLASHY;
        setTitle(title);
        Log.d(TAG, title);
        mAllocator = new DefaultAllocator(false, C.DEFAULT_BUFFER_SEGMENT_SIZE);
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
                            .setAllocator(mAllocator)
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
            player.prepare();
            playerView.setPlayer(player);
            playerView.onResume();
        }
        logTotalBytesAllocated("onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        logTotalBytesAllocated("onStop");
        for (int i = 0; i < NUM_PLAYERS; i++) {
            PlayerView playerView = (PlayerView) mLayout.getChildAt(i);
            playerView.onPause();
            ExoPlayer player = (ExoPlayer) playerView.getPlayer();
            if (player != null) {
                player.release();
                playerView.setPlayer(null);
            }
        }
        logTotalBytesAllocated("before trim");
        mAllocator.setTargetBufferSize(0);
        logTotalBytesAllocated("after trim");
    }

    private void logTotalBytesAllocated(String prefix) {
        int totalSize = mAllocator.getTotalBytesAllocated();
        Log.d(TAG, prefix + ": " + Formatter.formatShortFileSize(this, totalSize));
    }
}