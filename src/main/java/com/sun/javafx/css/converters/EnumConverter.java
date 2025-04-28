package com.sun.javafx.css.converters;

import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;

/**
 * Shim cho JavaFX 17 thay thế lớp cũ ở JavaFX 8.
 * Phải khớp cả 2 constructor:
 *   • EnumConverter()                – EmojisFX đôi lúc gọi
 *   • EnumConverter(Class<?>)        – EmojisFX gọi trong StyleableProperties
 */
public final class EnumConverter<T extends Enum<T>>
        extends StyleConverter<String,T> {

    /* Giữ tham chiếu enumClass nếu cần (không thực sự dùng) */
    private Class<T> enumClass;

    /** Constructor KHÔNG tham số (để bytecode new EnumConverter() chạy) */
    public EnumConverter() { }

    /** Constructor có tham số Class<?>  (bytecode new EnumConverter(Class) ) */
    @SuppressWarnings("unchecked")
    public EnumConverter(Class<?> enumClass) {
        this.enumClass = (Class<T>) enumClass;
    }

    /* ================= API getInstance cũ ================= */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> EnumConverter<E> getInstance(Class<E> cls) {
        return new EnumConverter<>(cls);
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> EnumConverter<E> getInstance() {
        return new EnumConverter<>();
    }

    /* convert() không dùng tới trong EmojisFX → trả null là đủ an toàn */
    @Override
    public T convert(ParsedValue<String,T> value, Font font) {
        return null;
    }
}
