package com.hu.medialoop.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.DisplayModeHelper;
import com.hu.medialoop.util.VerificationRecorder;

import java.util.ArrayList;
import java.util.List;

/**
 * 拼接模板视图：按 Constants.TEMPLATE_REGIONS 的归一化坐标绘制 T1–T5。
 * 区域数 ≤4、任意两区交集面积为 0；图片不足时缺区画占位色。
 * 切换触发：时间间隔（自动轮换模板）或手动；过渡：淡入淡出/滑动。
 */
public class CollageView extends ImageView {
    private final List<Bitmap> images = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint emptyPaint = new Paint();
    private final Handler handler = new Handler();
    private AppConfig cfg;
    private int templateIndex = 0;
    private int previousTemplate = -1;
    private int transition = Constants.TRANSITION_FADE;
    private float progress = 1f;
    private ValueAnimator animator;
    private boolean autoSwitching;

    public CollageView(Context c) { super(c); init(c); }
    public CollageView(Context c, AttributeSet a) { super(c, a); init(c); }

    private void init(Context c) {
        cfg = AppConfig.get(c);
        templateIndex = cfg.getTemplateIndex();
        transition = cfg.getCollageTransition();
        emptyPaint.setColor(Color.DKGRAY);
        emptyPaint.setStyle(Paint.Style.FILL);
    }

    public void setImages(List<Bitmap> imgs) {
        images.clear();
        if (imgs != null) images.addAll(imgs);
        invalidate();
    }

    /** 切换模板；idx 越界时回退默认 T1 并记录日志 */
    public void switchTemplate(int idx, int trigger) {
        int safe = (idx >= 0 && idx < Constants.TEMPLATE_REGIONS.length) ? idx : 0;
        if (safe != idx) {
            AppLogger.e("模板不匹配，回退默认 T1: " + idx);
            VerificationRecorder.recordFallback("TEMPLATE_MISMATCH", "T" + idx);
        }
        cfg.setTemplateIndex(safe);
        previousTemplate = templateIndex;
        templateIndex = safe;
        VerificationRecorder.recordCollageSwitch(trigger, safe);
        startTransition();
    }

    /** 手动触发按钮调用 */
    public void manualNext() {
        switchTemplate((templateIndex + 1) % Constants.TEMPLATE_REGIONS.length,
                Constants.TRIGGER_MANUAL);
    }

    public void startAutoSwitch(int intervalSec) {
        stopAutoSwitch();
        autoSwitching = true;
        handler.postDelayed(autoRunnable, Math.max(1, intervalSec) * 1000L);
    }

    public void stopAutoSwitch() {
        autoSwitching = false;
        handler.removeCallbacks(autoRunnable);
    }

    private final Runnable autoRunnable = new Runnable() {
        @Override public void run() {
            if (!autoSwitching) return;
            switchTemplate((templateIndex + 1) % Constants.TEMPLATE_REGIONS.length,
                    Constants.TRIGGER_INTERVAL);
            handler.postDelayed(this, Math.max(1, cfg.getCollageIntervalSec()) * 1000L);
        }
    };

    private void startTransition() {
        if (animator != null) animator.cancel();
        if (transition == Constants.TRANSITION_NONE || previousTemplate < 0) {
            progress = 1f;
            previousTemplate = -1;
            invalidate();
            return;
        }
        progress = 0f;
        VerificationRecorder.recordTransitionStart(transition, cfg.getTransitionMs());
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(cfg.getTransitionMs());
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                progress = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                progress = 1f;
                previousTemplate = -1;
                invalidate();
                VerificationRecorder.recordTransitionEnd(transition);
            }
        });
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(Color.BLACK);
        if (transition != Constants.TRANSITION_NONE && progress < 1f && previousTemplate >= 0) {
            if (transition == Constants.TRANSITION_FADE) {
                drawTemplate(canvas, previousTemplate, 1f - progress, 0);
                drawTemplate(canvas, templateIndex, progress, 0);
            } else {
                drawTemplate(canvas, previousTemplate, 1f, (int) (-w * progress));
                drawTemplate(canvas, templateIndex, 1f, (int) (w * (1f - progress)));
            }
        } else {
            drawTemplate(canvas, templateIndex, 1f, 0);
        }
    }

    private void drawTemplate(Canvas canvas, int tpl, float alpha, int offsetX) {
        if (tpl < 0 || tpl >= Constants.TEMPLATE_REGIONS.length) return;
        float[][] regions = Constants.TEMPLATE_REGIONS[tpl];
        paint.setAlpha(Math.max(0, Math.min(255, (int) (255 * alpha))));
        emptyPaint.setAlpha(paint.getAlpha());
        canvas.save();
        canvas.translate(offsetX, 0);
        int vw = getWidth(), vh = getHeight();
        for (int i = 0; i < regions.length; i++) {
            float[] r = regions[i];
            RectF rect = new RectF(r[0] * vw, r[1] * vh, r[2] * vw, r[3] * vh);
            if (i < images.size() && images.get(i) != null) {
                Bitmap bmp = images.get(i);
                DisplayModeHelper.DrawSpec spec = DisplayModeHelper.calc(
                        bmp.getWidth(), bmp.getHeight(), (int) rect.width(), (int) rect.height(),
                        Constants.DISPLAY_FILL);
                canvas.save();
                canvas.translate(rect.left, rect.top);
                canvas.drawBitmap(bmp, spec.src, spec.dst, paint);
                canvas.restore();
            } else {
                canvas.drawRect(rect, emptyPaint); // 图片不足：缺区留空（占位色）
            }
        }
        canvas.restore();
    }

    /** 释放引用，避免持有已回收 Bitmap */
    public void clearImages() {
        images.clear();
        invalidate();
    }
}
