package org.whitedoggy.maplelibrary;
import java.time.Duration;
public record ClientConfig(int concurrency,Duration requestTimeout,Duration connectTimeout,Duration deadline,int retries,int maxBodyBytes) {
    public ClientConfig {
        if(concurrency<1 || retries<0 || maxBodyBytes<1 || maxBodyBytes>16*1024*1024 || requestTimeout==null || connectTimeout==null || deadline==null
            || requestTimeout.isNegative() || requestTimeout.isZero() || connectTimeout.isNegative() || connectTimeout.isZero() || deadline.isNegative() || deadline.isZero())throw new MapleException("INVALID_REQUEST");
    }
    public static ClientConfig defaults(){return new ClientConfig(4,Duration.ofSeconds(10),Duration.ofSeconds(5),Duration.ofSeconds(60),2,16*1024*1024);}
}
