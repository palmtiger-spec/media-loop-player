package com.hu.medialoop.security;

import android.content.Context;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.config.Constants;
import com.hu.medialoop.util.AppLogger;
import com.hu.medialoop.util.VerificationRecorder;

import java.security.MessageDigest;
import java.util.UUID;

/**
 * 安全模块：密码强度、错误锁定、管理员解锁/重置。
 * 密码以 SHA-256(salt+pwd) 存储，不明文落盘。
 */
public final class AuthManager {
    public enum Result { SUCCESS, FAIL, LOCKED, WEAK }

    private final AppConfig cfg;

    public AuthManager(Context ctx) { this.cfg = AppConfig.get(ctx); }

    /** 强度规则：长度 ≥8 且同时包含字母与数字 */
    public boolean isStrong(String pwd) {
        if (pwd == null || pwd.length() < Constants.MIN_PASSWORD_LEN) return false;
        boolean letter = false, digit = false;
        for (int i = 0; i < pwd.length(); i++) {
            char c = pwd.charAt(i);
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        return letter && digit;
    }

    public boolean isLocked() {
        long start = cfg.getLockStartMs();
        if (start <= 0) return false;
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= (long) Constants.LOCK_MINUTES * 60 * 1000) {
            cfg.setFailCount(0);
            cfg.setLockStartMs(0);
            AppLogger.i("锁定时间到期，自动解锁");
            return false;
        }
        return true;
    }

    public long getRemainingLockMs() {
        long start = cfg.getLockStartMs();
        if (start <= 0) return 0;
        long remain = (long) Constants.LOCK_MINUTES * 60 * 1000
                - (System.currentTimeMillis() - start);
        return Math.max(0, remain);
    }

    /** 首次进入若未设置密码，用本方法设置；重置后也走此路径（强制改密） */
    public Result setInitialPassword(String pwd) {
        if (!isStrong(pwd)) {
            VerificationRecorder.recordAuth(false, cfg.getFailCount(), isLocked());
            return Result.WEAK;
        }
        savePassword(pwd);
        cfg.setForceChangePassword(false);
        cfg.setFailCount(0);
        cfg.setLockStartMs(0);
        VerificationRecorder.recordAuth(true, 0, false);
        return Result.SUCCESS;
    }

    /** 认证：锁定期内即使密码正确也拒绝 */
    public Result authenticate(String pwd) {
        if (isLocked()) {
            VerificationRecorder.recordAuth(false, cfg.getFailCount(), true);
            return Result.LOCKED;
        }
        if (!cfg.isPasswordSet()) return setInitialPassword(pwd);

        if (sha256(cfg.getPasswordSalt() + (pwd == null ? "" : pwd)).equals(cfg.getPasswordHash())) {
            cfg.setFailCount(0);
            cfg.setLockStartMs(0);
            VerificationRecorder.recordAuth(true, 0, false);
            return Result.SUCCESS;
        }

        int fail = cfg.getFailCount() + 1;
        cfg.setFailCount(fail);
        if (fail >= Constants.LOCK_THRESHOLD) {
            cfg.setLockStartMs(System.currentTimeMillis());
            AppLogger.e("连续 " + fail + " 次错误，锁定 " + Constants.LOCK_MINUTES + " 分钟");
        }
        VerificationRecorder.recordAuth(false, fail, isLocked());
        return Result.FAIL;
    }

    /** 管理员手动解锁（提前解除锁定） */
    public void unlockByAdmin() {
        cfg.setFailCount(0);
        cfg.setLockStartMs(0);
        AppLogger.i("管理员手动解锁");
        VerificationRecorder.recordAuth(true, 0, false);
    }

    /** 管理员预设重置密码：重置后清空密码，强制下次设置新密码 */
    public boolean resetByAdminPassword(String input) {
        if (!Constants.RESET_PASSWORD.equals(input)) return false;
        cfg.setPasswordHash("");
        cfg.setPasswordSalt("");
        cfg.setFailCount(0);
        cfg.setLockStartMs(0);
        cfg.setForceChangePassword(true);
        AppLogger.i("管理员重置密码成功，下次进入强制修改");
        VerificationRecorder.recordAuth(false, 0, false);
        return true;
    }

    /** 修改密码：需原密码正确（首次/重置后可不校验），新密码须满足强度 */
    public boolean changePassword(String oldPwd, String newPwd) {
        if (!isStrong(newPwd)) return false;
        if (cfg.isPasswordSet() && !cfg.isForceChangePassword()) {
            if (!sha256(cfg.getPasswordSalt() + (oldPwd == null ? "" : oldPwd))
                    .equals(cfg.getPasswordHash())) return false;
        }
        savePassword(newPwd);
        cfg.setForceChangePassword(false);
        return true;
    }

    private void savePassword(String pwd) {
        String salt = UUID.randomUUID().toString();
        cfg.setPasswordSalt(salt);
        cfg.setPasswordHash(sha256(salt + pwd));
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            AppLogger.e("SHA-256 计算失败: " + e.getMessage());
            return "";
        }
    }
}
