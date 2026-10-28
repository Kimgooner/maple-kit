package org.whitedoggy.maplelibrary;
import java.util.concurrent.CompletableFuture;
/** Share one limiter across callers using the same API application. */
@FunctionalInterface public interface RateLimiter { CompletableFuture<Void> acquire(); }
