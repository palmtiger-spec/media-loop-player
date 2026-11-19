package com.hu.medialoop.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hu.medialoop.util.AppLogger;

/** 开机/应用更新后恢复：重新注册定时计划；播放位置由 LoopPlayer 从配置中读取 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        AppLogger.i("收到 " + action + "，恢复定时计划与播放位置");
        ScheduleManager sm = new ScheduleManager(ctx);
        sm.refresh();
        sm.broadcastState(sm.shouldPlayNow(), "BOOT_RESTORE");
    }
}
