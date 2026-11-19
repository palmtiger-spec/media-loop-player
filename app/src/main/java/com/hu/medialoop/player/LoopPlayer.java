package com.hu.medialoop.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.model.MediaItem;
import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.VerificationRecorder;

import java.util.List;
import java.util.Random;

/**
 * 播放引擎：顺序/随机循环、图片异步解码（采样降内存）、图片停留计时、异常降级、断电续播。
 * 视图层通过 Callback 接收渲染指令，便于拼接/普通播放两种界面复用。
 */
public final class LoopPlayer {
    /** 把来源（本地路径或网络 URL）解析为可解码的本地路径；返回 null 表示解析失败 */
    public interface PathResolver {
        String resolve(String source);
    }

    public interface Callback {
        void onShowImage(String source, Bitmap bitmap, boolean placeholder);
        void onShowVideo(String source, long startPositionMs);
        void onPlaybackStateChanged(boolean playing);
    }

    private final Context ctx;
    private final AppConfig cfg;
    private final Callback cb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PathResolver resolver;
    private final int targetW, targetH;

    private List<MediaItem> playlist;
    private int currentIndex = 0;
    private Random random;
    private boolean running;
    private boolean pausedBySchedule;
    private BgmController bgm;

    public LoopPlayer(Context ctx, Callback cb, PathResolver resolver, int targetW, int targetH) {
        this.ctx = ctx.getApplicationContext();
        this.cfg = AppConfig.get(ctx);
        this.cb = cb;
        this.resolver = resolver;
        this.targetW = targetW > 0 ? targetW : 1280;
        this.targetH = targetH > 0 ? targetH : 720;
        initRandom();
    }

    private void initRandom() {
        long seed = cfg.getRandomSeed();
        random = new Random(seed >= 0 ? seed : System.currentTimeMillis());
    }

    public void setBgm(BgmController bgm) { this.bgm = bgm; }
    public void setPlaylist(List<MediaItem> list) { this.playlist = list; }
    public int getCurrentIndex() { return currentIndex; }
    public boolean isRunning() { return running; }

    /** 启动播放：从上次位置继续（断电恢复） */
    public void start() {
        if (playlist == null || playlist.isEmpty()) {
            AppLogger.e("播放列表为空");
            cb.onShowImage("", null, true);
            return;
        }
        running = true;
        pausedBySchedule = false;
        initRandom();
        int saved = cfg.getLastIndex();
        currentIndex = (saved >= 0 && saved < playlist.size()) ? saved : 0;
        if (bgm != null) bgm.start();
        cb.onPlaybackStateChanged(true);
        playCurrent(cfg.getLastPlaying() && currentIndex == saved);
    }

    /** 停止并保存播放位置，供下次/断电后恢复 */
    public void stop() {
        running = false;
        main.removeCallbacks(advanceRunnable);
        cfg.setLastIndex(currentIndex);
        cfg.setLastPlaying(!pausedBySchedule);
        if (bgm != null) bgm.stop();
        cb.onPlaybackStateChanged(false);
    }

    /** 定时模块调用：区间外暂停 */
    public void pauseBySchedule() {
        if (!running || pausedBySchedule) return;
        pausedBySchedule = true;
        main.removeCallbacks(advanceRunnable);
        if (bgm != null) bgm.pause();
        cb.onPlaybackStateChanged(false);
        VerificationRecorder.recordScheduleState(false, "SCHEDULE_OUT");
    }

    /** 定时模块调用：区间内恢复 */
    public void resumeBySchedule() {
        if (!running || !pausedBySchedule) return;
        pausedBySchedule = false;
        if (bgm != null) bgm.resume();
        cb.onPlaybackStateChanged(true);
        VerificationRecorder.recordScheduleState(true, "SCHEDULE_IN");
        playCurrent(false);
    }

    public void saveVideoPosition(long posMs) { cfg.setLastPositionMs(posMs); }

    /** 视频播放完成回调 */
    public void onVideoCompleted() {
        if (!running || pausedBySchedule) return;
        cfg.setLastPositionMs(0);
        advance();
    }

    /** 手动触发下一项（拼接手动切换或调试） */
    public void nextManually() {
        if (!running || playlist == null || playlist.isEmpty()) return;
        advance();
    }

    private void playCurrent(boolean restoreVideoPosition) {
        if (!running || playlist == null || playlist.isEmpty()) return;
        if (currentIndex < 0 || currentIndex >= playlist.size()) currentIndex = 0;
        final MediaItem item = playlist.get(currentIndex);
        cfg.setLastIndex(currentIndex);
        VerificationRecorder.recordPlay(item.path, currentIndex, cfg.getLoopMode());

        if (item.placeholder) {
            AppLogger.w("占位图项，跳过解码");
            cb.onShowImage(item.path, null, true);
            scheduleNext(cfg.getImageDurationMs());
            return;
        }

        if (item.type == MediaItem.Type.VIDEO) {
            // VideoView 可直接播放 http(s) 视频；本地视频走原路径
            cfg.setLastPositionMs(restoreVideoPosition ? cfg.getLastPositionMs() : 0);
            cb.onShowVideo(item.path, cfg.getLastPositionMs());
        } else {
            // 网络图片的下载/缓存解析放到解码线程，避免阻塞 UI
            decodeAsync(item, item.path);
        }
    }

    private static boolean isNetworkSource(String path) {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    /** 后台线程：先解析网络资源（下载/缓存），再采样解码；RGB_565 控制内存峰值 */
    private void decodeAsync(final MediaItem item, final String source) {
        new Thread(new Runnable() {
            @Override public void run() {
                String local = source;
                if (resolver != null && isNetworkSource(source)) {
                    local = resolver.resolve(source); // 可能重试+等待，必须在后台线程
                    if (local == null) {
                        main.post(new Runnable() {
                            @Override public void run() {
                                if (!running) return;
                                AppLogger.e("网络资源降级为占位图: " + item.path);
                                VerificationRecorder.recordFallback("NETWORK_FALLBACK", item.path);
                                cb.onShowImage(item.path, null, true);
                                scheduleNext(cfg.getImageDurationMs());
                            }
                        });
                        return;
                    }
                }
                final Bitmap bmp = decodeSampled(local);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (!running) return;
                        if (bmp == null) {
                            AppLogger.e("解码失败，显示占位图并降级到下一项: " + item.path);
                            VerificationRecorder.recordFallback("DECODE_FAIL", item.path);
                            cb.onShowImage(item.path, null, true);
                        } else {
                            cb.onShowImage(item.path, bmp, false);
                            if (bgm != null) bgm.onImageShown();
                        }
                        scheduleNext(cfg.getImageDurationMs());
                    }
                });
            }
        }, "decode-worker").start();
    }

    private Bitmap decodeSampled(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            // 按显示尺寸的 2 倍留余量，避免放大模糊同时控制内存
            while (o.outWidth / sample > targetW * 2 || o.outHeight / sample > targetH * 2) {
                sample *= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565; // 比 ARGB_8888 省一半内存
            return BitmapFactory.decodeFile(path, o);
        } catch (OutOfMemoryError e) {
            AppLogger.e("解码 OOM: " + path);
            VerificationRecorder.recordFallback("OOM", path);
            return null;
        } catch (Exception e) {
            AppLogger.e("解码异常: " + path + " " + e.getMessage());
            return null;
        }
    }

    private void scheduleNext(long delayMs) {
        main.removeCallbacks(advanceRunnable);
        if (running && !pausedBySchedule) main.postDelayed(advanceRunnable, delayMs);
    }

    private final Runnable advanceRunnable = new Runnable() {
        @Override public void run() { advance(); }
    };

    private void advance() {
        if (!running || playlist == null || playlist.isEmpty()) return;
        int size = playlist.size();
        if (cfg.getLoopMode() == Constants.LOOP_RANDOM) {
            int next = random.nextInt(size);
            // 避免连续重复（多文件时）
            if (size > 1 && next == currentIndex) next = random.nextInt(size);
            currentIndex = next;
        } else {
            currentIndex = (currentIndex + 1) % size;
        }
        playCurrent(false);
    }
}
