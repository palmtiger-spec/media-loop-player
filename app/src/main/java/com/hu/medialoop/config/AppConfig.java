package com.hu.medialoop.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.hu.medialoop.model.SchedulePlan;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 表驱动配置中心。所有需求参数读写集中于此，验收时改配置无需改代码。
 * 存储：SharedPreferences（低端机无数据库开销）。
 */
public final class AppConfig {
    private static final String PREF = "medialoop_config";
    private static AppConfig sInstance;
    private final SharedPreferences sp;

    private AppConfig(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized AppConfig get(Context ctx) {
        if (sInstance == null) sInstance = new AppConfig(ctx);
        return sInstance;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int i(String key, int def) { return sp.getInt(key, def); }
    private void putI(String key, int v) { sp.edit().putInt(key, v).apply(); }
    private long l(String key, long def) { return sp.getLong(key, def); }
    private void putL(String key, long v) { sp.edit().putLong(key, v).apply(); }
    private String s(String key, String def) { return sp.getString(key, def); }
    private void putS(String key, String v) { sp.edit().putString(key, v).apply(); }
    private boolean b(String key, boolean def) { return sp.getBoolean(key, def); }
    private void putB(String key, boolean v) { sp.edit().putBoolean(key, v).apply(); }

    /* ==================== 播放模块 ==================== */
    public int getLoopMode() { return i("loop_mode", Constants.LOOP_SEQUENTIAL); }
    public void setLoopMode(int v) { putI("loop_mode", v); }

    public int getTransition() { return i("transition", Constants.TRANSITION_FADE); }
    public void setTransition(int v) { putI("transition", v); }

    public int getTransitionMs() {
        return clamp(i("transition_ms", Constants.DEFAULT_TRANSITION_MS),
                Constants.MIN_TRANSITION_MS, Constants.MAX_TRANSITION_MS);
    }
    public void setTransitionMs(int ms) {
        putI("transition_ms", clamp(ms, Constants.MIN_TRANSITION_MS, Constants.MAX_TRANSITION_MS));
    }

    public int getImageDurationMs() {
        return clamp(i("image_duration_ms", Constants.DEFAULT_IMAGE_DURATION_MS), 500, 600000);
    }
    public void setImageDurationMs(int ms) { putI("image_duration_ms", clamp(ms, 500, 600000)); }

    public int getBgmMode() { return i("bgm_mode", Constants.BGM_PER_IMAGE); }
    public void setBgmMode(int v) { putI("bgm_mode", v); }

    /** 固定随机种子便于验收：默认 -1（系统时间），验收时可设固定值使随机序列可复现 */
    public long getRandomSeed() { return l("random_seed", -1L); }
    public void setRandomSeed(long seed) { putL("random_seed", seed); }

    public int getMaxFileMb() { return clamp(i("max_file_mb", Constants.DEFAULT_MAX_FILE_MB), 1, 4096); }
    public void setMaxFileMb(int v) { putI("max_file_mb", clamp(v, 1, 4096)); }

    public int getMaxImageWidth() { return clamp(i("max_img_w", Constants.DEFAULT_MAX_IMAGE_W), 320, 7680); }
    public void setMaxImageWidth(int v) { putI("max_img_w", clamp(v, 320, 7680)); }
    public int getMaxImageHeight() { return clamp(i("max_img_h", Constants.DEFAULT_MAX_IMAGE_H), 240, 4320); }
    public void setMaxImageHeight(int v) { putI("max_img_h", clamp(v, 240, 4320)); }

    /* ==================== 拼接模板模块 ==================== */
    /** 模板索引 0–4，对应 T1–T5 */
    public int getTemplateIndex() { return clamp(i("template_index", 0), 0, Constants.MAX_TEMPLATES - 1); }
    public void setTemplateIndex(int idx) { putI("template_index", clamp(idx, 0, Constants.MAX_TEMPLATES - 1)); }

    public int getCollageTrigger() { return i("collage_trigger", Constants.TRIGGER_INTERVAL); }
    public void setCollageTrigger(int v) { putI("collage_trigger", v); }

    public int getCollageIntervalSec() { return clamp(i("collage_interval_sec", 10), 1, 3600); }
    public void setCollageIntervalSec(int v) { putI("collage_interval_sec", clamp(v, 1, 3600)); }

    public int getCollageTransition() { return i("collage_transition", Constants.TRANSITION_FADE); }
    public void setCollageTransition(int v) { putI("collage_transition", v); }

    /* ==================== 定时模块 ==================== */
    public List<SchedulePlan> getPlans() {
        List<SchedulePlan> out = new ArrayList<>();
        String raw = s("plans", "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                out.add(new SchedulePlan(
                        o.optString("start", "08:00"),
                        o.optString("end", "22:00"),
                        o.optString("repeat", Constants.REPEAT_DAILY)));
            }
        } catch (JSONException e) {
            // 配置损坏时回退为空计划并记录（由调用方记录日志）
        }
        return out;
    }

    public void setPlans(List<SchedulePlan> plans) {
        JSONArray arr = new JSONArray();
        if (plans != null) {
            for (SchedulePlan p : plans) {
                JSONObject o = new JSONObject();
                try {
                    o.put("start", p.start);
                    o.put("end", p.end);
                    o.put("repeat", p.repeat);
                    arr.put(o);
                } catch (JSONException ignored) { }
            }
        }
        putS("plans", arr.toString());
    }

    /** 断电恢复：最后播放索引与进度 */
    public int getLastIndex() { return i("last_index", 0); }
    public void setLastIndex(int v) { putI("last_index", v); }
    public long getLastPositionMs() { return l("last_position_ms", 0L); }
    public void setLastPositionMs(long v) { putL("last_position_ms", v); }
    public boolean getLastPlaying() { return b("last_playing", false); }
    public void setLastPlaying(boolean v) { putB("last_playing", v); }

    /* ==================== 网络资源模块 ==================== */
    public List<String> getNetworkUrls() {
        List<String> out = new ArrayList<>();
        String raw = s("network_urls", "");
        if (!TextUtils.isEmpty(raw)) {
            for (String u : raw.split("\\n")) {
                if (!TextUtils.isEmpty(u.trim())) out.add(u.trim());
            }
        }
        return out;
    }
    public void setNetworkUrls(List<String> urls) {
        StringBuilder sb = new StringBuilder();
        if (urls != null) {
            for (String u : urls) {
                if (TextUtils.isEmpty(u)) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(u.trim());
            }
        }
        putS("network_urls", sb.toString());
    }

    public int getNetTimeoutSec() {
        return clamp(i("net_timeout_sec", Constants.DEFAULT_NET_TIMEOUT_SEC),
                Constants.MIN_NET_TIMEOUT_SEC, Constants.MAX_NET_TIMEOUT_SEC);
    }
    public void setNetTimeoutSec(int v) {
        putI("net_timeout_sec", clamp(v, Constants.MIN_NET_TIMEOUT_SEC, Constants.MAX_NET_TIMEOUT_SEC));
    }
    public int getNetRetry() { return clamp(i("net_retry", Constants.DEFAULT_NET_RETRY), 0, Constants.MAX_NET_RETRY); }
    public void setNetRetry(int v) { putI("net_retry", clamp(v, 0, Constants.MAX_NET_RETRY)); }
    public int getRetryIntervalSec() { return clamp(i("retry_interval_sec", Constants.DEFAULT_RETRY_INTERVAL_SEC), 0, 300); }
    public void setRetryIntervalSec(int v) { putI("retry_interval_sec", clamp(v, 0, 300)); }
    public int getCacheMb() { return clamp(i("cache_mb", Constants.DEFAULT_CACHE_MB), 1, 4096); }
    public void setCacheMb(int v) { putI("cache_mb", clamp(v, 1, 4096)); }
    public String getEviction() {
        String v = s("eviction", Constants.EVICTION_LRU);
        return Constants.EVICTION_FIFO.equals(v) ? v : Constants.EVICTION_LRU;
    }
    public void setEviction(String v) {
        putS("eviction", Constants.EVICTION_FIFO.equals(v) ? v : Constants.EVICTION_LRU);
    }

    /** 命中率统计接口：命中率 = 命中次数 / 总请求次数 × 100% */
    public void incrementCacheHit() { putL("cache_hit", l("cache_hit", 0) + 1); }
    public void incrementCacheMiss() { putL("cache_miss", l("cache_miss", 0) + 1); }
    public long getCacheHit() { return l("cache_hit", 0); }
    public long getCacheMiss() { return l("cache_miss", 0); }
    public float getCacheHitRate() {
        long hit = getCacheHit(), miss = getCacheMiss();
        long total = hit + miss;
        return total == 0 ? 0f : (hit * 100f / total);
    }
    public void resetCacheStats() {
        sp.edit().putLong("cache_hit", 0).putLong("cache_miss", 0).apply();
    }

    /* ==================== 安全模块 ==================== */
    public String getPasswordHash() { return s("pwd_hash", ""); }
    public void setPasswordHash(String hash) { putS("pwd_hash", hash); }
    public String getPasswordSalt() { return s("pwd_salt", ""); }
    public void setPasswordSalt(String salt) { putS("pwd_salt", salt); }
    public boolean isPasswordSet() { return !TextUtils.isEmpty(getPasswordHash()); }
    public int getFailCount() { return i("fail_count", 0); }
    public void setFailCount(int v) { putI("fail_count", v); }
    public long getLockStartMs() { return l("lock_start_ms", 0L); }
    public void setLockStartMs(long v) { putL("lock_start_ms", v); }
    public boolean isForceChangePassword() { return b("force_change_pwd", false); }
    public void setForceChangePassword(boolean v) { putB("force_change_pwd", v); }

    /* ==================== 显示模式模块 ==================== */
    public int getDisplayMode() { return i("display_mode", Constants.DISPLAY_FIT); }
    public void setDisplayMode(int v) { putI("display_mode", v); }

    /* ==================== 媒体路径 ==================== */
    public String getMediaDir() { return s("media_dir", "/sdcard/MediaLoop"); }
    public void setMediaDir(String path) { putS("media_dir", path); }
    public String getBgmDir() { return s("bgm_dir", "/sdcard/MediaLoop/bgm"); }
    public void setBgmDir(String path) { putS("bgm_dir", path); }

    /* ==================== 其他 ==================== */
    public boolean isWakeLockEnabled() { return b("wakelock_enabled", false); }
    public void setWakeLockEnabled(boolean v) { putB("wakelock_enabled", v); }
}
