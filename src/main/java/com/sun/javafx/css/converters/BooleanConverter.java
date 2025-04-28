package com.sun.javafx.css.converters;

import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;

/** Shim thay thế BooleanConverter của JavaFX 8 cho JavaFX 17+. */
public final class BooleanConverter
        extends StyleConverter<ParsedValue<?, ?>, Boolean> {

    private static final StyleConverter<?, ?> INSTANCE = new BooleanConverter();

    /** Bắt buộc phải có ctor rỗng public. */
    public BooleanConverter() {}

    /* ==== API cũ mà EmojisFX cần ==== */
    public static StyleConverter<?, ?> getInstance() {
        return INSTANCE;
    }

    /* EmojisFX không dùng việc convert ‑ cứ trả null/false. */
    @Override
    public Boolean convert(ParsedValue<ParsedValue<?, ?>, Boolean> value,
                           Font font) {
        return Boolean.FALSE;
    }
}
