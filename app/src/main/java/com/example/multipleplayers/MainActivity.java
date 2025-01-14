package com.example.multipleplayers;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.VideoView;

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

public class MainActivity extends AppCompatActivity {
    private GridLayout mLayout;
    private final Uri VIDEO_URI = Uri.parse("https://storage.googleapis.com/wvmedia/clear/hevc/30fps/llama/llama_hevc_480p_30fps_3000.mp4");
    private final int NUM_PLAYERS = 9;
    private final boolean USE_VIDEO_VIEW = false; // For comparison (ExoPlayer performs better)

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Arrange players in a N x N grid (assume videos and display have the same aspect ratio)
        mLayout = new GridLayout(this);
        int NUM_COLUMNS = (int) Math.ceil(Math.sqrt(NUM_PLAYERS));
        mLayout.setColumnCount(NUM_COLUMNS);
        for (int i = 0; i < NUM_PLAYERS; i++) {
            View view;
            if (USE_VIDEO_VIEW) {
                view = new VideoView(this);
            } else {
                PlayerView playerView = new PlayerView(this);
                playerView.setUseController(false);
                view = playerView;
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.height = 0;
            params.width = 0;
            view.setLayoutParams(params);
            mLayout.addView(view);
        }
        setContentView(mLayout);
        setTitle(NUM_PLAYERS + " players using " +
                (USE_VIDEO_VIEW ? "VideoView" : MediaLibraryInfo.VERSION_SLASHY));
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onStart() {
        super.onStart();
        DefaultAllocator allocator = new DefaultAllocator(false, C.DEFAULT_BUFFER_SEGMENT_SIZE);
        for (int i = 0; i < NUM_PLAYERS; i++) {
            View view = mLayout.getChildAt(i);
            if (USE_VIDEO_VIEW) {
                VideoView videoView = (VideoView) view;
                videoView.setOnCompletionListener(MediaPlayer::start);
                videoView.setVideoURI(VIDEO_URI);
                videoView.start();
            } else {
                PlayerView playerView = (PlayerView) view;
                ExoPlayer player = new ExoPlayer.Builder(this)
                        .setLoadControl(new DefaultLoadControl.Builder()
                                .setAllocator(allocator)
                                .build())
                        .build();
                player.setMediaItem(MediaItem.fromUri(VIDEO_URI));
                player.setPlayWhenReady(true);
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                player.prepare();
                playerView.setPlayer(player);
                playerView.onResume();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            View view = mLayout.getChildAt(i);
            if (USE_VIDEO_VIEW) {
                VideoView videoView = (VideoView) view;
                videoView.stopPlayback();
            } else {
                PlayerView playerView = (PlayerView) view;
                playerView.onPause();
                ExoPlayer player = (ExoPlayer) playerView.getPlayer();
                if (player != null) {
                    player.release();
                    playerView.setPlayer(null);
                }
            }
        }
    }
}