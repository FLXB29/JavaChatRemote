package com.sun.javafx.css.converters;

import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;

/** Shim tối giản cho JavaFX 17 thay thế PaintConverter cũ (FX 8). */
public final class PaintConverter
        extends StyleConverter<ParsedValue<?, ?>[], Paint> {

    private static final StyleConverter<?, ?> INSTANCE = new PaintConverter();

    /* bắt buộc: constructor rỗng */
    public PaintConverter() {}

    /* ===============================================
       API cũ mà EmojisFX mong đợi
       =============================================== */
    public static StyleConverter<?, ?> getInstance() {
        return INSTANCE;
    }

    /* Không thật sự dùng trong EmojisFX → trả null */
    @Override
    public Paint convert(ParsedValue<ParsedValue<?, ?>[], Paint> value,
                         Font font) {
        return null;
    }
}
