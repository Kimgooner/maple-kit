package org.whitedoggy.maplelibrary;

import java.util.*;
import java.math.BigDecimal;

/** Typed access into a shared raw tree; values are not copied into a second tree. */
public record EntityView(RawValue raw, String pointer) {
    public EntityView { Objects.requireNonNull(raw); Objects.requireNonNull(pointer); }
    public static String escape(String text) { return text.replace("~", "~0").replace("/", "~1"); }
    public FieldValue<RawValue> field(String name) {
        String path = pointer + "/" + escape(name);
        if (!(raw instanceof RawValue.ObjectValue object)) return new FieldValue<>(FieldValue.State.INVALID, null, raw, path);
        return FieldValue.of(object.fields().get(name), path);
    }
    public FieldValue<String> text(String name) {
        var field = field(name);
        if(field.state() != FieldValue.State.VALUE) return new FieldValue<>(field.state(), null, field.raw(), field.pointer());
        return field.raw() instanceof RawValue.StringValue text
            ? new FieldValue<>(FieldValue.State.VALUE, text.value(), field.raw(), field.pointer())
            : new FieldValue<>(FieldValue.State.INVALID, null, field.raw(), field.pointer());
    }
    public FieldValue<BigDecimal> number(String name) {
        var field = field(name);
        if(field.state() != FieldValue.State.VALUE) return new FieldValue<>(field.state(), null, field.raw(), field.pointer());
        return FieldValue.number(field.raw(), field.pointer());
    }
    public FieldValue<java.time.OffsetDateTime> timestamp(String name) {
        var value=text(name);
        if(value.state()!=FieldValue.State.VALUE)return new FieldValue<>(value.state(),null,value.raw(),value.pointer());
        try {return new FieldValue<>(FieldValue.State.VALUE,java.time.OffsetDateTime.parse(value.value()),value.raw(),value.pointer());}
        catch(java.time.format.DateTimeParseException e){return new FieldValue<>(FieldValue.State.INVALID,null,value.raw(),value.pointer());}
    }
    public FieldValue<EntityView> object(String name) {
        var field = field(name);
        if(field.state() != FieldValue.State.VALUE) return new FieldValue<>(field.state(), null, field.raw(), field.pointer());
        return field.raw() instanceof RawValue.ObjectValue
            ? new FieldValue<>(FieldValue.State.VALUE, new EntityView(field.raw(), field.pointer()), field.raw(), field.pointer())
            : new FieldValue<>(FieldValue.State.INVALID, null, field.raw(), field.pointer());
    }
    public FieldValue<List<EntityView>> entities(String name) {
        var field = field(name);
        if(field.state() != FieldValue.State.VALUE) return new FieldValue<>(field.state(), null, field.raw(), field.pointer());
        if(!(field.raw() instanceof RawValue.ArrayValue array)) return new FieldValue<>(FieldValue.State.INVALID, null, field.raw(), field.pointer());
        List<EntityView> entities = new ArrayList<>();
        for(int i=0; i<array.values().size(); i++) entities.add(new EntityView(array.values().get(i), field.pointer()+"/"+i));
        return new FieldValue<>(FieldValue.State.VALUE, List.copyOf(entities), field.raw(), field.pointer());
    }
}
