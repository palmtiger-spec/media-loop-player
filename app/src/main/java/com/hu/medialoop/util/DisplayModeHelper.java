package com.hu.medialoop.util;

import android.graphics.Rect;
import android.graphics.RectF;

import com.hu.medialoop.config.Constants;

/**
 * 显示模式计算：填满(CENTER_CROP) / 保持比例(FIT_CENTER + 黑边)。
 * 纯计算无视图依赖，便于单独验证宽高比与黑边行为。
 */
public final class DisplayModeHelper {
    private DisplayModeHelper() {}

    public static final class DrawSpec {
        public final Rect src;    // 源图裁剪区（填满模式可能非全图）
        public final RectF dst;   // 目标绘制区（保持比例时小于目标区，余量留黑）
        public DrawSpec(Rect src, RectF dst) { this.src = src; this.dst = dst; }
    }

    public static DrawSpec calc(int srcW, int srcH, int dstW, int dstH, int mode) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
            return new DrawSpec(new Rect(0, 0, 1, 1), new RectF(0, 0, dstW, dstH));
        }
        if (mode == Constants.DISPLAY_FILL) {
            // 等比放大到短边铺满，居中裁剪
            float scale = Math.max((float) dstW / srcW, (float) dstH / srcH);
            int visW = Math.min(srcW, Math.round(dstW / scale));
            int visH = Math.min(srcH, Math.round(dstH / scale));
            int sx = (srcW - visW) / 2;
            int sy = (srcH - visH) / 2;
            return new DrawSpec(new Rect(sx, sy, sx + visW, sy + visH),
                    new RectF(0, 0, dstW, dstH));
        }
        // 保持比例：等比缩放至完全可见，居中留黑
        float scale = Math.min((float) dstW / srcW, (float) dstH / srcH);
        int w = Math.round(srcW * scale);
        int h = Math.round(srcH * scale);
        int x = (dstW - w) / 2;
        int y = (dstH - h) / 2;
        return new DrawSpec(new Rect(0, 0, srcW, srcH), new RectF(x, y, x + w, y + h));
    }
}
