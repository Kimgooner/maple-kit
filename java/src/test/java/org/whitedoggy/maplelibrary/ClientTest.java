package org.whitedoggy.maplelibrary;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class ClientTest {
    MapleClient client(HttpTransport transport,ClientConfig config,RateLimiter limiter) {
        return new MapleClient("test-secret",config,limiter,transport,Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"),ZoneOffset.UTC),()->0,URI.create("https://example.test"));
    }
    HttpTransport.Response response(int code,String body){return new HttpTransport.Response(code,Map.of(),body);}
    @Test void fullAndGrouped() {
        List<URI> calls=new CopyOnWriteArrayList<>();
        try(var c=client(r->{calls.add(r.uri());return CompletableFuture.completedFuture(response(200,r.uri().getPath().endsWith("/id")?"{\"ocid\":\"opaque\"}":"{}"));},ClientConfig.defaults(),()->CompletableFuture.completedFuture(null))) {
            var sheet=c.getCharacter(CharacterRequest.nickname("synthetic")).join();
            assertEquals(27,sheet.documents().size());assertEquals(27,calls.size());
            assertEquals(Document.Status.UNSUPPORTED,sheet.document(Source.RING_EXCHANGE).status());
            assertEquals(5,calls.stream().filter(u->u.getQuery().contains("character_skill_grade=")).count());
            calls.clear();sheet=c.getCharacterSelected(CharacterRequest.ocid("opaque"),Set.of(Source.UNION,Source.UNION_RAIDER)).join();
            assertEquals(4,calls.size());assertEquals(4,sheet.documents().size());
        }
    }
    @Test void partialAndAuthentication() {
        for(int status:new int[]{400,401,403})try(var c=client(r->CompletableFuture.completedFuture(response(status,"{\"error\":{\"message\":\"test-secret\"}}")),ClientConfig.defaults(),()->CompletableFuture.completedFuture(null))) {
            var future=c.getCharacterSelected(CharacterRequest.ocid("opaque").partial(),Set.of(Source.SYMBOLS));
            if(status==400)assertEquals(Document.Status.FETCH_FAILED,future.join().document(Source.SYMBOLS).status());
            else {var e=assertThrows(CompletionException.class,future::join);assertInstanceOf(ApiException.class,e.getCause());assertFalse(e.toString().contains("test-secret"));}
        }
    }
    @Test void retriesAcquirePermits() {
        AtomicInteger calls=new AtomicInteger(),permits=new AtomicInteger();
        try(var c=client(r->CompletableFuture.completedFuture(response(calls.incrementAndGet()==1?429:200,"{}")),ClientConfig.defaults(),()->{permits.incrementAndGet();return CompletableFuture.completedFuture(null);})) {
            var d=c.getCharacterSelected(CharacterRequest.ocid("opaque"),Set.of(Source.SYMBOLS)).join().document(Source.SYMBOLS);
            assertEquals(2,d.attempts());assertEquals(2,permits.get());
        }
    }
    @Test void invalidBodies() {
        for(String body:List.of("not json","[]","{\"symbol\":{}}"))try(var c=client(r->CompletableFuture.completedFuture(response(200,body)),ClientConfig.defaults(),()->CompletableFuture.completedFuture(null))) {
            var future=c.getCharacterSelected(CharacterRequest.ocid("opaque"),Set.of(Source.SYMBOLS));
            var e=assertThrows(CompletionException.class,future::join);assertInstanceOf(ApiException.class,e.getCause());
        }
    }
    @Test void cancellationStopsTransport() {
        var network=new CompletableFuture<HttpTransport.Response>();
        try(var c=client(r->network,ClientConfig.defaults(),()->CompletableFuture.completedFuture(null))) {
            var task=c.getCharacterSelected(CharacterRequest.ocid("opaque"),Set.of(Source.SYMBOLS));task.cancel(true);
            assertTrue(network.isCancelled());assertTrue(task.isCancelled());
        }
    }
    @Test void originalOptionSpans() {
        String text="🧪 알 수 없음\n STR +12, DEX +3%";
        var options=OptionDecoder.decode(text);assertEquals(List.of("UNKNOWN","KNOWN","KNOWN"),options.stream().map(OptionDecoder.Option::status).toList());
        for(var o:options)assertEquals(o.rawText(),text.substring(text.offsetByCodePoints(0,o.start()),text.offsetByCodePoints(0,o.end())));
        var ap=OptionDecoder.decode("AP를 직접 투자한 LUK의 15% 만큼 DEX 증가").getFirst();assertNull(ap.value());assertEquals("INVESTED_AP.LUK",ap.expression().get("basis"));
        var o=Document.parse(Source.OTHER_STATS,"{\"x\":{\"option_type\":\"STR\",\"option_value\":\"+12\"}}").decodedOptions().getFirst();
        assertEquals("/x/option_value",o.pointer());assertEquals("+12",o.option().rawText());assertEquals(3,o.option().end());
    }
    @org.junit.jupiter.api.TestFactory java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> sharedOptions()throws Exception {
        var cases=(RawValue.ArrayValue)RawValue.parse(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("fixtures"),"cases/options-v1.json")));
        return cases.values().stream().map(raw->{var c=((RawValue.ObjectValue)raw).fields();String text=OfflineTest.text(c.get("text"));
            return org.junit.jupiter.api.DynamicTest.dynamicTest(text,()->{
                var options=OptionDecoder.decode(text);
                var actual=OfflineTest.strings(options.stream().map(o->String.join("|",o.status(),String.join(",",o.targets()),o.value()==null?"-":o.value().stripTrailingZeros().toPlainString(),o.unit(),String.join(",",o.conditions()),o.ruleId())).toList());
                assertEquals(c.get("expected"),actual);
                if(c.get("expression") instanceof RawValue.ObjectValue e) {
                    Map<String,String> expected=new HashMap<>();e.fields().forEach((k,v)->expected.put(k,OfflineTest.text(v)));
                    assertEquals(expected,options.getFirst().expression());
                }
                for(var o:options)assertEquals(o.rawText(),text.substring(text.offsetByCodePoints(0,o.start()),text.offsetByCodePoints(0,o.end())));
            });});
    }
    @Test void deadlineAndBodyBound() {
        var network=new CompletableFuture<HttpTransport.Response>();
        var config=new ClientConfig(1,Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofMillis(20),0,10);
        try(var c=client(r->network,config,()->CompletableFuture.completedFuture(null))) {
            var e=assertThrows(CompletionException.class,()->c.getCharacterSelected(CharacterRequest.ocid("opaque"),Set.of(Source.SYMBOLS)).join());
            assertEquals("DEADLINE_EXCEEDED",((ApiException)e.getCause()).code());assertThrows(CancellationException.class,()->network.get(1,TimeUnit.SECONDS));
        }
        try(var c=client(r->CompletableFuture.completedFuture(response(200,"{\"long\":123456789}")),new ClientConfig(1,Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofSeconds(60),0,10),()->CompletableFuture.completedFuture(null))) {
            var e=assertThrows(CompletionException.class,()->c.getCharacterSelected(CharacterRequest.ocid("opaque"),Set.of(Source.SYMBOLS)).join());
            assertEquals("RESOURCE_LIMIT_EXCEEDED",((ApiException)e.getCause()).code());
        }
    }
    @Test void partialKeepsRawAndTimestamp() {
        try(var c=client(r->CompletableFuture.completedFuture(response(200,"{\"symbol\":{},\"future\":42}")),ClientConfig.defaults(),()->CompletableFuture.completedFuture(null))) {
            var d=c.getCharacterSelected(CharacterRequest.ocid("opaque").partial(),Set.of(Source.SYMBOLS)).join().document(Source.SYMBOLS);
            assertNotNull(d.raw());assertEquals(Document.Status.FETCH_FAILED,d.status());
        }
        var view=Document.parse(Source.BASIC,"{\"date\":\"2026-01-01T00:00+09:00\",\"naive\":\"2026-01-01T00:00\"}").view();
        assertEquals(FieldValue.State.VALUE,view.timestamp("date").state());assertEquals(FieldValue.State.INVALID,view.timestamp("naive").state());
    }
    @org.junit.jupiter.api.TestFactory java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> observedSchemas()throws Exception {
        var cases=(RawValue.ArrayValue)RawValue.parse(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("fixtures"),"cases/schema-v1.json")));
        return cases.values().stream().map(raw->{var c=((RawValue.ObjectValue)raw).fields();String source=OfflineTest.text(c.get("source"));
            return org.junit.jupiter.api.DynamicTest.dynamicTest(source,()->{
                var d=Document.parse(Source.valueOf(source),OfflineTest.json(c.get("json")));
                assertTrue(d.structurallyValid());assertTrue(d.presets().containsKey(OfflineTest.text(c.get("preset"))));
                assertFalse(d.diagnostics().stream().anyMatch(x->x.startsWith("UNKNOWN_FIELD:")||x.startsWith("SCHEMA_TYPE_DIFFERENCE:")));
                assertEquals(Integer.parseInt(((RawValue.NumberValue)c.get("extensions")).token()),d.diagnostics().stream().filter(x->x.startsWith("OBSERVED_SCHEMA_EXTENSION:")).count());
            });});
    }
}
