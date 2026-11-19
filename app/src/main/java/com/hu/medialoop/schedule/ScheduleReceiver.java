package com.hu.medialoop.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hu.medialoop.config.Constants;

import java.util.Calendar;

/** 定时闹钟接收器：把开始/结束事件转为播放状态广播 */
public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        boolean isStart = intent.getBooleanExtra(ScheduleManager.EXTRA_START, true);
        String repeat = intent.getStringExtra("repeat");
        if (Constants.REPEAT_WEEKDAY.equals(repeat)) {
            int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return; // 工作日计划：周末忽略
        }
        new ScheduleManager(ctx).broadcastState(isStart, isStart ? "PLAN_START" : "PLAN_END");
    }
}
