package com.hu.medialoop.model;

/** 时间计划项：开始/结束 HH:MM（分钟粒度），重复规则 daily/weekday */
public final class SchedulePlan {
    public final String start;
    public final String end;
    public final String repeat;

    public SchedulePlan(String start, String end, String repeat) {
        this.start = start;
        this.end = end;
        this.repeat = repeat;
    }

    /** HH:MM 格式校验，24 小时制 */
    public static boolean validTime(String t) {
        if (t == null || t.length() != 5 || t.charAt(2) != ':') return false;
        try {
            int h = Integer.parseInt(t.substring(0, 2));
            int m = Integer.parseInt(t.substring(3, 5));
            return h >= 0 && h <= 23 && m >= 0 && m <= 59;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean isValid() { return validTime(start) && validTime(end); }

    /** 分钟数，用于区间比较 */
    public int startMinutes() { return toMinutes(start); }
    public int endMinutes() { return toMinutes(end); }

    private static int toMinutes(String t) {
        return Integer.parseInt(t.substring(0, 2)) * 60 + Integer.parseInt(t.substring(3, 5));
    }
}
