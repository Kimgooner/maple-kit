package org.whitedoggy.maplelibrary;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
/** Injectable async transport. Externally supplied transports remain caller-owned. */
@FunctionalInterface public interface HttpTransport {
    CompletableFuture<Response> send(Request request);
    record Request(URI uri,String apiKey,Duration timeout,int maxBodyBytes) {
        @Override public String toString(){return "Request[uri=<redacted>,apiKey=<redacted>]";}
    }
    record Response(int status,Map<String,String> headers,String body) {
        public Response{headers=Map.copyOf(headers);}
        @Override public String toString(){return "Response[status="+status+",body=<redacted>]";}
    }
}
