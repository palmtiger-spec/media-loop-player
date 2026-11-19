package com.hu.medialoop.schedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.model.SchedulePlan;
import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.VerificationRecorder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 定时模块：分钟粒度的播放/暂停计划。
 * 冲突规则：后计划覆盖前计划（按配置顺序，重叠时丢弃先配置的计划）。
 * 断电恢复：BootReceiver 重新注册闹钟，LoopPlayer 从保存的索引/进度继续。
 */
public final class ScheduleManager {
    public static final String ACTION_SCHEDULE_CHANGED = "com.hu.medialoop.SCHEDULE_CHANGED";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_REASON = "reason";
    public static final String EXTRA_START = "is_start";

    private final Context ctx;
    private final AppConfig cfg;
    private final AlarmManager alarmManager;

    public ScheduleManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.cfg = AppConfig.get(ctx);
        this.alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * 冲突处理：若 plan[i] 与任意 plan[j] (j>i) 区间重叠，则 plan[i] 被丢弃。
     * 跨天区间（start >= end）视为无效并丢弃。
     */
    public List<SchedulePlan> effectivePlans(List<SchedulePlan> plans) {
        List<SchedulePlan> result = new ArrayList<>();
        if (plans == null || plans.isEmpty()) return result;
        for (int i = 0; i < plans.size(); i++) {
            SchedulePlan p = plans.get(i);
            if (!p.isValid() || p.startMinutes() >= p.endMinutes()) {
                AppLogger.w("无效时间计划，丢弃: " + p.start + "-" + p.end);
                VerificationRecorder.recordFallback("INVALID_SCHEDULE", p.start + "-" + p.end);
                continue;
            }
            boolean covered = false;
            for (int j = i + 1; j < plans.size(); j++) {
                SchedulePlan q = plans.get(j);
                if (!q.isValid()) continue;
                // 区间重叠：q.start < p.end && p.start < q.end
                if (q.startMinutes() < p.endMinutes() && p.startMinutes() < q.endMinutes()) {
                    covered = true;
                    AppLogger.i("计划冲突，后计划覆盖前计划: " + p.start + "-" + p.end
                            + " 被 " + q.start + "-" + q.end + " 覆盖");
                    VerificationRecorder.recordScheduleState(false, "PLAN_OVERRIDDEN");
                    break;
                }
            }
            if (!covered) result.add(p);
        }
        return result;
    }

    /** 重新注册所有闹钟；先取消旧的避免重复 */
    public void refresh() {
        if (alarmManager == null) return;
        cancelAll();
        List<SchedulePlan> plans = effectivePlans(cfg.getPlans());
        for (int i = 0; i < plans.size(); i++) {
            SchedulePlan p = plans.get(i);
            register(p.startMinutes(), true, i * 2, p.repeat);
            register(p.endMinutes(), false, i * 2 + 1, p.repeat);
        }
        AppLogger.i("定时计划已注册: " + plans.size() + " 条");
    }

    private void register(int minutes, boolean isStart, int requestCode, String repeat) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60);
        cal.set(Calendar.MINUTE, minutes % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        Intent intent = new Intent(ctx, ScheduleReceiver.class);
        intent.putExtra(EXTRA_START, isStart);
        intent.putExtra("repeat", repeat);
        intent.putExtra("plan_index", requestCode);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent, flags);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
    }

    public void cancelAll() {
        if (alarmManager == null) return;
        for (int i = 0; i < 64; i++) {
            Intent intent = new Intent(ctx, ScheduleReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(ctx, i, intent, flags);
            alarmManager.cancel(pi);
        }
    }

    /** 当前时刻是否处于有效计划区间内（用于启动/开机时判定） */
    public boolean shouldPlayNow() {
        Calendar now = Calendar.getInstance();
        int cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int dow = now.get(Calendar.DAY_OF_WEEK);
        boolean weekday = dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY;
        for (SchedulePlan p : effectivePlans(cfg.getPlans())) {
            if (Constants.REPEAT_WEEKDAY.equals(p.repeat) && !weekday) continue;
            if (p.startMinutes() <= cur && cur < p.endMinutes()) return true;
        }
        return false;
    }

    /** 发送状态变化广播，由 PlayerActivity 转为暂停/恢复 */
    public void broadcastState(boolean playing, String reason) {
        Intent i = new Intent(ACTION_SCHEDULE_CHANGED);
        i.setPackage(ctx.getPackageName());
        i.putExtra(EXTRA_PLAYING, playing);
        i.putExtra(EXTRA_REASON, reason);
        ctx.sendBroadcast(i);
        VerificationRecorder.recordScheduleState(playing, reason);
    }
}
