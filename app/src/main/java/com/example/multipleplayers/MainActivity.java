package com.example.multipleplayers;

import android.os.Bundle;
import android.widget.GridLayout;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final List<PlayerView> playerViewList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int NUM_PLAYERS = 9;
        // Arrange players in a N x N grid
        GridLayout grid = new GridLayout(this);
        int NUM_COLUMNS = (int) Math.ceil(Math.sqrt(NUM_PLAYERS));
        grid.setColumnCount(NUM_COLUMNS);
        for (int i = 0; i < NUM_PLAYERS; i++) {
            PlayerView playerView = new PlayerView(this);
            playerView.setUseController(false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.height = 0;
            params.width = 0;
            playerView.setLayoutParams(params);
            grid.addView(playerView);
            playerViewList.add(playerView);
        }
        setContentView(grid);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onStart() {
        super.onStart();
        DefaultAllocator allocator = new DefaultAllocator(false, C.DEFAULT_BUFFER_SEGMENT_SIZE);
        for (PlayerView playerView: playerViewList) {
            ExoPlayer player = new ExoPlayer.Builder(this)
                    .setLoadControl(new DefaultLoadControl.Builder()
                            .setAllocator(allocator)
                            .build())
                    .build();
            player.setMediaItem(MediaItem.fromUri("https://storage.googleapis.com/wvmedia/clear/hevc/30fps/llama/llama_hevc_480p_30fps_3000.mp4"));
            player.setPlayWhenReady(true);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.prepare();
            playerView.setPlayer(player);
            playerView.onResume();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        for (PlayerView playerView: playerViewList) {
            playerView.onPause();
            ExoPlayer player = (ExoPlayer) playerView.getPlayer();
            if (player != null) {
                player.release();
                playerView.setPlayer(null);
            }
        }
    }
}