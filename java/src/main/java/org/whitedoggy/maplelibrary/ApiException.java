package org.whitedoggy.maplelibrary;
/** Sanitized collection error; never includes request URL, key, or response body. */
public final class ApiException extends MapleException {
    private final Source source; private final String upstreamCode; private final int httpStatus,attempts; private final boolean retryable;
    public ApiException(String code,Source source,String upstreamCode,int httpStatus,boolean retryable,int attempts) {
        super(code);this.source=source;this.upstreamCode=upstreamCode;this.httpStatus=httpStatus;this.retryable=retryable;this.attempts=attempts;
    }
    public ApiException(String code) { this(code,null,null,0,false,0); }
    public Source source(){return source;} public String upstreamCode(){return upstreamCode;} public int httpStatus(){return httpStatus;}
    public boolean retryable(){return retryable;} public int attempts(){return attempts;}
}
