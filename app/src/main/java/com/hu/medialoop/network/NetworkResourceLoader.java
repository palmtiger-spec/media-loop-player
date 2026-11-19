package com.hu.medialoop.network;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.player.LoopPlayer;
import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.VerificationRecorder;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 网络资源加载器（LoopPlayer.PathResolver 实现）。
 * 顺序：缓存命中直接用本地 → 未命中发起请求（超时/重试/间隔可配）→ 失败降级为 null（由播放层显示占位图）。
 * 重试次数、间隔、超时均读配置，验收修改配置后行为随之变化。
 */
public final class NetworkResourceLoader implements LoopPlayer.PathResolver {
    private final Context ctx;
    private final AppConfig cfg;
    private final LruDiskCache cache;
    private final ConnectivityManager cm;

    public NetworkResourceLoader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.cfg = AppConfig.get(ctx);
        this.cache = new LruDiskCache(ctx, cfg.getCacheMb(), cfg.getEviction());
        this.cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public LruDiskCache getCache() { return cache; }

    @Override
    public String resolve(String source) {
        if (source == null || (!source.startsWith("http://") && !source.startsWith("https://"))) {
            return source; // 本地路径直接返回
        }

        // 1. 缓存命中优先
        File cached = cache.get(source);
        if (cached != null) {
            cfg.incrementCacheHit();
            VerificationRecorder.recordNetworkResult(source, true, true, Constants.LOAD_SUCCESS);
            AppLogger.i("缓存命中: " + source);
            return cached.getAbsolutePath();
        }
        cfg.incrementCacheMiss();

        // 2. 网络权限与连通性检查（权限缺失也算异常路径）
        if (ctx.checkSelfPermission(android.Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            AppLogger.e("网络权限缺失，降级为占位图: " + source);
            VerificationRecorder.recordFallback("NET_PERMISSION_DENIED", source);
            VerificationRecorder.recordNetworkResult(source, false, false, Constants.LOAD_FALLBACK);
            return null;
        }
        if (!networkAvailable()) {
            AppLogger.i("无网络连接，降级为占位图: " + source);
            VerificationRecorder.recordFallback("NO_NETWORK", source);
            VerificationRecorder.recordNetworkResult(source, false, false, Constants.LOAD_FALLBACK);
            return null;
        }

        // 3. 按配置重试（最多 retry 次，间隔固定；超时时间可配）
        int maxRetry = cfg.getNetRetry();
        int timeoutMs = cfg.getNetTimeoutSec() * 1000;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            VerificationRecorder.recordNetworkAttempt(source, attempt);
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(source).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setUseCaches(false);
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    InputStream in = conn.getInputStream();
                    File saved = cache.put(source, in);
                    in.close();
                    if (saved != null) {
                        AppLogger.i("下载成功并缓存: " + source);
                        VerificationRecorder.recordNetworkResult(source, true, false, Constants.LOAD_SUCCESS);
                        return saved.getAbsolutePath();
                    }
                    AppLogger.e("写入缓存失败: " + source);
                } else {
                    AppLogger.w("HTTP " + code + ": " + source);
                }
            } catch (Exception e) {
                AppLogger.w("请求失败(第" + (attempt + 1) + "次): " + source + " " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
            if (attempt < maxRetry) {
                try {
                    Thread.sleep((long) cfg.getRetryIntervalSec() * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 4. 全部失败 → 降级
        AppLogger.e("网络重试耗尽，降级为占位图: " + source);
        VerificationRecorder.recordFallback("NETWORK_RETRY_EXHAUSTED", source);
        VerificationRecorder.recordNetworkResult(source, false, false, Constants.LOAD_FALLBACK);
        return null;
    }

    private boolean networkAvailable() {
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }
}
