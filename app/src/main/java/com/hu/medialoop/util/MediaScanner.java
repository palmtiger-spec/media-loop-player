package com.hu.medialoop.util;

import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.hu.medialoop.config.AppConfig;
import com.hu.medialoop.model.MediaItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 媒体扫描：按格式/大小/分辨率/编码边界过滤，形成可复现的播放列表。
 * 边界超限的文件记日志并跳过；疑似损坏的文件保留在列表中，交由播放层触发占位图降级。
 */
public final class MediaScanner {
    private final AppConfig cfg;

    public MediaScanner(AppConfig cfg) { this.cfg = cfg; }

    private static MediaItem.Type typeOf(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) return MediaItem.Type.IMAGE;
        if (n.endsWith(".mp4")) return MediaItem.Type.VIDEO;
        if (n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a") || n.endsWith(".ogg")) return MediaItem.Type.AUDIO;
        return null;
    }

    /** 扫描目录中的图片与视频；排序保证验收序列可复现 */
    public List<MediaItem> scanDirectory(String dirPath) {
        List<MediaItem> out = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            AppLogger.w("媒体目录不存在: " + dirPath);
            return out;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            AppLogger.e("无法读取媒体目录（权限缺失?）: " + dirPath);
            VerificationRecorder.recordFallback("PERMISSION_DENIED", dirPath);
            return out;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File f : files) {
            if (f.isDirectory()) continue;
            MediaItem.Type type = typeOf(f.getName());
            if (type == null || type == MediaItem.Type.AUDIO) {
                AppLogger.i("跳过不支持格式: " + f.getName());
                VerificationRecorder.recordFallback("UNSUPPORTED_FORMAT", f.getPath());
                continue;
            }
            if (f.length() > (long) cfg.getMaxFileMb() * 1024 * 1024) {
                AppLogger.w("超过单文件大小上限(" + cfg.getMaxFileMb() + "MB): " + f.getName());
                VerificationRecorder.recordFallback("FILE_TOO_LARGE", f.getPath());
                continue;
            }
            if (type == MediaItem.Type.IMAGE && !checkImageBounds(f)) continue;
            if (type == MediaItem.Type.VIDEO && !checkVideoCodec(f)) continue;
            out.add(new MediaItem(f.getAbsolutePath(), type));
        }
        AppLogger.i("扫描完成: " + dirPath + " 可播放=" + out.size());
        return out;
    }

    /** 图片分辨率边界：inJustDecodeBounds 不占内存；解析失败保留文件以触发播放层占位图 */
    private boolean checkImageBounds(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            AppLogger.e("疑似损坏图片，保留以触发占位图降级: " + f.getName());
            VerificationRecorder.recordFallback("SUSPECT_CORRUPT", f.getPath());
            return true;
        }
        if (o.outWidth > cfg.getMaxImageWidth() || o.outHeight > cfg.getMaxImageHeight()) {
            AppLogger.w("超过分辨率上限 " + cfg.getMaxImageWidth() + "x" + cfg.getMaxImageHeight() + ": " + f.getName());
            VerificationRecorder.recordFallback("RESOLUTION_EXCEEDED", f.getPath());
            return false;
        }
        return true;
    }

    /** 视频编码边界：H.264(video/avc)；检测失败保留文件，由播放器解码结果决定降级 */
    private boolean checkVideoCodec(File f) {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(f.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat fmt = ex.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (!"video/avc".equals(mime)) {
                        AppLogger.w("视频编码不支持(需H.264, 实际" + mime + "): " + f.getName());
                        VerificationRecorder.recordFallback("UNSUPPORTED_CODEC", f.getPath());
                        return false;
                    }
                    break;
                }
            }
        } catch (Exception e) {
            AppLogger.w("编码检测失败，保留交由播放器处理: " + f.getName() + " " + e.getMessage());
        } finally {
            ex.release();
        }
        return true;
    }

    /** 背景音乐列表（仅音频） */
    public List<MediaItem> scanBgm(String dirPath) {
        List<MediaItem> out = new ArrayList<>();
        File dir = new File(dirPath);
        File[] files = (dir != null && dir.isDirectory()) ? dir.listFiles() : null;
        if (files == null) return out;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            MediaItem.Type t = typeOf(f.getName());
            if (t == MediaItem.Type.AUDIO) out.add(new MediaItem(f.getAbsolutePath(), t));
        }
        return out;
    }

    /** 合并本地目录与网络 URL，形成完整播放列表 */
    public List<MediaItem> buildPlaylist(String mediaDir, List<String> urls) {
        List<MediaItem> list = scanDirectory(mediaDir);
        if (urls != null) {
            for (String url : urls) {
                MediaItem.Type t = typeOf(url);
                if (t == null) t = MediaItem.Type.IMAGE; // URL 无扩展名时按图片处理
                list.add(new MediaItem(url, t));
            }
        }
        return list;
    }
}
