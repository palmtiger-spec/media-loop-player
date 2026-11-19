package com.hu.medialoop.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hu.medialoop.R;
import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.schedule.ScheduleManager;
import com.hu.medialoop.util.AppLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 主入口：权限申请、默认媒体目录准备、定时计划初始化、功能导航 */
public class MainActivity extends Activity {
    private static final int REQ_PERM = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prepareDirs();
        requestMediaPermissions();
        new ScheduleManager(this).refresh();

        ((Button) findViewById(R.id.btn_play)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PlayerActivity.class));
            }
        });
        ((Button) findViewById(R.id.btn_settings)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        ((Button) findViewById(R.id.btn_admin)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AdminActivity.class));
            }
        });
        ((Button) findViewById(R.id.btn_logs)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, LogActivity.class));
            }
        });

        AppConfig cfg = AppConfig.get(this);
        ((TextView) findViewById(R.id.tv_hint)).setText("媒体目录: " + cfg.getMediaDir()
                + "\n背景音乐目录: " + cfg.getBgmDir());
    }

    private void prepareDirs() {
        AppConfig cfg = AppConfig.get(this);
        File media = new File(cfg.getMediaDir());
        File bgm = new File(cfg.getBgmDir());
        if (!media.exists()) media.mkdirs();
        if (!bgm.exists()) bgm.mkdirs();
    }

    private void requestMediaPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[0]), REQ_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_PERM) {
            for (int i = 0; i < grants.length; i++) {
                if (grants[i] != PackageManager.PERMISSION_GRANTED) {
                    AppLogger.e("权限缺失: " + perms[i] + "（相关功能将降级）");
                    com.hu.medialoop.util.VerificationRecorder.recordFallback("PERMISSION_DENIED", perms[i]);
                }
            }
        }
    }
}
