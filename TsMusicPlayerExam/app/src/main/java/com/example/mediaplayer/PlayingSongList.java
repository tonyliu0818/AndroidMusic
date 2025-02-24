package com.example.mediaplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;

import java.io.IOException;
import java.util.Locale;

public class PlayingSongList extends AppCompatActivity {
    private ExoPlayer player;
    private String[] songFiles;
    private int currentPosition = 0;
    private boolean isPaused = false;
    private Handler handler = new Handler();
    private SeekBar seekBar;
    private TextView nowSecondText, remainSecondText, title;
    private ImageButton stopButton;
    private ImageView imageView;

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "music_control_channel";

    MediaSessionCompat mediaSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playing_song_list);

        // 設置通知頻道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Control",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // UI 初始化
        title = findViewById(R.id.Title);
        stopButton = findViewById(R.id.stop_button);
        seekBar = findViewById(R.id.seekBar);
        nowSecondText = findViewById(R.id.now_second);
        imageView=findViewById(R.id.imageView);
        remainSecondText = findViewById(R.id.remain_second);

        // 設定 ExoPlayer UI
        PlayerView playerView = findViewById(R.id.playerView);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        mediaSession = new MediaSessionCompat(this, "MusicPlayerSession");
        mediaSession.setActive(true);

        Intent intent = getIntent();
        songFiles = intent.getStringArrayExtra("SongFile");
        currentPosition = intent.getIntExtra("position", 0);

        playSong();
        try {
            createMusicNotification();
        } catch (IOException e) {
            e.printStackTrace();
        }

        stopButton.setOnClickListener(v -> {
            if (player.isPlaying()) {
                player.pause();
                isPaused = true;
                stopButton.setImageResource(R.drawable.ic_play);
            } else {
                player.play();
                isPaused = false;
                stopButton.setImageResource(R.drawable.ic_pause);
            }
            try {
                createMusicNotification(); // 更新通知
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        findViewById(R.id.next_button).setOnClickListener(v -> {
            if (currentPosition < songFiles.length - 1) {
                currentPosition++;
                playSong();
            }
        });

        findViewById(R.id.previous_button).setOnClickListener(v -> {
            if (currentPosition > 0) {
                currentPosition--;
                playSong();
            }
        });

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
            finish();
        });

        //主畫面調整秒數
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    player.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(updateRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                handler.postDelayed(updateRunnable, 500);
            }
        });

        player.addListener(new com.google.android.exoplayer2.Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == com.google.android.exoplayer2.Player.STATE_READY) {
                    long duration = player.getDuration();
                    if (duration != com.google.android.exoplayer2.C.TIME_UNSET) {
                        seekBar.setMax((int) duration);
                        handler.post(updateRunnable);
                    }
                } else if (state == com.google.android.exoplayer2.Player.STATE_ENDED) {
                    // 當歌曲播放完畢時自動切換到下一首
                    if (currentPosition < songFiles.length - 1) {
                        currentPosition++;
                        playSong();
                    } else {
                        // 若已是最後一首，可選擇循環播放或停止
                        currentPosition = 0; // 循環播放從第一首開始
                        playSong();
                    }
                }
            }
        });
    }

    void createMusicNotification() throws IOException {
        if (player == null || songFiles == null || songFiles.length == 0) return;

        String songTitle = songFiles[currentPosition];
        String artistName = getArtistName(songTitle);

        int playPauseIcon = player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play;

        // 建立 PendingIntent 來處理按鈕點擊事件
        PendingIntent playPauseIntent = PendingIntent.getBroadcast(
                this, 0, new Intent("PLAY_PAUSE"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        PendingIntent nextIntent = PendingIntent.getBroadcast(
                this, 1, new Intent("NEXT"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        PendingIntent prevIntent = PendingIntent.getBroadcast(
                this, 2, new Intent("PREVIOUS"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        // 取得專輯封面
        Bitmap albumArt = null;
        try {
            albumArt = getAlbumArtFromAssets(songTitle);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mediaSession == null) {
            mediaSession = new MediaSessionCompat(this, "MusicSession");
        }

        // 設置自訂通知佈局
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification_layout);
        remoteViews.setTextViewText(R.id.notification_title, songTitle);
        remoteViews.setImageViewBitmap(R.id.notification_album_art, albumArt);

        // 設置播放/暫停/上一首/下一首按鈕
        remoteViews.setOnClickPendingIntent(R.id.play_pause_button, playPauseIntent);
        remoteViews.setOnClickPendingIntent(R.id.next_button, nextIntent);
        remoteViews.setOnClickPendingIntent(R.id.previous_button, prevIntent);

        // 設置 SeekBar
        int currentPos = (int) player.getCurrentPosition();
        int duration = (int) player.getDuration();
        remoteViews.setProgressBar(R.id.seekBar, duration, currentPos, false);

        // 设置 SeekBar 拖动事件监听
        Intent seekbarIntent = new Intent("SEEKBAR_UPDATE");
        seekbarIntent.putExtra("seek_position", currentPos);
        PendingIntent seekbarPendingIntent = PendingIntent.getBroadcast(
                this, 3, seekbarIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        remoteViews.setOnClickPendingIntent(R.id.seekBar, seekbarPendingIntent);

        // 設置時間顯示
        remoteViews.setTextViewText(R.id.now_second, formatTime(currentPos));
        remoteViews.setTextViewText(R.id.remain_second, formatTime(duration - currentPos));

        // 設定 Notification Builder
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.music_note)
                .setContentTitle(songTitle)
                .setContentText(artistName)
                .setLargeIcon(albumArt)
                .setCustomBigContentView(remoteViews)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        notificationBuilder.addAction(R.drawable.ic_previous, "Previous", prevIntent)
                .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
                .addAction(R.drawable.ic_next, "Next", nextIntent);

        notificationBuilder.setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));

        // 顯示通知
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    //取得歌手
    private String getArtistName(String fileName) throws IOException {
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            AssetFileDescriptor afd = getAssets().openFd(fileName);
            retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

            // 取得歌手名稱
            String artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (artist != null && !artist.isEmpty()) {
                return artist;
            } else {
                return "未知歌手";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "未知歌手";
        } finally {
            retriever.release();
        }
    }






    private void playSong() {
        String selectedSong = songFiles[currentPosition];
        String songtitle=String.format("%02d %s", currentPosition + 1, selectedSong);
        title.setText(songtitle);

        player.stop();
        player.clearMediaItems();

        try {
            AssetFileDescriptor afd = getAssets().openFd(selectedSong);
            MediaItem mediaItem = new MediaItem.Builder().setUri("asset:///" + selectedSong).build();
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
            isPaused = false;
            stopButton.setImageResource(R.drawable.ic_pause);
            seekBar.setMax((int) player.getDuration());
            handler.post(updateRunnable);
            createMusicNotification();
            Bitmap albumArt;
            try {
                albumArt = getAlbumArtFromAssets(selectedSong);
                imageView.setImageBitmap(albumArt);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap getAlbumArtFromAssets(String fileName) throws IOException {
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            AssetFileDescriptor afd = getAssets().openFd(fileName);
            retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } finally {
            retriever.release();
        }
        return null;
    }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && (player.isPlaying() || isPaused)) {
                long currentPos = player.getCurrentPosition();
                seekBar.setProgress((int) currentPos);
                nowSecondText.setText(formatTime((int) currentPos));
                remainSecondText.setText(formatTime((int) (player.getDuration() - currentPos)));
            }
            handler.postDelayed(this, 1000);
            try {
                createMusicNotification();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private String formatTime(int milliseconds) {
        int minutes = (milliseconds / 1000) / 60;
        int seconds = (milliseconds / 1000) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    // 註冊廣播接收器來處理通知中的動作
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("PLAY_PAUSE".equals(action)) {
                if (player.isPlaying()) {
                    player.pause();
                    stopButton.setImageResource(R.drawable.ic_play);
                } else {
                    player.play();
                    stopButton.setImageResource(R.drawable.ic_pause);
                }
                try {
                    createMusicNotification();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if ("NEXT".equals(action)) {
                if (currentPosition < songFiles.length - 1) {
                    currentPosition++;
                    playSong();
                }
            } else if ("PREVIOUS".equals(action)) {
                if (currentPosition > 0) {
                    currentPosition--;
                    playSong();
                }
            } else if ("SEEKBAR_UPDATE".equals(action)) {
                int seekPosition = intent.getIntExtra("seek_position", 0);
                player.seekTo(seekPosition);
                try {
                    createMusicNotification(); // 更新通知中的 SeekBar
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        // 註冊廣播接收器
        registerReceiver(notificationReceiver, new IntentFilter("PLAY_PAUSE"));
        registerReceiver(notificationReceiver, new IntentFilter("NEXT"));
        registerReceiver(notificationReceiver, new IntentFilter("PREVIOUS"));
        registerReceiver(notificationReceiver, new IntentFilter("SEEKBAR_UPDATE"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 取消註冊廣播接收器
        unregisterReceiver(notificationReceiver);
    }
}
