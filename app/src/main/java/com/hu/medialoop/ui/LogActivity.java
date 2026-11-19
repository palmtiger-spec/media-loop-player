package com.hu.medialoop.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.VerificationRecorder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/** 验收数据查看：播放序列/转场时间戳/重试间隔/降级事件/命中率，可用 adb pull 导出 */
public class LogActivity extends Activity {
    private TextView tvSummary;
    private TextView tvVerify;
    private AppConfig cfg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = AppConfig.get(this);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        sv.addView(root);
        setContentView(sv);

        tvSummary = new TextView(this);
        tvSummary.setTextSize(14f);
        root.addView(tvSummary);

        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        root.addView(refresh);

        tvVerify = new TextView(this);
        tvVerify.setTextSize(11f);
        root.addView(tvVerify);

        refresh();
    }

    private void refresh() {
        File vf = VerificationRecorder.getFile();
        File rf = AppLogger.getLogFile();
        tvSummary.setText("缓存命中: " + cfg.getCacheHit() + " / 总请求 "
                + (cfg.getCacheHit() + cfg.getCacheMiss())
                + "  命中率: " + String.format("%.1f%%", cfg.getCacheHitRate())
                + "\n验收日志: " + (vf == null ? "无" : vf.getAbsolutePath())
                + "\n运行日志: " + (rf == null ? "无" : rf.getAbsolutePath())
                + "\n导出命令: adb pull " + (vf == null ? "" : vf.getAbsolutePath()));
        tvVerify.setText(tail(vf, 120));
    }

    private String tail(File file, int lines) {
        if (file == null || !file.exists()) return "（暂无验收记录）";
        List<String> buf = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                buf.add(line);
                if (buf.size() > lines) buf.remove(0);
            }
            br.close();
        } catch (Exception e) {
            return "读取失败: " + e.getMessage();
        }
        StringBuilder sb = new StringBuilder();
        for (String l : buf) sb.append(l).append('\n');
        return sb.toString();
    }
}
