package com.hu.medialoop.config;

/**
 * 全局常量：枚举取值、默认阈值、拼接模板坐标表。
 * 对应需求文档《媒体循环播放应用_需求模块与验收约束》第 0 节术语约定。
 * 所有数值集中于此，便于验收时按表调整。
 */
public final class Constants {
    private Constants() {}

    /* ---------- 枚举取值（与需求文档全文统一） ---------- */
    // 循环模式
    public static final int LOOP_SEQUENTIAL = 0;   // 顺序
    public static final int LOOP_RANDOM      = 1;   // 随机
    // 转场效果
    public static final int TRANSITION_NONE  = 0;   // 无
    public static final int TRANSITION_FADE  = 1;   // 淡入淡出（默认）
    public static final int TRANSITION_SLIDE = 2;   // 滑动
    // 背景音乐切换规则
    public static final int BGM_PER_IMAGE    = 0;   // 每张图片切换
    public static final int BGM_BY_LIST      = 1;   // 按播放列表顺序
    // 显示模式
    public static final int DISPLAY_FILL     = 0;   // 填满区域（可裁剪）
    public static final int DISPLAY_FIT      = 1;   // 保持比例（黑边填充）
    // 拼接切换触发
    public static final int TRIGGER_INTERVAL = 0;   // 时间间隔
    public static final int TRIGGER_MANUAL   = 1;   // 手动
    // 淘汰策略
    public static final String EVICTION_LRU  = "LRU";
    public static final String EVICTION_FIFO = "FIFO";
    // 重复规则
    public static final String REPEAT_DAILY   = "daily";
    public static final String REPEAT_WEEKDAY = "weekday";
    // 加载状态
    public static final int LOAD_LOADING = 0;
    public static final int LOAD_SUCCESS = 1;
    public static final int LOAD_FAILED  = 2;
    public static final int LOAD_FALLBACK = 3;

    /* ---------- 播放模块默认值与边界 ---------- */
    public static final int DEFAULT_TRANSITION_MS = 500;   // 默认转场 0.5 s
    public static final int MIN_TRANSITION_MS     = 100;   // 0.1 s
    public static final int MAX_TRANSITION_MS     = 2000;  // 2.0 s
    public static final int DEFAULT_IMAGE_DURATION_MS = 5000;
    public static final int DEFAULT_MAX_FILE_MB   = 100;   // 单文件 ≤100 MB
    public static final int DEFAULT_MAX_IMAGE_W   = 1920;  // px
    public static final int DEFAULT_MAX_IMAGE_H   = 1080;  // px
    public static final String VIDEO_CODEC = "H.264";

    /* ---------- 拼接模板模块边界 ---------- */
    public static final int MAX_TEMPLATES = 5;
    public static final int MAX_COLLAGE_REGIONS = 4;

    /**
     * 模板坐标表：归一化 [x0,y0,x1,y1]，原点左上，取值 0–1。
     * 数组下标 = 模板序号 - 1；任意两区域交集面积为 0（可在设置界面校验）。
     */
    public static final float[][][] TEMPLATE_REGIONS = {
        /* T1 全屏单图 */ {{0f,0f,1f,1f}},
        /* T2 水平等分 */ {{0f,0f,0.5f,1f}, {0.5f,0f,1f,1f}},
        /* T3 垂直等分 */ {{0f,0f,1f,0.5f}, {0f,0.5f,1f,1f}},
        /* T4 2×2 网格 */ {{0f,0f,0.5f,0.5f}, {0.5f,0f,1f,0.5f}, {0f,0.5f,0.5f,1f}, {0.5f,0.5f,1f,1f}},
        /* T5 主图+三缩略 */ {{0f,0f,1f,0.7f}, {0f,0.7f,0.333f,1f}, {0.333f,0.7f,0.667f,1f}, {0.667f,0.7f,1f,1f}}
    };

    /* ---------- 定时模块边界 ---------- */
    public static final int SCHEDULE_MINUTE_GRANULARITY = 1; // 分钟级

    /* ---------- 网络模块默认值与边界 ---------- */
    public static final int DEFAULT_NET_TIMEOUT_SEC = 10;
    public static final int MIN_NET_TIMEOUT_SEC = 1;
    public static final int MAX_NET_TIMEOUT_SEC = 60;
    public static final int DEFAULT_NET_RETRY = 3;
    public static final int MAX_NET_RETRY = 5;
    public static final int DEFAULT_RETRY_INTERVAL_SEC = 5;
    public static final int DEFAULT_CACHE_MB = 500;
    public static final int HIGH_LATENCY_MS = 2000;   // 弱网验收：延迟 ≥2 s
    public static final int LOW_BANDWIDTH_KBPS = 100; // 弱网验收：带宽 ≤100 kbps

    /* ---------- 安全模块边界 ---------- */
    public static final int MIN_PASSWORD_LEN = 8;
    public static final int LOCK_THRESHOLD = 5;       // 连续 5 次错误
    public static final int LOCK_MINUTES = 5;         // 锁定 5 min
    public static final String RESET_PASSWORD = "Reset2026"; // 管理员预设重置密码（≥8 位含字母数字）

    /* ---------- 资源使用验收门槛 ---------- */
    public static final int MEMORY_PEAK_MB = 512;     // 内存峰值上限
    public static final int CPU_AVG_PERCENT = 70;     // CPU 平均上限
    public static final int SMOKE_MINUTES = 10;       // 冒烟/采样时长
}
