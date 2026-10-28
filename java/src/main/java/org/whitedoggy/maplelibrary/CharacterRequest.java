package org.whitedoggy.maplelibrary;

import java.time.LocalDate;
/** Exactly one target, current (null date) or historical date, and explicit partial mode. */
public record CharacterRequest(String nickname,String ocid,LocalDate onDate,boolean allowPartial) {
    public CharacterRequest {
        if((nickname==null)==(ocid==null) || (nickname!=null?nickname:ocid).isBlank())throw new MapleException("INVALID_REQUEST");
    }
    public static CharacterRequest nickname(String name){return new CharacterRequest(name,null,null,false);}
    public static CharacterRequest ocid(String id){return new CharacterRequest(null,id,null,false);}
    public CharacterRequest on(LocalDate date){return new CharacterRequest(nickname,ocid,java.util.Objects.requireNonNull(date),allowPartial);}
    public CharacterRequest partial(){return new CharacterRequest(nickname,ocid,onDate,true);}
    @Override public String toString(){return "CharacterRequest[target=<redacted>,date="+onDate+",partial="+allowPartial+"]";}
}
