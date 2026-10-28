package org.whitedoggy.maplelibrary;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PresentationTest {
    static List<RawValue> cases() throws Exception {
        return ((RawValue.ArrayValue)RawValue.parse(Files.readString(Path.of(System.getProperty("fixtures"),"cases/presentation-v1.json")))).values();
    }
    static Map<String,RawValue> fields(RawValue raw) { return ((RawValue.ObjectValue)raw).fields(); }
    static String text(RawValue raw) { return ((RawValue.StringValue)raw).value(); }
    static FriendlyNode model(RawValue raw) {
        var c=fields(raw);return Document.parse(Source.valueOf(text(c.get("source"))),OfflineTest.json(c.get("raw"))).asModel();
    }
    static RawValue pointer(RawValue value,String path) {
        for(String token:path.substring(1).split("/"))value=value instanceof RawValue.ArrayValue array?array.values().get(Integer.parseInt(token)):fields(value).get(token);
        return value;
    }
    @TestFactory Stream<DynamicTest> sharedViews() throws Exception {
        return cases().stream().map(raw->DynamicTest.dynamicTest(text(fields(raw).get("id")),()-> {
            RawValue view=RawValue.parse(model(raw).toJson());
            fields(fields(raw).get("expected")).forEach((path,expected)->assertEquals(expected,pointer(view,path),path));
        }));
    }
    @Test void exportViews() throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        for(var c:cases())result.put(text(fields(c).get("id")),model(c).toView());
        Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/presentation.json"),Presentation.toJson(result));
    }
    @Test void typedItemPresetsAndImmutability() throws Exception {
        var equipment=(Models.Equipment)model(cases().getFirst());var item=equipment.weapon();
        assertEquals("합성 튜너",item.name());assertEquals(new BigDecimal("1122"),item.stats().attackPower());
        assertEquals(new BigDecimal("81"),item.options().scroll().attackPower());
        assertEquals(new BigDecimal("13"),item.potential().lines().getFirst().effects().getFirst().value());
        assertEquals("UNKNOWN",item.potential().lines().get(1).parseStatus());
        assertEquals(new BigDecimal("20"),item.soul().attackPower());assertEquals(4,item.soul().potential().amplificationLevel());
        assertEquals(2,equipment.currentPresetNumber());assertNotEquals(item.name(),equipment.preset(2).items().getFirst().name());
        assertEquals("합성 칭호",equipment.titlePreset(1).name());
        assertFalse(item.stats().toText().contains("DEX +0"));assertTrue(item.stats().toText(true).contains("DEX +0"));
        assertTrue(item.stats().toText().contains("공격력 +1122"));
        assertThrows(UnsupportedOperationException.class,()->equipment.items().clear());
        var view=item.toView();view.put("name","overwrite");assertEquals("합성 튜너",item.name());
        assertEquals("FIELD_UNAVAILABLE",assertThrows(MapleException.class,()->equipment.preset(1)).code());
        assertEquals("INVALID_REQUEST",assertThrows(MapleException.class,()->equipment.preset(0)).code());
    }
    @Test void allSourcesAndStatuses() throws Exception {
        Map<Source,Document> docs=new EnumMap<>(Source.class);
        for(var c:cases().subList(0,27)) {
            Source source=Source.valueOf(text(fields(c).get("source")));
            docs.put(source,Document.parse(source,OfflineTest.json(fields(c).get("raw"))));
        }
        assertEquals(27,docs.size());var c=new CharacterSheet("synthetic",docs).asCharacter();
        assertEquals("합성 캐릭터",c.name());assertEquals(9000,c.union().basic().level());
        assertEquals(-2,c.union().raider().blocks().getFirst().positions().getFirst().x());
        assertEquals(new BigDecimal("40"),c.union().raider().memberEffects().getFirst().effects().getFirst().value());
        assertEquals(new BigDecimal("123"),c.symbols().items().getFirst().growth().current());
        assertEquals(30,c.skills().sixth().items().getFirst().level());assertEquals(10,c.hexa().cores().cores().getFirst().level());
        assertEquals("데미지",c.hyperStats().items().getFirst().name());
        assertEquals("/pet_2_equipment",c.pets().items().getFirst().equipment().detail().pointer());
        assertEquals(c.toView().get("union"),c.union().toView());
        Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/character-view.json"),c.toJson());
    }
    @Test void unavailableAndMalformedAreExplicit() throws Exception {
        for(var status:List.of(Document.Status.NOT_REQUESTED,Document.Status.FETCH_FAILED,Document.Status.UNSUPPORTED)) {
            var c=new CharacterSheet("synthetic",Map.of(Source.EQUIPMENT,new Document(Source.EQUIPMENT,status,null))).asCharacter();
            assertEquals("DOCUMENT_UNAVAILABLE",assertThrows(MapleException.class,c::equipment).code());
            assertNull(c.toView().get("equipment"));
            assertEquals(new RawValue.StringValue(status.name()),pointer(RawValue.parse(c.toJson()),"/sources/EQUIPMENT/status"));
        }
        var raw=cases().stream().filter(c->text(fields(c).get("id")).equals("invalid-types")).findFirst().orElseThrow();
        var item=((Models.Equipment)model(raw)).items().getFirst();
        assertEquals("INVALID_FIELD",assertThrows(MapleException.class,item::starforce).code());
        assertEquals("INVALID_FIELD",assertThrows(MapleException.class,()->item.stats().str()).code());
        assertEquals("oops",item.detail().text("starforce").value());
    }
    @Test void convenienceFetchAndCancellation() {
        List<String> seen=new CopyOnWriteArrayList<>();
        try(var client=new ClientTest().client(request->{
            seen.add(request.uri().getPath());
            return CompletableFuture.completedFuture(new ClientTest().response(200,request.uri().getPath().endsWith("/id")?"{\"ocid\":\"synthetic\"}":"{\"symbol\":[]}"));
        },ClientConfig.defaults(),()->CompletableFuture.completedFuture(null))) {
            var c=client.character("합성 캐릭터",Set.of(Source.SYMBOLS)).join();
            assertTrue(c.symbols().items().isEmpty());c.symbols().toView();c.toJson();
            assertEquals(List.of("/maplestory/v1/id","/maplestory/v1/character/symbol-equipment"),seen);
            assertThrows(MapleException.class,()->c.union().basic());
        }
        var network=new CompletableFuture<HttpTransport.Response>();
        try(var client=new ClientTest().client(r->network,ClientConfig.defaults(),()->CompletableFuture.completedFuture(null))) {
            var task=client.character(CharacterRequest.ocid("synthetic"),Set.of(Source.SYMBOLS));task.cancel(true);
            assertTrue(network.isCancelled());
        }
    }
}
