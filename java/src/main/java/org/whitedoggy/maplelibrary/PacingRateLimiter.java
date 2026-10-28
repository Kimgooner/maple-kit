package org.whitedoggy.maplelibrary;
import java.util.concurrent.*;
/** Monotonic, burst-one pacing. Closing an owned limiter cancels pending waits. */
public final class PacingRateLimiter implements RateLimiter,AutoCloseable {
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"maple-rate-limiter");t.setDaemon(true);return t;});
    private final long interval;private long next=System.nanoTime();
    private final java.util.Set<CompletableFuture<Void>> pending=ConcurrentHashMap.newKeySet();
    private boolean closed;
    public PacingRateLimiter(double rate){if(!Double.isFinite(rate)||rate<=0)throw new MapleException("INVALID_REQUEST");interval=Math.max(1,(long)(1e9/rate));}
    public synchronized CompletableFuture<Void> acquire(){
        if(closed)return CompletableFuture.failedFuture(new ApiException("CLIENT_CLOSED"));
        long now=System.nanoTime(),at=Math.max(now,next);next=at+interval;
        CompletableFuture<Void> result=new CompletableFuture<>();pending.add(result);
        ScheduledFuture<?> timer=scheduler.schedule(()->result.complete(null),Math.max(0,at-now),TimeUnit.NANOSECONDS);
        result.whenComplete((v,e)->{timer.cancel(false);pending.remove(result);});return result;
    }
    public synchronized void close(){closed=true;pending.forEach(f->f.cancel(false));scheduler.shutdownNow();}
}
