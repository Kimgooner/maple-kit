package org.whitedoggy.maplelibrary;
import java.util.*;
/** Convenient, network-free access to a collected sheet. */
public record MapleCharacter(CharacterSheet sheet) {
 public MapleCharacter { Objects.requireNonNull(sheet); }
 public FriendlyNode source(Source source) { var info=Presentation.SOURCES.get(source.name()); if(info==null)throw new MapleException("INVALID_REQUEST"); return Models.create(info.get(1),sheet.document(source).view()); }
 public Models.Basic basic() { return (Models.Basic)source(Source.BASIC); }
 public Models.ReportedStats stats() { return (Models.ReportedStats)source(Source.REPORTED_STATS); }
 public Models.Equipment equipment() { return (Models.Equipment)source(Source.EQUIPMENT); }
 public Models.Symbols symbols() { return (Models.Symbols)source(Source.SYMBOLS); }
 public Models.HyperStats hyperStats() { return (Models.HyperStats)source(Source.HYPER_STATS); }
 public Models.Propensity propensity() { return (Models.Propensity)source(Source.PROPENSITY); }
 public Models.Abilities abilities() { return (Models.Abilities)source(Source.ABILITIES); }
 public Models.Cash cash() { return (Models.Cash)source(Source.CASH); }
 public Models.SetEffects setEffects() { return (Models.SetEffects)source(Source.SET_EFFECTS); }
 public Models.Android android() { return (Models.Android)source(Source.ANDROID); }
 public Models.Pets pets() { return (Models.Pets)source(Source.PETS); }
 public Models.LinkSkills linkSkills() { return (Models.LinkSkills)source(Source.LINK_SKILLS); }
 public Models.VMatrix vMatrix() { return (Models.VMatrix)source(Source.V_MATRIX); }
 public Models.OtherStats otherStats() { return (Models.OtherStats)source(Source.OTHER_STATS); }
 public Models.HexaCores hexaCores() { return (Models.HexaCores)source(Source.HEXA_CORES); }
 public Models.HexaStats hexaStats() { return (Models.HexaStats)source(Source.HEXA_STATS); }
 public Models.RingExchange ringExchange() { return (Models.RingExchange)source(Source.RING_EXCHANGE); }
 public Models.RingReserve ringReserve() { return (Models.RingReserve)source(Source.RING_RESERVE); }
 public Models.UnionBasic unionBasic() { return (Models.UnionBasic)source(Source.UNION_BASIC); }
 public Models.UnionRaider unionRaider() { return (Models.UnionRaider)source(Source.UNION_RAIDER); }
 public Models.UnionArtifact unionArtifact() { return (Models.UnionArtifact)source(Source.UNION_ARTIFACT); }
 public Models.UnionChampion unionChampion() { return (Models.UnionChampion)source(Source.UNION_CHAMPION); }
 public Models.Skills skillsZero() { return (Models.Skills)source(Source.SKILL_0); }
 public Models.Skills skillsHyperPassive() { return (Models.Skills)source(Source.SKILL_HYPER_PASSIVE); }
 public Models.Skills skillsHyperActive() { return (Models.Skills)source(Source.SKILL_HYPER_ACTIVE); }
 public Models.Skills skillsFifth() { return (Models.Skills)source(Source.SKILL_5); }
 public Models.Skills skillsSixth() { return (Models.Skills)source(Source.SKILL_6); }
 public Union union() { return new Union(this); }
 public record Union(MapleCharacter character) {
  public Models.UnionBasic basic() { return character.unionBasic(); }
  public Models.UnionRaider raider() { return character.unionRaider(); }
  public Models.UnionArtifact artifact() { return character.unionArtifact(); }
  public Models.UnionChampion champion() { return character.unionChampion(); }
  public Map<String,Object> toView() { return Presentation.map("basic",Presentation.sourceView(character,Source.UNION_BASIC),"raider",Presentation.sourceView(character,Source.UNION_RAIDER),"artifact",Presentation.sourceView(character,Source.UNION_ARTIFACT),"champion",Presentation.sourceView(character,Source.UNION_CHAMPION)); }
  public String toJson() { return Presentation.toJson(toView()); }
 }
 public Skills skills() { return new Skills(this); }
 public record Skills(MapleCharacter character) {
  public Models.Skills zero() { return character.skillsZero(); }
  public Models.Skills hyperPassive() { return character.skillsHyperPassive(); }
  public Models.Skills hyperActive() { return character.skillsHyperActive(); }
  public Models.Skills fifth() { return character.skillsFifth(); }
  public Models.Skills sixth() { return character.skillsSixth(); }
  public Map<String,Object> toView() { return Presentation.map("zero",Presentation.sourceView(character,Source.SKILL_0),"hyper_passive",Presentation.sourceView(character,Source.SKILL_HYPER_PASSIVE),"hyper_active",Presentation.sourceView(character,Source.SKILL_HYPER_ACTIVE),"fifth",Presentation.sourceView(character,Source.SKILL_5),"sixth",Presentation.sourceView(character,Source.SKILL_6)); }
  public String toJson() { return Presentation.toJson(toView()); }
 }
 public Hexa hexa() { return new Hexa(this); }
 public record Hexa(MapleCharacter character) {
  public Models.HexaCores cores() { return character.hexaCores(); }
  public Models.HexaStats stats() { return character.hexaStats(); }
  public Map<String,Object> toView() { return Presentation.map("cores",Presentation.sourceView(character,Source.HEXA_CORES),"stats",Presentation.sourceView(character,Source.HEXA_STATS)); }
  public String toJson() { return Presentation.toJson(toView()); }
 }
 public SpecialRings specialRings() { return new SpecialRings(this); }
 public record SpecialRings(MapleCharacter character) {
  public Models.RingExchange exchange() { return character.ringExchange(); }
  public Models.RingReserve reserve() { return character.ringReserve(); }
  public Map<String,Object> toView() { return Presentation.map("exchange",Presentation.sourceView(character,Source.RING_EXCHANGE),"reserve",Presentation.sourceView(character,Source.RING_RESERVE)); }
  public String toJson() { return Presentation.toJson(toView()); }
 }
 public String name() { return basic().name(); }
 public Integer level() { return basic().level(); }
 public String job() { return basic().job(); }
 public String world() { return basic().world(); }
 public Map<String,Object> toView() { return Presentation.characterView(this); }
 public String toJson() { return Presentation.toJson(toView()); }
}
