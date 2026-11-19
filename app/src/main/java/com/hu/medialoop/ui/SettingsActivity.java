package com.hu.medialoop.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.model.SchedulePlan;
import com.hu.medialoop.schedule.ScheduleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放设置：所有需求参数以表单呈现并写入 AppConfig（表驱动，验收时可直接改）。
 * 为保持零布局资源开销，界面用代码构建。
 */
public class SettingsActivity extends Activity {
    private AppConfig cfg;
    private LinearLayout root;

    private EditText etMediaDir, etBgmDir, etTransitionMs, etImageDuration, etRandomSeed;
    private EditText etCollageInterval, etPlans, etUrls, etTimeout, etRetry, etRetryInterval, etCacheMb;
    private Spinner spLoop, spTransition, spBgm, spDisplay, spTemplate, spTrigger, spEviction;
    private CheckBox cbWakeLock;
    private TextView tvHitRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = AppConfig.get(this);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = 24;
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);
        setContentView(sv);

        section("路径");
        etMediaDir = addText("媒体目录", cfg.getMediaDir(), false);
        etBgmDir = addText("背景音乐目录", cfg.getBgmDir(), false);

        section("播放模块");
        spLoop = addSpinner("循环模式", new String[]{"顺序", "随机"}, cfg.getLoopMode());
        spTransition = addSpinner("转场效果", new String[]{"无", "淡入淡出", "滑动"}, cfg.getTransition());
        etTransitionMs = addText("转场时长(ms, 100-2000)", String.valueOf(cfg.getTransitionMs()), true);
        etImageDuration = addText("每张图片停留(ms)", String.valueOf(cfg.getImageDurationMs()), true);
        spBgm = addSpinner("背景音乐切换", new String[]{"每张图片切换", "按列表顺序"}, cfg.getBgmMode());
        etRandomSeed = addText("随机种子(-1=系统时间)", String.valueOf(cfg.getRandomSeed()), true);
        section("显示模式");
        spDisplay = addSpinner("显示模式", new String[]{"填满区域", "保持比例"}, cfg.getDisplayMode());

        section("拼接模板模块");
        spTemplate = addSpinner("默认模板", new String[]{"T1", "T2", "T3", "T4", "T5"}, cfg.getTemplateIndex());
        spTrigger = addSpinner("切换触发", new String[]{"时间间隔", "手动"}, cfg.getCollageTrigger());
        etCollageInterval = addText("自动切换间隔(秒)", String.valueOf(cfg.getCollageIntervalSec()), true);

        section("定时模块（每行 HH:MM-HH:MM:daily|weekday，后计划覆盖前计划）");
        StringBuilder plans = new StringBuilder();
        for (SchedulePlan p : cfg.getPlans()) {
            if (plans.length() > 0) plans.append('\n');
            plans.append(p.start).append('-').append(p.end).append(':').append(p.repeat);
        }
        etPlans = addMultiText(plans.toString());

        section("网络资源模块（每行一个 URL）");
        StringBuilder urls = new StringBuilder();
        for (String u : cfg.getNetworkUrls()) {
            if (urls.length() > 0) urls.append('\n');
            urls.append(u);
        }
        etUrls = addMultiText(urls.toString());
        etTimeout = addText("超时(秒, 1-60)", String.valueOf(cfg.getNetTimeoutSec()), true);
        etRetry = addText("重试次数(0-5)", String.valueOf(cfg.getNetRetry()), true);
        etRetryInterval = addText("重试间隔(秒)", String.valueOf(cfg.getRetryIntervalSec()), true);
        etCacheMb = addText("缓存容量(MB)", String.valueOf(cfg.getCacheMb()), true);
        spEviction = addSpinner("淘汰策略", new String[]{"LRU", "FIFO"},
                Constants.EVICTION_FIFO.equals(cfg.getEviction()) ? 1 : 0);
        tvHitRate = new TextView(this);
        tvHitRate.setTextSize(14f);
        root.addView(tvHitRate);
        showHitRate();
        Button btnReset = new Button(this);
        btnReset.setText("重置命中率统计");
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cfg.resetCacheStats();
                showHitRate();
            }
        });
        root.addView(btnReset);

        section("其他");
        cbWakeLock = new CheckBox(this);
        cbWakeLock.setText("播放期间保持屏幕常亮");
        cbWakeLock.setChecked(cfg.isWakeLockEnabled());
        root.addView(cbWakeLock);

        Button save = new Button(this);
        save.setText("保存设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAll(); }
        });
        root.addView(save);
    }

    private void showHitRate() {
        tvHitRate.setText(String.format("缓存命中: %d/%d  命中率: %.1f%%",
                cfg.getCacheHit(), cfg.getCacheHit() + cfg.getCacheMiss(), cfg.getCacheHitRate()));
    }

    private void section(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(16f);
        tv.setPadding(0, 24, 0, 8);
        root.addView(tv);
    }

    private EditText addText(String label, String value, boolean numeric) {
        TextView tv = new TextView(this);
        tv.setText(label);
        root.addView(tv);
        EditText et = new EditText(this);
        et.setText(value);
        if (numeric) et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(et);
        return et;
    }

    private EditText addMultiText(String value) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setMinLines(3);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(et);
        return et;
    }

    private Spinner addSpinner(String label, String[] items, int selected) {
        TextView tv = new TextView(this);
        tv.setText(label);
        root.addView(tv);
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setSelection(selected);
        root.addView(sp);
        return sp;
    }

    private int parseInt(EditText et, int fallback) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return fallback; }
    }

    private void saveAll() {
        cfg.setMediaDir(etMediaDir.getText().toString().trim());
        cfg.setBgmDir(etBgmDir.getText().toString().trim());

        cfg.setLoopMode(spLoop.getSelectedItemPosition());
        cfg.setTransition(spTransition.getSelectedItemPosition());
        cfg.setTransitionMs(parseInt(etTransitionMs, Constants.DEFAULT_TRANSITION_MS));
        cfg.setImageDurationMs(parseInt(etImageDuration, Constants.DEFAULT_IMAGE_DURATION_MS));
        cfg.setBgmMode(spBgm.getSelectedItemPosition());
        cfg.setRandomSeed(parseInt(etRandomSeed, -1));
        cfg.setDisplayMode(spDisplay.getSelectedItemPosition());

        cfg.setTemplateIndex(spTemplate.getSelectedItemPosition());
        cfg.setCollageTrigger(spTrigger.getSelectedItemPosition());
        cfg.setCollageIntervalSec(parseInt(etCollageInterval, 10));

        List<SchedulePlan> plans = new ArrayList<>();
        String[] lines = etPlans.getText().toString().split("\\n");
        for (String line : lines) {
            String[] parts = line.trim().split("-");
            if (parts.length == 3 && SchedulePlan.validTime(parts[0]) && SchedulePlan.validTime(parts[1])) {
                String repeat = "weekday".equals(parts[2]) ? Constants.REPEAT_WEEKDAY : Constants.REPEAT_DAILY;
                plans.add(new SchedulePlan(parts[0], parts[1], repeat));
            }
        }
        cfg.setPlans(plans);

        List<String> urls = new ArrayList<>();
        for (String line : etUrls.getText().toString().split("\\n")) {
            if (!line.trim().isEmpty()) urls.add(line.trim());
        }
        cfg.setNetworkUrls(urls);
        cfg.setNetTimeoutSec(parseInt(etTimeout, Constants.DEFAULT_NET_TIMEOUT_SEC));
        cfg.setNetRetry(parseInt(etRetry, Constants.DEFAULT_NET_RETRY));
        cfg.setRetryIntervalSec(parseInt(etRetryInterval, Constants.DEFAULT_RETRY_INTERVAL_SEC));
        cfg.setCacheMb(parseInt(etCacheMb, Constants.DEFAULT_CACHE_MB));
        cfg.setEviction(spEviction.getSelectedItemPosition() == 1
                ? Constants.EVICTION_FIFO : Constants.EVICTION_LRU);
        cfg.setWakeLockEnabled(cbWakeLock.isChecked());

        new ScheduleManager(this).refresh(); // 定时配置变化后立即重注册
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
