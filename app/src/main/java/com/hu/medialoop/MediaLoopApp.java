package com.hu.medialoop;

import android.app.Application;

import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.VerificationRecorder;

/** 应用入口：统一初始化日志与验收记录器 */
public class MediaLoopApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.init(this);
        VerificationRecorder.init(this);
        AppLogger.i("应用启动 / 日志与验收记录器就绪");
    }
}
