package org.whitedoggy.maplelibrary;
/** Stable, sanitized failure code; input bodies and identifiers are not included. */
public class MapleException extends IllegalArgumentException {
    private final String code;
    public MapleException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
