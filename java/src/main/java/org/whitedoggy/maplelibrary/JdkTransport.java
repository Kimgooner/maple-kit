package org.whitedoggy.maplelibrary;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/** Bounded response subscriber; no blocking reads on the async request path. */
final class JdkTransport implements HttpTransport,AutoCloseable {
    private final HttpClient client;
    JdkTransport(ClientConfig config){client=HttpClient.newBuilder().connectTimeout(config.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();}
    public CompletableFuture<Response> send(Request request){
        HttpRequest http=HttpRequest.newBuilder(request.uri()).timeout(request.timeout()).header("x-nxopen-api-key",request.apiKey()).header("Accept-Encoding","identity").GET().build();
        CompletableFuture<HttpResponse<byte[]>> sent=client.sendAsync(http,info->new BoundedBody(request.maxBodyBytes()));
        CompletableFuture<Response> result=sent.thenApply(response->{
            byte[] bytes=response.body();
            if(response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip")) {
                try(var gzip=new java.util.zip.GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    bytes=gzip.readNBytes(request.maxBodyBytes()+1);
                    if(bytes.length>request.maxBodyBytes())throw new ApiException("RESOURCE_LIMIT_EXCEEDED");
                }catch(IOException e){throw new ApiException("INVALID_JSON");}
            }
            try {
                String body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                Map<String,String> headers=new HashMap<>();response.headers().map().forEach((k,v)->headers.put(k.toLowerCase(Locale.ROOT),String.join(",",v)));
                return new Response(response.statusCode(),headers,body);
            }catch(CharacterCodingException e){throw new ApiException("INVALID_JSON");}
        });
        result.whenComplete((v,e)->{if(result.isCancelled())sent.cancel(true);});return result;
    }
    public void close(){client.shutdownNow();}
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final int limit;private Flow.Subscription subscription;
        BoundedBody(int limit){this.limit=limit;result.whenComplete((v,e)->{if(result.isCancelled()&&subscription!=null)subscription.cancel();});}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(ByteBuffer buffer:buffers){if(buffer.remaining()>limit-bytes.size()){subscription.cancel();result.completeExceptionally(new ApiException("RESOURCE_LIMIT_EXCEEDED"));return;}
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}
            subscription.request(1);
        }
        public void onError(Throwable e){result.completeExceptionally(e);}public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
