package org.whitedoggy.maplelibrary;

import java.util.*;

/** One supplied API response. This type performs no network calls. */
public record Document(Source source, Status status, RawValue raw, java.time.Instant fetchedAt, MapleException error, int attempts) {
    public Document(Source source, Status status, RawValue raw) { this(source,status,raw,null,null,0); }
    public enum Status { FETCHED, NOT_REQUESTED, FETCH_FAILED, UNSUPPORTED }
    public Document {
        Objects.requireNonNull(source); Objects.requireNonNull(status);
        if(source==Source.SKILLS || source==Source.UNION) throw new MapleException("INVALID_REQUEST");
        if(status == Status.FETCHED ? !(raw instanceof RawValue.ObjectValue) : raw != null && (status!=Status.FETCH_FAILED || !(raw instanceof RawValue.ObjectValue)))
            throw new MapleException("INVALID_DOCUMENT");
    }
    public static Document parse(Source source, String json) { return new Document(source, Status.FETCHED, RawValue.parse(json)); }
    public EntityView view() {
        if(status != Status.FETCHED) throw new MapleException("DOCUMENT_UNAVAILABLE");
        return new EntityView(raw, "");
    }
    public FieldValue<List<EntityView>> symbols() {
        if(source != Source.SYMBOLS) throw new MapleException("INVALID_REQUEST");
        return view().entities("symbol");
    }
    public FieldValue<List<EntityView>> equipment() { return equipment(null); }
    public FieldValue<List<EntityView>> equipment(Integer preset) {
        if(source != Source.EQUIPMENT || (preset != null && preset < 1)) throw new MapleException("INVALID_REQUEST");
        return view().entities(preset == null ? "item_equipment" : "item_equipment_preset_" + preset);
    }
    public FieldValue<Integer> currentPresetNumber() {
        String key=source==Source.HYPER_STATS||source==Source.UNION_RAIDER?"use_preset_no":"preset_no";
        var field = view().number(key);
        if(field.state() != FieldValue.State.VALUE) return new FieldValue<>(field.state(), null, field.raw(), field.pointer());
        try {
            int value = field.value().intValueExact();
            if(value < 1) throw new ArithmeticException();
            return new FieldValue<>(FieldValue.State.VALUE, value, field.raw(), field.pointer());
        } catch(ArithmeticException e) { return new FieldValue<>(FieldValue.State.INVALID, null, field.raw(), field.pointer()); }
    }
    static boolean equipmentArray(String name) {
        return name.equals("item_equipment") || name.matches("item_equipment_preset_[1-9][0-9]*");
    }
    public boolean structurallyValid() {
        if(status != Status.FETCHED) return false;
        var object = (RawValue.ObjectValue)raw;
        for(var entry: object.fields().entrySet()) {
            String name = entry.getKey();
            boolean relevant = "array".equals(SourceRegistry.fields(source).get("/"+name)) || (source==Source.EQUIPMENT && equipmentArray(name));
            if(!relevant || entry.getValue() == RawValue.NullValue.INSTANCE) continue;
            if(!(entry.getValue() instanceof RawValue.ArrayValue array)) return false;
            if("object".equals(SourceRegistry.fields(source).get("/"+name+"/*")) && array.values().stream().anyMatch(v -> !(v instanceof RawValue.ObjectValue))) return false;
        }
        return true;
    }
    public EntityView primary() { return view(); }
    public FieldValue<List<EntityView>> collection(String name) { return view().entities(name); }
    public Map<String, FieldValue<RawValue>> presets() { return SourceRegistry.presets(this); }
    public List<OptionDecoder.LocatedOption> decodedOptions() { return OptionDecoder.documentOptions(this); }
    public List<String> diagnostics() { return SourceRegistry.diagnostics(this); }
}
