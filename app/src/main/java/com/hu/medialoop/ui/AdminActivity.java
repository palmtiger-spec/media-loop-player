package com.hu.medialoop.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.security.AuthManager;

/** 后台管理：登录、修改密码、管理员解锁、管理员重置（重置后强制改密） */
public class AdminActivity extends Activity {
    private AuthManager auth;
    private AppConfig cfg;
    private EditText etPwd, etOldPwd, etNewPwd, etResetPwd;
    private TextView tvStatus;
    private LinearLayout panel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthManager(this);
        cfg = AppConfig.get(this);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        sv.addView(root);
        setContentView(sv);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(15f);
        root.addView(tvStatus);

        if (!cfg.isPasswordSet() || cfg.isForceChangePassword()) {
            tvStatus.setText("首次进入 / 重置后：请设置新密码（≥8 位，含字母和数字）");
        } else if (auth.isLocked()) {
            tvStatus.setText("已锁定，剩余 " + (auth.getRemainingLockMs() / 1000) + " 秒");
        } else {
            tvStatus.setText("请输入后台管理密码");
        }

        etPwd = new EditText(this);
        etPwd.setHint("密码");
        etPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(etPwd);

        Button login = new Button(this);
        login.setText("登录 / 设置密码");
        login.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doLogin(); }
        });
        root.addView(login);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        root.addView(panel);
        buildPanel();

        if (auth.isLocked()) {
            etPwd.setEnabled(false);
            login.setEnabled(false);
        }
    }

    private void buildPanel() {
        TextView t1 = new TextView(this);
        t1.setText("—— 修改密码 ——");
        panel.addView(t1);
        etOldPwd = new EditText(this);
        etOldPwd.setHint("原密码（重置后无需填写）");
        etOldPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(etOldPwd);
        etNewPwd = new EditText(this);
        etNewPwd.setHint("新密码（≥8 位，含字母和数字）");
        etNewPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(etNewPwd);
        Button btnChange = new Button(this);
        btnChange.setText("修改密码");
        btnChange.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean ok = auth.changePassword(etOldPwd.getText().toString(),
                        etNewPwd.getText().toString());
                Toast.makeText(AdminActivity.this, ok ? "密码已修改" : "修改失败：新密码强度不足或原密码错误",
                        Toast.LENGTH_SHORT).show();
            }
        });
        panel.addView(btnChange);

        TextView t2 = new TextView(this);
        t2.setText("—— 管理员操作 ——");
        panel.addView(t2);
        Button btnUnlock = new Button(this);
        btnUnlock.setText("管理员手动解锁");
        btnUnlock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                auth.unlockByAdmin();
                tvStatus.setText("已手动解锁");
                finish();
            }
        });
        panel.addView(btnUnlock);

        etResetPwd = new EditText(this);
        etResetPwd.setHint("管理员预设重置密码");
        etResetPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(etResetPwd);
        Button btnReset = new Button(this);
        btnReset.setText("重置密码（强制下次修改）");
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean ok = auth.resetByAdminPassword(etResetPwd.getText().toString());
                Toast.makeText(AdminActivity.this, ok ? "重置成功，下次进入请设置新密码" : "重置密码错误",
                        Toast.LENGTH_SHORT).show();
                if (ok) finish();
            }
        });
        panel.addView(btnReset);
        panel.setVisibility(View.GONE);
    }

    private void doLogin() {
        AuthManager.Result r = auth.authenticate(etPwd.getText().toString());
        switch (r) {
            case SUCCESS:
                panel.setVisibility(View.VISIBLE);
                tvStatus.setText("认证成功，后台功能已解锁");
                break;
            case LOCKED:
                tvStatus.setText("账户已锁定，剩余 " + (auth.getRemainingLockMs() / 1000) + " 秒");
                break;
            case WEAK:
                tvStatus.setText("密码强度不足：至少 8 位且包含字母和数字");
                break;
            case FAIL:
            default:
                int left = com.hu.medialoop.config.Constants.LOCK_THRESHOLD - cfg.getFailCount();
                tvStatus.setText("密码错误，还可尝试 " + Math.max(0, left) + " 次（连续 5 次错误锁定 5 分钟）");
                break;
        }
    }
}
