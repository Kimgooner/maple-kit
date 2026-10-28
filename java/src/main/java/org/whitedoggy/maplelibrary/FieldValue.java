package org.whitedoggy.maplelibrary;

import java.math.BigDecimal;

/** Presence and validity are independent of empty/zero values. */
public record FieldValue<T>(State state, T value, RawValue raw, String pointer) {
    public enum State { MISSING, NULL, VALUE, INVALID }
    public FieldValue {
        java.util.Objects.requireNonNull(state);
        java.util.Objects.requireNonNull(pointer);
        if (state == State.VALUE && value == null) throw new MapleException("INVALID_REQUEST");
        if (state != State.VALUE && value != null) throw new MapleException("INVALID_REQUEST");
    }
    static FieldValue<RawValue> of(RawValue raw, String pointer) {
        return raw == null ? new FieldValue<>(State.MISSING, null, null, pointer)
            : raw == RawValue.NullValue.INSTANCE ? new FieldValue<>(State.NULL, null, raw, pointer)
            : new FieldValue<>(State.VALUE, raw, raw, pointer);
    }
    public static FieldValue<BigDecimal> number(RawValue raw, String pointer) {
        var field = of(raw, pointer);
        if(field.state() != State.VALUE) return new FieldValue<>(field.state(), null, raw, pointer);
        String text = raw instanceof RawValue.NumberValue n ? n.token()
            : raw instanceof RawValue.StringValue s ? s.value() : null;
        if (text == null || (raw instanceof RawValue.StringValue && !text.matches("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")))
            return new FieldValue<>(State.INVALID, null, raw, pointer);
        try { return new FieldValue<>(State.VALUE, RawValue.decimal(text), raw, pointer); }
        catch (MapleException error) { return new FieldValue<>(State.INVALID, null, raw, pointer); }
    }
}
