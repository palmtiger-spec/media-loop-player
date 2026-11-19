package com.hu.medialoop.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.VideoView;

import com.hu.medialoop.R;
import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.model.MediaItem;
import com.hu.medialoop.network.NetworkResourceLoader;
import com.hu.medialoop.player.BgmController;
import com.hu.medialoop.player.CollageView;
import com.hu.medialoop.player.LoopPlayer;
import com.hu.medialoop.player.PlayerView;
import com.hu.medialoop.schedule.ScheduleManager;
import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.MediaScanner;
import com.hu.medialoop.util.VerificationRecorder;

import java.util.ArrayList;
import java.util.List;

/** 播放界面：普通循环播放 + 拼接模板模式，接收定时状态广播 */
public class PlayerActivity extends Activity implements LoopPlayer.Callback {
    private AppConfig cfg;
    private PlayerView playerView;
    private VideoView videoView;
    private CollageView collageView;
    private TextView tvStatus;
    private LoopPlayer player;
    private NetworkResourceLoader netLoader;
    private BgmController bgm;
    private List<MediaItem> playlist;
    private PowerManager.WakeLock wakeLock;
    private boolean collageMode;
    private boolean receiverRegistered;

    private final BroadcastReceiver scheduleReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            boolean playing = intent.getBooleanExtra(ScheduleManager.EXTRA_PLAYING, true);
            String reason = intent.getStringExtra(ScheduleManager.EXTRA_REASON);
            if (player == null) return;
            if (playing) player.resumeBySchedule();
            else player.pauseBySchedule();
            tvStatus.setText("定时状态: " + reason + " -> " + (playing ? "播放" : "暂停"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = (PlayerView) findViewById(R.id.player_view);
        videoView = (VideoView) findViewById(R.id.video_view);
        collageView = (CollageView) findViewById(R.id.collage_view);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        cfg = AppConfig.get(this);

        netLoader = new NetworkResourceLoader(this);
        MediaScanner scanner = new MediaScanner(cfg);
        playlist = scanner.buildPlaylist(cfg.getMediaDir(), cfg.getNetworkUrls());

        player = new LoopPlayer(this, this, netLoader,
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        bgm = new BgmController(this, scanner.scanBgm(cfg.getBgmDir()));
        player.setBgm(bgm);
        player.setPlaylist(playlist);

        IntentFilter filter = new IntentFilter(ScheduleManager.ACTION_SCHEDULE_CHANGED);
        registerReceiver(scheduleReceiver, filter);
        receiverRegistered = true;

        acquireWakeLockIfNeeded();

        ((Button) findViewById(R.id.btn_collage)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCollageMode(); }
        });
        ((Button) findViewById(R.id.btn_next)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (collageMode) collageView.manualNext();
                else player.nextManually();
            }
        });
        ((Button) findViewById(R.id.btn_exit)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        player.start();
        ScheduleManager sm = new ScheduleManager(this);
        if (!sm.shouldPlayNow()) {
            player.pauseBySchedule();
            tvStatus.setText("当前不在播放计划区间内，已暂停");
        }
        if (playlist.isEmpty()) {
            tvStatus.setText("无可播放文件：请将图片/视频放入 " + cfg.getMediaDir()
                    + "，或在设置中添加网络资源 URL");
        }
    }

    /* ==================== LoopPlayer.Callback ==================== */
    @Override public void onShowImage(String source, Bitmap bitmap, boolean placeholder) {
        if (collageMode) return;
        videoView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        playerView.setDisplayMode(cfg.getDisplayMode());
        playerView.show(bitmap, cfg.getTransition(), cfg.getTransitionMs());
        tvStatus.setText((placeholder ? "占位图(降级) " : "图片 ") + source);
    }

    @Override public void onShowVideo(String source, final long startPositionMs) {
        if (collageMode) return;
        playerView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoPath(source);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                if (startPositionMs > 0) {
                    try { mp.seekTo((int) startPositionMs); } catch (Exception ignored) { }
                }
                mp.start();
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) { player.onVideoCompleted(); }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                AppLogger.e("视频解码失败，降级到下一项: " + source);
                VerificationRecorder.recordFallback("VIDEO_DECODE_FAIL", source);
                player.saveVideoPosition(0);
                player.onVideoCompleted();
                return true;
            }
        });
        tvStatus.setText("视频 " + source);
    }

    @Override public void onPlaybackStateChanged(boolean playing) {
        // 状态已由定时接收器显示，这里不覆盖
    }

    /* ==================== 拼接模板模式 ==================== */
    private void toggleCollageMode() {
        Button btn = (Button) findViewById(R.id.btn_collage);
        if (!collageMode) {
            collageMode = true;
            player.stop(); // 保存位置，退出后从此处继续
            playerView.setVisibility(View.GONE);
            videoView.setVisibility(View.GONE);
            collageView.setVisibility(View.VISIBLE);
            collageView.switchTemplate(cfg.getTemplateIndex(), Constants.TRIGGER_MANUAL);
            loadCollageImages();
            if (cfg.getCollageTrigger() == Constants.TRIGGER_INTERVAL) {
                collageView.startAutoSwitch(cfg.getCollageIntervalSec());
            } else {
                collageView.stopAutoSwitch();
            }
            btn.setText("返回播放");
            tvStatus.setText("拼接模式 T" + (cfg.getTemplateIndex() + 1));
        } else {
            collageMode = false;
            collageView.stopAutoSwitch();
            collageView.setVisibility(View.GONE);
            btn.setText("拼接模式");
            player.start(); // 从上次位置继续
        }
    }

    /** 取列表前 4 张图片（模板最多 4 区），后台采样解码 */
    private void loadCollageImages() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Bitmap> imgs = new ArrayList<>();
                for (MediaItem it : playlist) {
                    if (imgs.size() >= Constants.MAX_COLLAGE_REGIONS) break;
                    if (it.type != MediaItem.Type.IMAGE || it.placeholder) continue;
                    String p = it.path;
                    if (p.startsWith("http://") || p.startsWith("https://")) {
                        p = netLoader.resolve(p);
                    }
                    if (p == null) continue;
                    Bitmap b = decodeSmall(p);
                    if (b != null) imgs.add(b);
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() { collageView.setImages(imgs); }
                });
            }
        }, "collage-decode").start();
    }

    private Bitmap decodeSmall(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / sample > 640 || o.outHeight / sample > 360) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, o);
        } catch (Exception e) {
            AppLogger.e("拼接图片解码失败: " + path + " " + e.getMessage());
            VerificationRecorder.recordFallback("COLLAGE_DECODE_FAIL", path);
            return null;
        }
    }

    /* ==================== 生命周期 ==================== */
    private void acquireWakeLockIfNeeded() {
        if (!cfg.isWakeLockEnabled()) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MediaLoop:Playback");
        wakeLock.acquire();
    }

    @Override protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            try { player.saveVideoPosition(videoView.getCurrentPosition()); } catch (Exception ignored) { }
            videoView.pause();
        }
        if (bgm != null) bgm.pause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (bgm != null) bgm.resume();
        if (videoView != null && !collageMode && videoView.getVisibility() == View.VISIBLE) {
            try { videoView.start(); } catch (Exception ignored) { }
        }
    }

    @Override protected void onDestroy() {
        if (player != null) player.stop();
        if (bgm != null) bgm.release();
        if (receiverRegistered) unregisterReceiver(scheduleReceiver);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
