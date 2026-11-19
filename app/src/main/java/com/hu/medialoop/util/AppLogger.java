package com.hu.medialoop.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 运行日志：Logcat + 外部文件双写，供验收查看异常路径是否被触发 */
public final class AppLogger {
    private static final String TAG = "MediaLoop";
    private static final int MAX_MEM_LINES = 300;
    private static final List<String> MEM = Collections.synchronizedList(new ArrayList<String>());
    private static File logFile;
    private static final SimpleDateFormat DF = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA);

    private AppLogger() {}

    public static void init(Context ctx) {
        File dir = ctx.getApplicationContext().getExternalFilesDir(null);
        if (dir == null) dir = ctx.getApplicationContext().getFilesDir();
        logFile = new File(dir, "run.log");
    }

    public static void i(String msg) { write("I", msg); }
    public static void w(String msg) { write("W", msg); }
    public static void e(String msg) { write("E", msg); }

    private static void write(String level, String msg) {
        String line = DF.format(new Date()) + " [" + level + "] " + msg;
        Log.i(TAG, line);
        MEM.add(line);
        if (MEM.size() > MAX_MEM_LINES) MEM.remove(0);
        if (logFile == null) return;
        try {
            FileOutputStream fos = new FileOutputStream(logFile, true);
            fos.write((line + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (IOException ignored) { }
    }

    public static List<String> recentLines() {
        synchronized (MEM) { return new ArrayList<>(MEM); }
    }

    public static File getLogFile() { return logFile; }
}
