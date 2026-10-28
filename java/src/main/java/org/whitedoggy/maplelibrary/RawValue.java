package org.whitedoggy.maplelibrary;

import java.util.*;
import java.math.BigDecimal;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;

/** Immutable JSON tree retaining original numeric tokens and unknown fields. */
public sealed interface RawValue {
    record ObjectValue(Map<String, RawValue> fields) implements RawValue {
        public ObjectValue { fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            if (fields.containsKey(null) || fields.containsValue(null)) throw new MapleException("INVALID_JSON"); }
    }
    record ArrayValue(List<RawValue> values) implements RawValue {
        public ArrayValue { values = List.copyOf(values); }
    }
    record StringValue(String value) implements RawValue {
        public StringValue { Objects.requireNonNull(value); }
    }
    record NumberValue(String token) implements RawValue {
        public NumberValue {
            if (token == null || !token.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))
                throw new MapleException("INVALID_JSON");
            decimal(token);
        }
    }
    record BooleanValue(boolean value) implements RawValue {}
    enum NullValue implements RawValue { INSTANCE }

    static BigDecimal decimal(String text) {
        if (text.length() > 1024) throw new MapleException("RESOURCE_LIMIT_EXCEEDED");
        try {
            String[] exponent = text.split("[eE]");
            if (exponent.length == 2 && new java.math.BigInteger(exponent[1]).abs().compareTo(java.math.BigInteger.valueOf(5120)) > 0)
                throw new MapleException("RESOURCE_LIMIT_EXCEEDED");
            BigDecimal value = new BigDecimal(text);
            if (Math.abs((long)value.scale()) > 4096) throw new MapleException("RESOURCE_LIMIT_EXCEEDED");
            return value;
        } catch (NumberFormatException error) { throw new MapleException("INVALID_JSON"); }
    }

    static RawValue parse(String json) {
        if (json == null) throw new MapleException("INVALID_JSON");
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16 * 1024 * 1024)
            throw new MapleException("RESOURCE_LIMIT_EXCEEDED");
        JsonFactory factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(128).maxNumberLength(1024).maxStringLength(16 * 1024 * 1024).build()).build();
        try (JsonParser parser = factory.createParser(ObjectReadContext.empty(), json)) {
            if (parser.nextToken() == null) throw new MapleException("INVALID_JSON");
            RawValue value = read(parser);
            if (parser.nextToken() != null) throw new MapleException("INVALID_JSON");
            return value;
        } catch (MapleException error) { throw error; }
        catch (tools.jackson.core.exc.StreamConstraintsException error) { throw new MapleException("RESOURCE_LIMIT_EXCEEDED"); }
        catch (Exception error) { throw new MapleException("INVALID_JSON"); }
    }

    private static RawValue read(JsonParser p) {
        return switch(p.currentToken()) {
            case START_OBJECT -> {
                Map<String, RawValue> map = new LinkedHashMap<>();
                while(p.nextToken() != JsonToken.END_OBJECT) {
                    if (p.currentToken() != JsonToken.PROPERTY_NAME) throw new MapleException("INVALID_JSON");
                    String key = p.currentName();
                    if (map.containsKey(key)) throw new MapleException("INVALID_JSON");
                    if(p.nextToken() == null) throw new MapleException("INVALID_JSON");
                    map.put(key, read(p));
                }
                yield new ObjectValue(map);
            }
            case START_ARRAY -> {
                List<RawValue> values = new ArrayList<>();
                while(p.nextToken() != JsonToken.END_ARRAY) {
                    if(p.currentToken() == null) throw new MapleException("INVALID_JSON");
                    values.add(read(p));
                }
                yield new ArrayValue(values);
            }
            case VALUE_STRING -> new StringValue(p.getString());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new NumberValue(p.getString());
            case VALUE_TRUE -> new BooleanValue(true);
            case VALUE_FALSE -> new BooleanValue(false);
            case VALUE_NULL -> NullValue.INSTANCE;
            default -> throw new MapleException("INVALID_JSON");
        };
    }
}
