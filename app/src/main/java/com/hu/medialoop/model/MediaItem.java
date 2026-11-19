package com.hu.medialoop.model;

/** 媒体项：路径 + 类型 + 是否占位图（异常降级标记） */
public final class MediaItem {
    public enum Type { IMAGE, VIDEO, AUDIO }

    public final String path;
    public final Type type;
    public final boolean placeholder;

    public MediaItem(String path, Type type) { this(path, type, false); }

    public MediaItem(String path, Type type, boolean placeholder) {
        this.path = path;
        this.type = type;
        this.placeholder = placeholder;
    }

    /** 占位图用空路径 + IMAGE 类型，供降级渲染 */
    public static MediaItem placeholder() {
        return new MediaItem("", Type.IMAGE, true);
    }

    public boolean isPlayable() { return type == Type.IMAGE || type == Type.VIDEO; }
}
