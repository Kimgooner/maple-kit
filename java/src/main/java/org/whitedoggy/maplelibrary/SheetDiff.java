package org.whitedoggy.maplelibrary;

import java.util.*;
import java.math.BigDecimal;

/** Pure data comparison: unknown values remain visible, numeric deltas are path-bound. */
public record SheetDiff(List<Source> comparedDocuments, List<Skipped> skippedDocuments,
                        List<Change> changes, List<String> diagnostics) {
    public SheetDiff {
        comparedDocuments=List.copyOf(comparedDocuments); skippedDocuments=List.copyOf(skippedDocuments);
        changes=List.copyOf(changes); diagnostics=List.copyOf(diagnostics);
    }
    public record Skipped(Source source, String reason) {}
    public record Change(Source source, String beforePointer, String afterPointer, String kind,
                         FieldValue<?> before, FieldValue<?> after, boolean semanticChanged, BigDecimal delta) {}
    private static final Set<String> SYMBOL_NUMBERS = Set.of("symbol_level", "symbol_force", "symbol_str", "symbol_dex", "symbol_int", "symbol_luk", "symbol_hp", "symbol_growth_count", "symbol_require_growth_count");
    private static final Set<String> OPTION_GROUPS = Set.of("item_base_option", "item_add_option", "item_etc_option", "item_starforce_option", "item_total_option", "item_exceptional_option");
    private static final Set<String> OPTION_NUMBERS = Set.of("str", "dex", "int", "luk", "max_hp", "max_mp", "attack_power", "magic_power", "armor", "speed", "jump", "all_stat", "damage", "boss_damage", "ignore_monster_armor", "max_hp_rate", "max_mp_rate");
    private static boolean numeric(Source source, String path) {
        String[] t=path.split("/", -1);
        if(source == Source.SYMBOLS) return t.length==4 && t[1].equals("symbol") && t[2].matches("[0-9]+") && SYMBOL_NUMBERS.contains(t[3]);
        if(source==Source.REPORTED_STATS)return t.length==4 && t[1].equals("final_stat") && t[2].matches("[0-9]+") && t[3].equals("stat_value");
        String pattern=path.replaceAll("/[0-9]+(?=/|$)","/*");
        String type=SourceRegistry.fields(source).get(pattern);
        if("integer".equals(type)||"number".equals(type))return true;
        return source==Source.EQUIPMENT && t.length==5 && Document.equipmentArray(t[1])
            && t[2].matches("[0-9]+") && OPTION_GROUPS.contains(t[3]) && OPTION_NUMBERS.contains(t[4]);
    }
    private static int unicode(String a, String b) {
        var ai=a.codePoints().iterator(); var bi=b.codePoints().iterator();
        while(ai.hasNext() && bi.hasNext()) { int c=Integer.compare(ai.nextInt(),bi.nextInt()); if(c!=0) return c; }
        return Boolean.compare(ai.hasNext(),bi.hasNext());
    }
    private static SortedSet<String> keys(Set<String> a, Set<String> b) {
        SortedSet<String> all=new TreeSet<>(SheetDiff::unicode); all.addAll(a);all.addAll(b);return all;
    }
    private static int pathOrder(String a, String b) {
        String[] aa=a.split("/",-1), bb=b.split("/",-1);
        String prefix="item_equipment_preset_";
        for(int i=0;i<Math.min(aa.length,bb.length);i++) {
            String x=aa[i], y=bb[i];
            boolean xn=x.matches(prefix+"[0-9]+"), yn=y.matches(prefix+"[0-9]+");
            int c=unicode(xn?prefix:x, yn?prefix:y);
            if(c==0) c=Boolean.compare(xn,yn);
            if(c==0 && xn && yn) c=new java.math.BigInteger(x.substring(prefix.length())).compareTo(new java.math.BigInteger(y.substring(prefix.length())));
            if(c!=0) return c;
        }
        return Integer.compare(aa.length,bb.length);
    }
    static SheetDiff compare(CharacterSheet before, CharacterSheet after) {
        if(!before.ocid().equals(after.ocid())) throw new MapleException("SUBJECT_MISMATCH");
        if(!before.contractVersion().equals(after.contractVersion())) throw new MapleException("INCOMPATIBLE_CONTRACT");
        boolean sameRules=before.parserRulesVersion().equals(after.parserRulesVersion());
        var engine=new Engine(sameRules);
        List<Source> compared=new ArrayList<>(); List<Skipped> skipped=new ArrayList<>();
        if(!sameRules) engine.diagnostics.add("PARSER_VERSION_DIFFERENCE");
        Set<Source> sources=new HashSet<>(before.documents().keySet()); sources.addAll(after.documents().keySet());
        for(Source source: sources.stream().sorted(Comparator.comparing(Enum::name)).toList()) {
            var a=before.document(source);var b=after.document(source);
            if(a.status()!=Document.Status.FETCHED || b.status()!=Document.Status.FETCHED) {
                skipped.add(new Skipped(source,a.status()+"/"+b.status()));continue;
            }
            if(!a.structurallyValid() || !b.structurallyValid()) { skipped.add(new Skipped(source,"INVALID_DOCUMENT"));continue; }
            compared.add(source);engine.walk(source,a.raw(),b.raw(),"","");
        }
        engine.changes.sort(Comparator.comparing((Change c)->c.source().name())
            .thenComparing(Change::afterPointer,SheetDiff::pathOrder).thenComparing(Change::beforePointer,SheetDiff::unicode));
        return new SheetDiff(compared,skipped,engine.changes,engine.diagnostics.stream().sorted(SheetDiff::unicode).toList());
    }
    private record Indexed(int index, RawValue value) {}
    private static final class Engine {
        final boolean sameRules; final List<Change> changes=new ArrayList<>(); final Set<String> diagnostics=new HashSet<>();
        Engine(boolean sameRules) { this.sameRules=sameRules; }
        Map<String,Indexed> slots(RawValue.ArrayValue array,Source source,String path,String keyField) {
            Map<String,Indexed> result=new HashMap<>();
            for(int i=0;i<array.values().size();i++) {
                RawValue item=array.values().get(i);
                RawValue slot=item instanceof RawValue.ObjectValue o?o.fields().get(keyField):null;
                String key=slot instanceof RawValue.StringValue s?s.value():slot instanceof RawValue.NumberValue n?n.token():null;
                if(key==null||key.isEmpty())return null;
                if(result.containsKey(key)) { diagnostics.add("DUPLICATE_ENTITY_KEY:"+source+":"+path); return null; }
                result.put(key,new Indexed(i,item));
            }
            return result;
        }
        void walk(Source source,RawValue left,RawValue right,String bp,String ap) { walk(source,left,right,bp,ap,true); }
        void walk(Source source,RawValue left,RawValue right,String bp,String ap,boolean allowNumeric) {
            if(Objects.equals(left,right)) return;
            if(left instanceof RawValue.ObjectValue a && right instanceof RawValue.ObjectValue b) {
                if(source==Source.REPORTED_STATS && (a.fields().containsKey("stat_value")||b.fields().containsKey("stat_value"))) {
                    var name=a.fields().get("stat_name");
                    allowNumeric=allowNumeric && name instanceof RawValue.StringValue n && !n.value().isEmpty() && name.equals(b.fields().get("stat_name"));
                }
                for(String key: keys(a.fields().keySet(),b.fields().keySet()))
                    walk(source,a.fields().get(key),b.fields().get(key),bp+"/"+EntityView.escape(key),ap+"/"+EntityView.escape(key),allowNumeric);
                return;
            }
            if(left instanceof RawValue.ArrayValue a && right instanceof RawValue.ArrayValue b) {
                String keyField=source==Source.EQUIPMENT && Document.equipmentArray(ap.substring(1)) && ap.chars().filter(c->c=='/').count()==1?"item_equipment_slot":
                    source==Source.REPORTED_STATS && ap.equals("/final_stat")?"stat_name":
                    source==Source.UNION_RAIDER && ap.equals("/union_raider_preset")?"preset_no":null;
                if(keyField!=null) {
                    var am=slots(a,source,ap,keyField);var bm=slots(b,source,ap,keyField);
                    if(am!=null && bm!=null) {
                        for(String key:keys(am.keySet(),bm.keySet())) {
                            var av=am.get(key);var bv=bm.get(key);
                            walk(source,av==null?null:av.value(),bv==null?null:bv.value(),av==null?bp:bp+"/"+av.index(),bv==null?ap:ap+"/"+bv.index());
                        }
                        return;
                    }
                }
                for(int i=0;i<Math.max(a.values().size(),b.values().size());i++)
                    walk(source,i<a.values().size()?a.values().get(i):null,i<b.values().size()?b.values().get(i):null,bp+"/"+i,ap+"/"+i);
                return;
            }
            FieldValue<?> bf=FieldValue.of(left,bp), af=FieldValue.of(right,ap);
            BigDecimal delta=null;boolean semantic=true;
            if(allowNumeric && numeric(source,bp) && numeric(source,ap)) {
                var bn=FieldValue.number(left,bp);var an=FieldValue.number(right,ap);bf=bn;af=an;
                if(sameRules && bn.state()==FieldValue.State.VALUE && an.state()==FieldValue.State.VALUE) {
                    delta=an.value().subtract(bn.value());semantic=delta.signum()!=0;
                }
            }
            changes.add(new Change(source,bp,ap,left==null?"ADDED":right==null?"REMOVED":"CHANGED",bf,af,semantic,delta));
        }
    }
}
