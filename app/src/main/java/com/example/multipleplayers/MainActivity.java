package com.example.multipleplayers;

import android.app.Activity;
import android.os.Bundle;
import android.widget.GridLayout;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends Activity {
    private static final int NUM_PLAYERS = 9;
    private static final String URI = "https://storage.googleapis.com/wvmedia/cenc/hevc/60fps/llama/llama_hevc_720p_60fps_4000.mp4";
    private static final String L1_LICENSE_URI = "https://proxy.uat.widevine.com/proxy?video_id=GTS_HW_SECURE_ALL&provider=widevine_test";
    private static final String L3_LICENSE_URI = "https://proxy.uat.widevine.com/proxy?video_id=GTS_SW_SECURE_CRYPTO&provider=widevine_test";
    private final String LICENSE_URI = Util.isRunningOnEmulator() ? L3_LICENSE_URI : L1_LICENSE_URI;
    private final PlayerView[] mPlayerViews = new PlayerView[NUM_PLAYERS];

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
    }

    @Override
    public void onStart() {
        super.onStart();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            ExoPlayer player = new ExoPlayer.Builder(this).build();
            player.setMediaItem(new MediaItem.Builder()
                    .setUri(URI)
                    .setDrmConfiguration(
                            new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                    .setLicenseUri(LICENSE_URI)
                                    .build()
                    )
                    .build());
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
    }
}
