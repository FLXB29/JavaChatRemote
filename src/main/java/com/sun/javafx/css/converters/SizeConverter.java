package com.sun.javafx.css.converters;

import javafx.css.ParsedValue;
import javafx.css.Size;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;

/** Shim SizeConverter của JavaFX 8 cho JavaFX 17+. */
public final class SizeConverter
        extends StyleConverter<ParsedValue<?, Size>, Number> {

    private static final StyleConverter<?, ?> INSTANCE = new SizeConverter();

    /** bắt buộc ctor rỗng public */
    public SizeConverter() {}

    /* ==== API cũ mà EmojisFX 1.1 yêu cầu ==== */
    public static StyleConverter<?, ?> getInstance() {
        return INSTANCE;
    }

    /* EmojisFX chỉ cần có lớp tồn tại, convert không dùng → trả 0 */
    @Override
    public Number convert(ParsedValue<ParsedValue<?, Size>, Number> value,
                          Font font) {
        return 0;
    }
}
