package org.whitedoggy.maplelibrary;

import java.net.*;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleSupplier;

/** Async source-scoped collector. No API key is copied into returned sheets/errors. */
public final class MapleClient implements AutoCloseable {
    private String apiKey;
    private final ClientConfig config;private final RateLimiter limiter;private final HttpTransport transport;
    private final boolean ownsLimiter,ownsTransport;private final Clock clock;private final DoubleSupplier random;
    private final URI base;private final ScheduledExecutorService scheduler;
    private final Set<Operation> active=ConcurrentHashMap.newKeySet();private final Gate gate;private volatile boolean closed;
    public MapleClient(String apiKey){this(apiKey,ClientConfig.defaults(),null,null,Clock.systemUTC(),Math::random,URI.create("https://open.api.nexon.com"));}
    public MapleClient(String apiKey,ClientConfig config,RateLimiter limiter,HttpTransport transport,Clock clock,DoubleSupplier random,URI base){
        if(apiKey==null||apiKey.isBlank()||base==null||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null
            || (base.getPath()!=null&&!base.getPath().isEmpty()&&!base.getPath().equals("/"))
            || !(base.getScheme().equals("https") || (base.getScheme().equals("http") && Set.of("localhost","127.0.0.1","[::1]").contains(base.getHost()))))throw new MapleException("INVALID_REQUEST");
        this.apiKey=apiKey;this.config=Objects.requireNonNull(config);this.clock=Objects.requireNonNull(clock);this.random=Objects.requireNonNull(random);this.base=base;
        ownsLimiter=limiter==null;this.limiter=ownsLimiter?new PacingRateLimiter(5):limiter;
        ownsTransport=transport==null;this.transport=ownsTransport?new JdkTransport(config):transport;
        scheduler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"maple-client-timer");t.setDaemon(true);return t;});gate=new Gate(config.concurrency());
    }
    @Override public String toString(){return "MapleClient[apiKey=<redacted>]";}
    public CompletableFuture<CharacterSheet> getCharacter(CharacterRequest request){return getCharacterSelected(request,SourceRegistry.allSources());}
    public CompletableFuture<CharacterSheet> getCharacterSelected(CharacterRequest request,Set<Source> sources){
        Set<Source> expanded=SourceRegistry.expand(sources);Set<Source> requested=Set.copyOf(sources);
        if(request==null||closed)throw new MapleException("INVALID_REQUEST");
        if(request.onDate()!=null&&!request.onDate().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")))))throw new MapleException("INVALID_REQUEST");
        Operation op=new Operation();active.add(op);
        op.root.whenComplete((v,e)->{active.remove(op);op.cancelPending();});
        op.timer(scheduler.schedule(()->op.root.completeExceptionally(new ApiException("DEADLINE_EXCEEDED")),config.deadline().toMillis(),TimeUnit.MILLISECONDS));
        CompletableFuture<String> identity=request.ocid()!=null?CompletableFuture.completedFuture(request.ocid()):
            fetch(op,"/maplestory/v1/id",Map.of("character_name",request.nickname()),null,1).thenApply(f->{
                RawValue v=f.raw() instanceof RawValue.ObjectValue o?o.fields().get("ocid"):null;
                if(!(v instanceof RawValue.StringValue s)||s.value().isBlank())throw new ApiException("INVALID_DOCUMENT");return s.value();
            });
        op.track(identity).whenComplete((ocid,error)->{
            if(error!=null){op.fail(error);return;}if(op.root.isDone())return;
            Map<Source,Document> documents=new ConcurrentHashMap<>();List<CompletableFuture<?>> calls=new ArrayList<>();
            for(Source source:expanded.stream().sorted().toList()){
                LocalDate queryDate=request.onDate()!=null?request.onDate():LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
                if(!SourceRegistry.supportsDate(source,queryDate)) {
                    documents.put(source,new Document(source,Document.Status.UNSUPPORTED,null,null,new ApiException("DATE_NOT_SUPPORTED",source,null,0,false,0),0));continue;
                }
                Map<String,String> params=new LinkedHashMap<>();params.put("ocid",ocid);
                if(request.onDate()!=null)params.put("date",request.onDate().toString());String grade=SourceRegistry.grade(source);if(grade!=null)params.put("character_skill_grade",grade);
                var retained=new java.util.concurrent.atomic.AtomicReference<RawValue>();
                var call=fetch(op,SourceRegistry.path(source),params,source,1).thenApply(f->{
                    retained.set(f.raw());
                    Document doc=new Document(source,Document.Status.FETCHED,f.raw(),f.at(),null,f.attempts());
                    if(!doc.structurallyValid())throw new ApiException("INVALID_DOCUMENT",source,null,200,false,f.attempts());
                    if(grade!=null){RawValue g=((RawValue.ObjectValue)f.raw()).fields().get("character_skill_grade");
                        if(g instanceof RawValue.StringValue s&&!s.value().equals(grade))throw new ApiException("INVALID_DOCUMENT",source,null,200,false,f.attempts());}
                    return doc;
                }).handle((doc,failure)->{
                    if(failure==null){documents.put(source,doc);return null;}
                    Throwable cause=unwrap(failure);
                    if(cause instanceof CancellationException)throw new CompletionException(cause);
                    ApiException api=asApi(cause,source,0);
                    if(!request.allowPartial()||Set.of("AUTHENTICATION_FAILED","PERMISSION_DENIED").contains(api.code())){op.fail(api);throw new CompletionException(api);}
                    documents.put(source,new Document(source,Document.Status.FETCH_FAILED,retained.get(),clock.instant(),api,api.attempts()));return null;
                });
                calls.add(op.track(call));
            }
            op.track(CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new))).whenComplete((ignored,failure)->{
                if(failure!=null){op.fail(failure);return;}
                List<String> diagnostics=new ArrayList<>();
                if(request.onDate()!=null)for(Document doc:documents.values())if(doc.status()==Document.Status.FETCHED){
                    RawValue d=((RawValue.ObjectValue)doc.raw()).fields().get("date");
                    if(d instanceof RawValue.StringValue s)try{
                        LocalDate date=s.value().length()==10?LocalDate.parse(s.value()):OffsetDateTime.parse(s.value()).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate();
                        if(!date.equals(request.onDate()))diagnostics.add("SOURCE_DATE_MISMATCH:"+doc.source());
                    }catch(java.time.format.DateTimeParseException e){diagnostics.add("INVALID_FIELD:"+doc.source()+":/date");}
                }
                op.root.complete(new CharacterSheet(ocid,documents,"0.1","0.2",requested,request.onDate(),diagnostics));
            });
        });return op.root;
    }
    private record Fetched(RawValue raw,int attempts,Instant at) {}
    private CompletableFuture<Fetched> fetch(Operation op,String path,Map<String,String> params,Source source,int attempt){
        if(op.root.isDone())return CompletableFuture.failedFuture(new CancellationException());
        CompletableFuture<Fetched> output=new CompletableFuture<>();op.track(output);
        op.track(gate.acquire()).thenCompose(ignored->{
            if(op.root.isDone()){gate.release();return CompletableFuture.<HttpTransport.Response>failedFuture(new CancellationException());}
            CompletableFuture<Void> permit;
            try { permit=op.track(limiter.acquire()); }
            catch(Exception e) { gate.release(); return CompletableFuture.<HttpTransport.Response>failedFuture(e); }
            return permit.thenCompose(v->{
                if(op.root.isDone())return CompletableFuture.<HttpTransport.Response>failedFuture(new CancellationException());
                String query=String.join("&",params.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).toList());
                URI uri=base.resolve(path+"?"+query);
                CompletableFuture<HttpTransport.Response> sent;
                try{sent=op.track(transport.send(new HttpTransport.Request(uri,apiKey,config.requestTimeout(),config.maxBodyBytes())));}
                catch(Throwable e){sent=CompletableFuture.failedFuture(e);}
                CompletableFuture<HttpTransport.Response> bounded=new CompletableFuture<>();op.track(bounded);
                var timeout=scheduler.schedule(()->{bounded.completeExceptionally(new ApiException("REQUEST_TIMEOUT",source,null,0,true,attempt));},config.requestTimeout().toMillis(),TimeUnit.MILLISECONDS);op.timer(timeout);
                var network=sent;
                bounded.whenComplete((response,error)->{timeout.cancel(false);if(error!=null)network.cancel(true);});
                sent.whenComplete((response,error)->{if(error==null)bounded.complete(response);else bounded.completeExceptionally(error);});return bounded;
            }).whenComplete((v,e)->gate.release());
        }).whenComplete((response,failure)->{
            if(op.root.isDone()){output.cancel(true);return;}
            ApiException error;long retryAfter=0;
            if(failure!=null){Throwable e=unwrap(failure);if(e instanceof CancellationException){output.cancel(true);return;}error=asApi(e,source,attempt);}
            else{
                if(response.body().getBytes(StandardCharsets.UTF_8).length>config.maxBodyBytes()){output.completeExceptionally(new ApiException("RESOURCE_LIMIT_EXCEEDED",source,null,response.status(),false,attempt));return;}
                RawValue raw=null;
                try{raw=RawValue.parse(response.body());}catch(MapleException e){if(response.status()>=200&&response.status()<300){output.completeExceptionally(new ApiException(e.code(),source,null,response.status(),false,attempt));return;}}
                if(response.status()>=200&&response.status()<300&&raw instanceof RawValue.ObjectValue o&&!o.fields().containsKey("error")){
                    output.complete(new Fetched(raw,attempt,clock.instant()));return;
                }
                if(response.status()>=200&&response.status()<300&&!(raw instanceof RawValue.ObjectValue))error=new ApiException("INVALID_DOCUMENT",source,null,response.status(),false,attempt);
                else error=upstream(response.status(),raw,source,attempt);
                String retry=response.headers().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase("retry-after")).map(Map.Entry::getValue).findFirst().orElse(null);
                if(retry!=null)try{double seconds=Double.parseDouble(retry);retryAfter=Double.isFinite(seconds)?Math.max(0,(long)(seconds*1000)):Long.MAX_VALUE;}
                    catch(NumberFormatException e){try{retryAfter=Math.max(0,Duration.between(clock.instant(),ZonedDateTime.parse(retry,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());}catch(Exception ignored){}}
            }
            if(!error.retryable()||attempt>config.retries()){output.completeExceptionally(error);return;}
            long delay=Math.max(retryAfter,(long)(Math.min(3000,300*Math.pow(2,Math.min(attempt-1,10)))*Math.max(0,Math.min(1,random.getAsDouble()))));
            op.timer(scheduler.schedule(()->{
                if(op.root.isDone()){output.cancel(true);return;}
                fetch(op,path,params,source,attempt+1).whenComplete((value,e)->{if(e==null)output.complete(value);else output.completeExceptionally(e);});
            },delay,TimeUnit.MILLISECONDS));
        });return output;
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static Throwable unwrap(Throwable e){while((e instanceof CompletionException||e instanceof ExecutionException)&&e.getCause()!=null)e=e.getCause();return e;}
    private static ApiException asApi(Throwable e,Source source,int attempts){
        if(e instanceof ApiException a)return a;
        if(e instanceof MapleException m)return new ApiException(m.code(),source,null,0,false,attempts);
        if(e instanceof HttpTimeoutException)return new ApiException("REQUEST_TIMEOUT",source,null,0,true,attempts);
        boolean tls=false;for(Throwable c=e;c!=null;c=c.getCause())if(c instanceof javax.net.ssl.SSLHandshakeException)tls=true;
        return new ApiException("TRANSPORT_ERROR",source,null,0,!tls,attempts);
    }
    private static ApiException upstream(int status,RawValue raw,Source source,int attempt){
        String code=null;
        if(raw instanceof RawValue.ObjectValue o && o.fields().get("error") instanceof RawValue.ObjectValue e && e.fields().get("name") instanceof RawValue.StringValue s)code=s.value();
        Map<String,String> map=Map.ofEntries(Map.entry("OPENAPI00001","UPSTREAM_UNAVAILABLE"),Map.entry("OPENAPI00002","PERMISSION_DENIED"),Map.entry("OPENAPI00003","INVALID_IDENTIFIER"),Map.entry("OPENAPI00004","UPSTREAM_INVALID_REQUEST"),Map.entry("OPENAPI00005","AUTHENTICATION_FAILED"),Map.entry("OPENAPI00006","INVALID_API_ROUTE_OR_SCOPE"),Map.entry("OPENAPI00007","RATE_LIMITED"),Map.entry("OPENAPI00009","DATA_NOT_READY"),Map.entry("OPENAPI00010","UPSTREAM_UNAVAILABLE"),Map.entry("OPENAPI00011","UPSTREAM_UNAVAILABLE"));
        if(code!=null&&!map.containsKey(code))code=null;
        String name=code==null?(status==401?"AUTHENTICATION_FAILED":status==403?"PERMISSION_DENIED":status==429?"RATE_LIMITED":status>=500?"UPSTREAM_UNAVAILABLE":"UPSTREAM_INVALID_REQUEST"):map.get(code);
        return new ApiException(name,source,code,status,Set.of("RATE_LIMITED","UPSTREAM_UNAVAILABLE").contains(name),attempt);
    }
    private static final class Operation{
        final CompletableFuture<CharacterSheet> root=new CompletableFuture<>();final Set<CompletableFuture<?>> pending=ConcurrentHashMap.newKeySet();final Set<Future<?>> timers=ConcurrentHashMap.newKeySet();
        <T> CompletableFuture<T> track(CompletableFuture<T> f){pending.add(f);f.whenComplete((v,e)->pending.remove(f));if(root.isDone())f.cancel(true);return f;}
        void timer(Future<?> timer){timers.add(timer);if(root.isDone())timer.cancel(false);}
        void fail(Throwable e){root.completeExceptionally(unwrap(e));}
        void cancelPending(){pending.forEach(f->f.cancel(true));timers.forEach(f->f.cancel(false));}
    }
    private static final class Gate{
        final int limit;int used;final Queue<CompletableFuture<Void>> queue=new ArrayDeque<>();Gate(int limit){this.limit=limit;}
        synchronized CompletableFuture<Void> acquire(){if(used<limit){used++;return CompletableFuture.completedFuture(null);}var f=new CompletableFuture<Void>();queue.add(f);return f;}
        synchronized void release(){used--;while(!queue.isEmpty()){var next=queue.remove();if(next.isDone())continue;used++;if(next.complete(null))return;used--;}}
    }
    public void close(){closed=true;active.forEach(o->o.root.cancel(true));scheduler.shutdownNow();
        if(ownsTransport)((JdkTransport)transport).close();if(ownsLimiter)((PacingRateLimiter)limiter).close();apiKey="";
    }
}
