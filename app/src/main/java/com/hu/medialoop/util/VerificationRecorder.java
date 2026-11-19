package com.hu.medialoop.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 验收数据记录器：把"可验证"的验收标准落成可读取的时间戳/序列日志。
 * 输出文件 verification.log，位于应用外部文件目录，可用 adb pull 导出核对。
 */
public final class VerificationRecorder {
    private static File file;
    private static long startMs = System.currentTimeMillis();
    private static final SimpleDateFormat DF = new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA);

    private VerificationRecorder() {}

    public static void init(Context ctx) {
        File dir = ctx.getApplicationContext().getExternalFilesDir(null);
        if (dir == null) dir = ctx.getApplicationContext().getFilesDir();
        file = new File(dir, "verification.log");
        startMs = System.currentTimeMillis();
    }

    /** event 为 ASCII 键，details 为参数；格式：相对ms | 时刻 | 事件 | 详情 */
    private static void rec(String event, String details) {
        long now = System.currentTimeMillis();
        String line = (now - startMs) + " | " + DF.format(new Date(now)) + " | " + event + " | " + details;
        AppLogger.i("VERIFY " + line);
        if (file == null) return;
        try {
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write((line + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (IOException ignored) { }
    }

    /** 播放模块：连续播放序列（验收顺序/随机规则） */
    public static void recordPlay(String path, int index, int loopMode) {
        rec("PLAY", "mode=" + loopMode + " index=" + index + " path=" + path);
    }

    /** 播放模块：转场起止（验收转场效果与时长） */
    public static void recordTransitionStart(int type, int configuredMs) {
        rec("TRANS_START", "type=" + type + " configuredMs=" + configuredMs);
    }
    public static void recordTransitionEnd(int type) { rec("TRANS_END", "type=" + type); }

    /** 背景音乐切换（验收切换规则） */
    public static void recordBgmSwitch(String path, int mode) {
        rec("BGM_SWITCH", "mode=" + mode + " path=" + path);
    }

    /** 拼接模板切换（验收触发条件与过渡） */
    public static void recordCollageSwitch(int trigger, int templateIndex) {
        rec("COLLAGE_SWITCH", "trigger=" + trigger + " template=T" + (templateIndex + 1));
    }

    /** 定时模块：播放状态翻转（验收时间计划与冲突） */
    public static void recordScheduleState(boolean playing, String reason) {
        rec("SCHEDULE", "playing=" + playing + " reason=" + reason);
    }

    /** 网络模块：每次请求/重试（验收重试次数与间隔） */
    public static void recordNetworkAttempt(String url, int attempt) {
        rec("NET_ATTEMPT", "attempt=" + attempt + " url=" + url);
    }
    public static void recordNetworkResult(String url, boolean success, boolean cacheHit, int state) {
        rec("NET_RESULT", "success=" + success + " cacheHit=" + cacheHit + " state=" + state + " url=" + url);
    }

    /** 安全模块：认证尝试（验收锁定规则） */
    public static void recordAuth(boolean success, int failCount, boolean locked) {
        rec("AUTH", "success=" + success + " failCount=" + failCount + " locked=" + locked);
    }

    /** 异常路径：文件损坏/格式不支持/解码失败/降级等 */
    public static void recordFallback(String reason, String path) {
        rec("FALLBACK", "reason=" + reason + " path=" + path);
    }

    public static File getFile() { return file; }
}
