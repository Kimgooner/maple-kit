package org.whitedoggy.maplelibrary;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class OfflineTest {
    private static final Path FIXTURES=Path.of(System.getProperty("fixtures"));
    static RawValue.ObjectValue object(RawValue value) { return (RawValue.ObjectValue)value; }
    static String text(RawValue value) { return ((RawValue.StringValue)value).value(); }
    static List<RawValue> cases() throws Exception { return ((RawValue.ArrayValue)RawValue.parse(Files.readString(FIXTURES.resolve("cases/offline-v1.json")))).values(); }
    static CharacterSheet sheet(RawValue raw,String rules) {
        Map<Source,Document> docs=new EnumMap<>(Source.class);
        object(raw).fields().forEach((k,v)->docs.put(Source.valueOf(k),Document.parse(Source.valueOf(k),text(v))));
        return new CharacterSheet("synthetic-ocid",docs,"0.1",rules);
    }
    static RawValue strings(List<String> values) { return new RawValue.ArrayValue(values.stream().map(s->(RawValue)new RawValue.StringValue(s)).toList()); }
    static RawValue projection(SheetDiff diff) {
        Map<String,RawValue> out=new LinkedHashMap<>();
        out.put("changes",strings(diff.changes().stream().map(c->String.join("|", c.source().name(),c.beforePointer(),c.afterPointer(),c.kind(),
            c.before().state().name(),c.after().state().name(),c.delta()==null?"-":c.delta().signum()==0?"0":c.delta().stripTrailingZeros().toPlainString(),Boolean.toString(c.semanticChanged()))).toList()));
        out.put("compared",strings(diff.comparedDocuments().stream().map(Enum::name).toList()));
        out.put("skipped",strings(diff.skippedDocuments().stream().map(s->s.source()+":"+s.reason()).toList()));
        out.put("diagnostics",strings(diff.diagnostics()));return new RawValue.ObjectValue(out);
    }
    static RawValue run(RawValue raw) {
        var c=object(raw).fields(); String rules=c.containsKey("afterRules")?text(c.get("afterRules")):"0.1";
        return projection(sheet(c.get("before"),"0.1").compareTo(sheet(c.get("after"),rules)));
    }
    @TestFactory Stream<DynamicTest> sharedExpected() throws Exception {
        return cases().stream().map(c->DynamicTest.dynamicTest(text(object(c).fields().get("id")),()->assertEquals(object(c).fields().get("expected"),run(c))));
    }
    @Test void exportResults() throws Exception {
        Map<String,RawValue> results=new LinkedHashMap<>();
        for(var c:cases()) results.put(text(object(c).fields().get("id")),run(c));
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/conformance.json"),json(new RawValue.ObjectValue(results)));
    }
    // Test-only projection encoder, deliberately not a public persistence format.
    static String json(RawValue raw) {
        return switch(raw) {
            case RawValue.StringValue s -> quote(s.value());
            case RawValue.ObjectValue o -> "{"+String.join(",",o.fields().entrySet().stream().map(e->quote(e.getKey())+":"+json(e.getValue())).toList())+"}";
            case RawValue.ArrayValue a -> "["+String.join(",",a.values().stream().map(OfflineTest::json).toList())+"]";
            case RawValue.NumberValue n -> n.token();
            case RawValue.BooleanValue b -> Boolean.toString(b.value());
            case RawValue.NullValue ignored -> "null";
        };
    }
    static String quote(String value) {
        StringBuilder b=new StringBuilder("\"");
        for(char c:value.toCharArray()) {
            if(c=='"' || c=='\\') b.append('\\').append(c);
            else if(c<32) b.append(String.format("\\u%04x",(int)c)); else b.append(c);
        }
        return b.append('"').toString();
    }
    @Test void invalidJsonAndLimits() {
        for(String input:List.of("{\"x\":1,\"x\":2}","NaN","{} {}","[1,]"))
            assertEquals("INVALID_JSON",assertThrows(MapleException.class,()->RawValue.parse(input)).code());
        for(String input:List.of("{\"x\":1e999999}","[".repeat(129)+"0"+"]".repeat(129)))
            assertEquals("RESOURCE_LIMIT_EXCEEDED",assertThrows(MapleException.class,()->RawValue.parse(input)).code());
    }
    @Test void viewsPreserveValues() throws Exception {
        var symbols=Document.parse(Source.SYMBOLS,Files.readString(FIXTURES.resolve("raw/symbols.json")));
        var symbol=symbols.symbols().value().getFirst();
        assertEquals(new BigDecimal("10"),symbol.number("symbol_str").value());
        assertEquals(new BigDecimal("20"),symbol.number("symbol_dex").value());
        assertEquals(BigDecimal.ZERO,symbol.number("symbol_hp").value());
        assertEquals(new RawValue.BooleanValue(true),object(symbol.field("unknown").raw()).fields().get("keep"));
        assertThrows(UnsupportedOperationException.class,()->object(symbols.raw()).fields().clear());
        var equipment=Document.parse(Source.EQUIPMENT,Files.readString(FIXTURES.resolve("raw/equipment.json")));
        assertTrue(equipment.equipment(1).value().isEmpty());
        assertEquals(FieldValue.State.MISSING,equipment.equipment(2).state());
        assertEquals(1,equipment.currentPresetNumber().value());
        var weapon=equipment.equipment().value().getFirst();
        assertEquals(new BigDecimal("30"),weapon.object("item_total_option").value().number("attack_power").value());
        assertEquals(new BigDecimal("10"),weapon.object("item_base_option").value().number("attack_power").value());
    }
    @Test void numberStates() {
        var entity=Document.parse(Source.SYMBOLS,"{\"symbol\":[{\"a\":null,\"b\":\"\",\"c\":\"bad\",\"d\":4.50,\"e\":\"0\"}]}").symbols().value().getFirst();
        assertEquals("4.50",((RawValue.NumberValue)entity.field("d").raw()).token());
        assertEquals(List.of("MISSING","NULL","INVALID","INVALID","VALUE","VALUE"),
            Stream.of("missing","a","b","c","d","e").map(k->entity.number(k).state().name()).toList());
        assertEquals("",entity.text("b").value());
    }
    @Test void identityAndStatus() {
        assertEquals("INVALID_DOCUMENT",assertThrows(MapleException.class,()->Document.parse(Source.SYMBOLS,"[]")).code());
        var a=new CharacterSheet("secret-ocid",Map.of());
        assertFalse(a.toString().contains("secret-ocid"));
        assertEquals("SUBJECT_MISMATCH",assertThrows(MapleException.class,()->a.compareTo(new CharacterSheet("other",Map.of()))).code());
        assertEquals("INCOMPATIBLE_CONTRACT",assertThrows(MapleException.class,()->a.compareTo(new CharacterSheet("secret-ocid",Map.of(),"0.2","0.1"))).code());
        var failed=new CharacterSheet("secret-ocid",Map.of(Source.SYMBOLS,new Document(Source.SYMBOLS,Document.Status.FETCH_FAILED,null)));
        assertTrue(a.compareTo(failed).changes().isEmpty());
    }
    @Test void defensiveCopies() {
        Map<String,RawValue> fields=new HashMap<>();fields.put("x",new RawValue.StringValue("kept"));
        var raw=new RawValue.ObjectValue(fields);fields.clear();assertTrue(raw.fields().containsKey("x"));
        Map<Source,Document> docs=new HashMap<>();docs.put(Source.SYMBOLS,Document.parse(Source.SYMBOLS,"{}"));
        var sheet=new CharacterSheet("synthetic",docs);docs.clear();assertEquals(Document.Status.FETCHED,sheet.document(Source.SYMBOLS).status());
    }
}
