package com.hu.medialoop.network;

import android.content.Context;

import com.hu.medialoop.config.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 磁盘缓存：容量上限 + LRU/FIFO 淘汰。
 * 元数据在内存维护，重启后按文件修改时间重建索引；缓存文件保留在外部私有目录。
 */
public final class LruDiskCache {
    private static final class Entry {
        final File file;
        final long size;
        long lastAccess;
        final long created;
        Entry(File file, long size, long lastAccess) {
            this.file = file; this.size = size;
            this.lastAccess = lastAccess; this.created = lastAccess;
        }
    }

    private final File dir;
    private final long maxBytes;
    private final boolean useLru;
    private final LinkedHashMap<String, Entry> map;
    private long totalBytes;

    public LruDiskCache(Context ctx, int maxMb, String eviction) {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) base = ctx.getFilesDir();
        dir = new File(base, "cache_media");
        if (!dir.exists()) dir.mkdirs();
        maxBytes = (long) maxMb * 1024 * 1024;
        useLru = Constants.EVICTION_LRU.equals(eviction);
        // accessOrder=true 时 get 会把条目移到尾部，实现 LRU；false 则保持插入顺序，实现 FIFO
        map = new LinkedHashMap<>(32, 0.75f, useLru);
        rebuildIndex();
    }

    private void rebuildIndex() {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            if (f.isFile()) {
                Entry e = new Entry(f, f.length(), f.lastModified());
                map.put(f.getName(), e);
                totalBytes += e.size;
            }
        }
        trim();
    }

    private static String keyToName(String key) {
        return Integer.toHexString(key.hashCode()) + ".media";
    }

    /** 命中返回缓存文件；未命中返回 null。命中会更新 LRU 访问时间 */
    public File get(String key) {
        String name = keyToName(key);
        Entry e = map.get(name);
        if (e == null || !e.file.exists()) return null;
        e.lastAccess = System.currentTimeMillis();
        if (useLru) {
            map.remove(name);
            map.put(name, e); // 移到尾部 = 最近使用
        }
        return e.file;
    }

    /** 写入缓存并返回文件；容量超限时按策略淘汰 */
    public File put(String key, InputStream in) {
        String name = keyToName(key);
        File target = new File(dir, name);
        File tmp = new File(dir, name + ".tmp");
        try {
            FileOutputStream fos = new FileOutputStream(tmp);
            byte[] buf = new byte[16 * 1024]; // 小缓冲，低端机友好
            int n, written = 0;
            while ((n = in.read(buf)) != -1 && written < maxBytes) {
                fos.write(buf, 0, n);
                written += n;
            }
            fos.flush();
            fos.close();
            if (written >= maxBytes) { // 单个资源超过缓存总容量，不缓存
                tmp.delete();
                return null;
            }
            // 淘汰至可容纳
            while (totalBytes + written > maxBytes && !map.isEmpty()) evictOne();
            if (tmp.exists() && !tmp.renameTo(target)) {
                tmp.delete();
                return null;
            }
            Entry e = new Entry(target, written, System.currentTimeMillis());
            map.put(name, e);
            totalBytes += written;
            return target;
        } catch (Exception ex) {
            tmp.delete();
            return null;
        }
    }

    private void evictOne() {
        Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator();
        if (!it.hasNext()) return;
        Entry e = it.next().getValue();
        it.remove();
        if (e.file.exists()) e.file.delete();
        totalBytes -= e.size;
    }

    public void clear() {
        for (Entry e : map.values()) if (e.file.exists()) e.file.delete();
        map.clear();
        totalBytes = 0;
    }

    public int size() { return map.size(); }
    public long usedBytes() { return totalBytes; }
}
