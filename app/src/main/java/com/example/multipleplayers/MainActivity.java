package com.example.multipleplayers;

import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Process;
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
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.ui.PlayerView;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity {
    private final int NUM_PLAYERS = 9;
    private final PlayerView[] mPlayerViews = new PlayerView[NUM_PLAYERS];
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
        MediaItem mediaItem = MediaItem.fromUri("https://storage.googleapis.com/wvmedia/clear/hevc/30fps/llama/llama_hevc_480p_30fps_3000.mp4");
        // Do not trim allocator on reset of individual players
        DefaultAllocator allocator = new DefaultAllocator(false, C.DEFAULT_BUFFER_SEGMENT_SIZE);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setAllocator(allocator)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        mPlaybackThread = new HandlerThread("ExoPlayer:Playback", Process.THREAD_PRIORITY_AUDIO);
        mPlaybackThread.start();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            ExoPlayer player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setPlaybackLooper(mPlaybackThread.getLooper())
                .build();
            player.setMediaItem(mediaItem);
            player.setPlayWhenReady(true);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.prepare();
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
