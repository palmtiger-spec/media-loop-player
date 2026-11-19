package com.hu.medialoop.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.util.DisplayModeHelper;
import com.hu.medialoop.util.VerificationRecorder;

/**
 * 图片渲染视图：支持显示模式（填满/保持比例）与三种转场（无/淡入淡出/滑动）。
 * 转场起止时间戳写入 verification.log，供验收度量实际时长。
 */
public class PlayerView extends ImageView {
    private Bitmap current;
    private Bitmap previous;
    private Bitmap placeholder;
    private int displayMode = Constants.DISPLAY_FIT;
    private int transitionType = Constants.TRANSITION_NONE;
    private float progress = 1f;
    private ValueAnimator animator;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bgPaint = new Paint();

    public PlayerView(Context c) { super(c); init(c); }
    public PlayerView(Context c, AttributeSet a) { super(c, a); init(c); }

    private void init(Context c) {
        bgPaint.setColor(Color.BLACK); // 保持比例模式的留黑
        displayMode = AppConfig.get(c).getDisplayMode();
        placeholder = Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565);
        placeholder.eraseColor(Color.DKGRAY);
    }

    public void setDisplayMode(int mode) { this.displayMode = mode; invalidate(); }

    /** 显示新帧并触发转场 */
    public void show(Bitmap next, int type, int durationMs) {
        previous = current;
        current = (next != null) ? next : placeholder;
        transitionType = type;

        if (type == Constants.TRANSITION_NONE || previous == null || durationMs <= 0) {
            progress = 1f;
            invalidate();
            return;
        }
        VerificationRecorder.recordTransitionStart(type, durationMs);
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(durationMs);
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
                previous = null;
                transitionType = Constants.TRANSITION_NONE;
                invalidate();
                VerificationRecorder.recordTransitionEnd(type);
            }
            @Override public void onAnimationCancel(Animator animation) { progress = 1f; previous = null; }
        });
        animator.start();
    }

    /** 文件损坏/解码失败时显示占位图 */
    public void showPlaceholder(int type, int durationMs) {
        show(placeholder, type, durationMs);
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);
        if (transitionType != Constants.TRANSITION_NONE && progress < 1f && previous != null) {
            if (transitionType == Constants.TRANSITION_FADE) {
                drawBitmap(canvas, previous, 1f - progress, 0);
                drawBitmap(canvas, current, progress, 0);
            } else if (transitionType == Constants.TRANSITION_SLIDE) {
                drawBitmap(canvas, previous, 1f, (int) (-w * progress));
                drawBitmap(canvas, current, 1f, (int) (w * (1f - progress)));
            } else {
                drawBitmap(canvas, current, 1f, 0);
            }
        } else {
            drawBitmap(canvas, current, 1f, 0);
        }
    }

    private void drawBitmap(Canvas canvas, Bitmap bmp, float alpha, int offsetX) {
        if (bmp == null || bmp.isRecycled()) return;
        DisplayModeHelper.DrawSpec spec = DisplayModeHelper.calc(
                bmp.getWidth(), bmp.getHeight(), getWidth(), getHeight(), displayMode);
        bitmapPaint.setAlpha(Math.max(0, Math.min(255, (int) (255 * alpha))));
        canvas.save();
        canvas.translate(offsetX, 0);
        canvas.drawBitmap(bmp, spec.src, spec.dst, bitmapPaint);
        canvas.restore();
    }

    /** 释放大图，避免低端机内存累积 */
    public void releaseCurrent() {
        if (current != null && current != placeholder && !current.isRecycled()) current.recycle();
        current = null;
    }
}
