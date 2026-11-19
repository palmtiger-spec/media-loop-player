package com.hu.medialoop.player;

import android.content.Context;
import android.media.MediaPlayer;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.model.MediaItem;
import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.VerificationRecorder;

import java.util.ArrayList;
import java.util.List;

/**
 * 背景音乐控制器。
 * 规则 A「每张图片切换」(BGM_PER_IMAGE)：每次显示新图片时切下一首，单曲循环直到下次切换。
 * 规则 B「按列表顺序」(BGM_BY_LIST)：按 BGM 列表顺序连续播放，一首结束自动下一首，与图片切换解耦。
 */
public final class BgmController {
    private final Context ctx;
    private final List<String> paths = new ArrayList<>();
    private final int mode;
    private int index = 0;
    private MediaPlayer player;
    private boolean started;

    public BgmController(Context ctx, List<MediaItem> bgmItems) {
        this.ctx = ctx.getApplicationContext();
        this.mode = AppConfig.get(ctx).getBgmMode();
        if (bgmItems != null) {
            for (MediaItem it : bgmItems) paths.add(it.path);
        }
    }

    public boolean hasMusic() { return !paths.isEmpty(); }

    public void start() {
        started = true;
        if (!hasMusic()) return;
        playAt(0);
    }

    /** 图片显示时调用：仅规则 A 触发切歌 */
    public void onImageShown() {
        if (!started || !hasMusic() || mode != Constants.BGM_PER_IMAGE) return;
        next();
    }

    /** 按列表顺序播放时，一首结束自动推进 */
    private void playAt(int i) {
        if (paths.isEmpty()) return;
        releasePlayer();
        index = ((i % paths.size()) + paths.size()) % paths.size();
        final String path = paths.get(index);
        player = new MediaPlayer();
        try {
            player.setDataSource(path);
            player.setLooping(mode == Constants.BGM_PER_IMAGE);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    if (mode == Constants.BGM_BY_LIST) next();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    AppLogger.e("背景音乐解码失败，跳过: " + path);
                    VerificationRecorder.recordFallback("BGM_DECODE_FAIL", path);
                    next();
                    return true;
                }
            });
            player.prepare();
            player.start();
            VerificationRecorder.recordBgmSwitch(path, mode);
        } catch (Exception e) {
            AppLogger.e("背景音乐打开失败: " + path + " " + e.getMessage());
            VerificationRecorder.recordFallback("BGM_OPEN_FAIL", path);
            releasePlayer();
        }
    }

    private void next() {
        if (paths.isEmpty()) return;
        playAt(index + 1);
    }

    public void pause() { if (player != null && player.isPlaying()) player.pause(); }
    public void resume() { if (player != null && !player.isPlaying()) player.start(); }

    public void stop() {
        started = false;
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    public void release() { stop(); }
}
