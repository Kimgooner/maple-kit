package org.whitedoggy.maplelibrary;

import java.util.*;
import java.util.regex.*;
import java.math.BigDecimal;

/** Conservative, provenance-preserving stat grammar. No effect application or aggregation. */
public final class OptionDecoder {
    private OptionDecoder() {}
    public record Option(String rawText,int start,int end,String status,List<String> targets,BigDecimal value,
                         String unit,List<String> conditions,Map<String,String> expression,String ruleId) {
        public Option { targets=List.copyOf(targets);conditions=List.copyOf(conditions);expression=Map.copyOf(expression); }
    }
    public record LocatedOption(String pointer,Option option) {}
    private static final Map<String,List<String>> ALIASES=aliases();
    private static Map<String,List<String>> aliases() {
        Map<String,List<String>> result=new HashMap<>();
        SourceRegistry.resource("option-aliases.json").fields().forEach((k,v)->result.put(k,((RawValue.ArrayValue)v).values().stream().map(x->((RawValue.StringValue)x).value()).toList()));return Map.copyOf(result);
    }
    private static final String LABEL=String.join("|",ALIASES.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder())).map(Pattern::quote).toList());
    private static final RawValue.ArrayValue RULES=(RawValue.ArrayValue)SourceRegistry.resourceValue("option-rules.json");
    private static final String NUMBER="[+-]?[0-9]+(?:\\.[0-9]+)?";
    private static final Pattern SIMPLE=Pattern.compile("(?<label>"+LABEL+")\\s*:?\\s*(?<value>"+NUMBER+")\\s*(?<unit>%|초|레벨)?\\s*(?<verb>증가|감소)?");
    private static final Pattern CHANCE=Pattern.compile("공격 시\\s*(?<chance>"+NUMBER+")%\\s*확률로\\s*(?<effect>.+)");
    private static final Pattern AP=Pattern.compile("AP를 직접 투자한 (STR|DEX|INT|LUK)의\\s*("+NUMBER+")% 만큼 (STR|DEX|INT|LUK) 증가");
    private static final Pattern PER_LEVEL=Pattern.compile("캐릭터 기준\\s*9레벨\\s*당\\s*(STR|DEX|INT|LUK)\\s*("+NUMBER+")\\s*(?:증가)?");
    private static Option option(String text,int start,int end,String status,List<String> targets,BigDecimal value,String unit,List<String> conditions,Map<String,String> expression,String rule) {
        return new Option(text.substring(start,end),text.codePointCount(0,start),text.codePointCount(0,end),status,targets,value,unit,conditions,expression,rule);
    }
    private static Option single(String text,int start,int end) {
        String body=text.substring(start,end);
        for(var rawRule:RULES.values()) {
            var rule=((RawValue.ObjectValue)rawRule).fields();var match=Pattern.compile(scalar(rule.get("pattern"))).matcher(body);
            if(match.matches()) {
                int group=Integer.parseInt(((RawValue.NumberValue)rule.get("effect_group")).token());String inner=match.group(group);
                if(!SIMPLE.matcher(inner).matches())return option(text,start,end,"UNKNOWN",List.of(),null,"UNKNOWN",List.of(),Map.of(),"unknown");
                var o=single(inner,0,inner.length());List<String> conditions=new ArrayList<>();
                ((RawValue.ArrayValue)rule.get("conditions")).values().forEach(v->conditions.add(scalar(v)));conditions.addAll(o.conditions());
                Map<String,String> expression=new HashMap<>(o.expression());
                if(rule.get("expression") instanceof RawValue.ObjectValue e)e.fields().forEach((k,v)->expression.put(k,scalar(v)));
                if(rule.get("duration_group") instanceof RawValue.NumberValue d) {
                    String duration=match.group(Integer.parseInt(d.token()));RawValue.decimal(duration);expression.put("duration_seconds",duration);
                }
                return option(text,start,end,o.status(),o.targets(),o.value(),o.unit(),conditions,expression,scalar(rule.get("id")));
            }
        }
        Matcher m=AP.matcher(body);
        if(m.matches())return option(text,start,end,"KNOWN",List.of(m.group(3)),null,"PERCENT",List.of(),Map.of("kind","PROPORTIONAL_TO_STAT","basis","INVESTED_AP."+m.group(1),"ratio",m.group(2)),"ap-ratio");
        m=PER_LEVEL.matcher(body);
        if(m.matches())return option(text,start,end,"KNOWN",List.of(m.group(1)),RawValue.decimal(m.group(2)),"FLAT",List.of(),Map.of("kind","PER_LEVEL","levels","9"),"per-level");
        m=CHANCE.matcher(body);String chance=null,effect=body;
        if(m.matches()) {
            chance=m.group("chance");BigDecimal c=RawValue.decimal(chance);
            if(c.signum()<0 || c.compareTo(BigDecimal.valueOf(100))>0)return option(text,start,end,"AMBIGUOUS",List.of(),null,"UNKNOWN",List.of(),Map.of(),"unknown");
            effect=m.group("effect");
        }
        m=SIMPLE.matcher(effect);
        if(!m.matches())return option(text,start,end,"UNKNOWN",List.of(),null,"UNKNOWN",List.of(),Map.of(),"unknown");
        List<String> targets=ALIASES.get(m.group("label"));String u=m.group("unit");
        String unit=u==null?"FLAT":switch(u){case "%"->"PERCENT";case "초"->"SECONDS";default->"LEVEL";};
        List<String> conditions=new ArrayList<>();if(targets.equals(List.of("BOSS_DAMAGE")))conditions.add("TARGET_BOSS");if(targets.equals(List.of("NORMAL_DAMAGE")))conditions.add("TARGET_NORMAL_MONSTER");
        if(chance!=null) { conditions.add("ON_ATTACK");conditions.add("CHANCE_PERCENT="+chance); }
        return option(text,start,end,"KNOWN",targets,RawValue.decimal(m.group("value")),unit,conditions,
            "감소".equals(m.group("verb"))?Map.of("direction","DECREASE"):Map.of(),"stat");
    }
    public static List<Option> decode(String text) {
        if(text==null)throw new MapleException("INVALID_REQUEST");
        List<Integer> bounds=new ArrayList<>();bounds.add(0);int segment=0,depth=0;
        Pattern next=Pattern.compile("\\s*(?:"+LABEL+")\\s*:?\\s*"+NUMBER);
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='[')depth++;else if(c==']')depth=Math.max(0,depth-1);
            if(depth==0 && (c=='\r'||c=='\n'||(c==',' && SIMPLE.matcher(text.substring(segment,i).strip()).matches() && next.matcher(text.substring(i+1)).lookingAt()))) { bounds.add(i);bounds.add(i+1);segment=i+1; }
        }
        bounds.add(text.length());List<Option> result=new ArrayList<>();
        for(int i=0;i<bounds.size();i+=2) {
            int start=bounds.get(i),end=bounds.get(i+1);
            while(start<end && Character.isWhitespace(text.charAt(start)))start++;
            while(end>start && Character.isWhitespace(text.charAt(end-1)))end--;
            if(start<end)try {result.add(single(text,start,end));}
            catch(MapleException e){result.add(option(text,start,end,"AMBIGUOUS",List.of(),null,"UNKNOWN",List.of(),Map.of(),"unknown"));}
        }
        return List.copyOf(result);
    }
    private static final Set<String> EFFECT_KEYS=Set.of("skill_effect","stat_increase","ability_value","title_description","soul_option","option_value","effect","stat","name");
    public static List<LocatedOption> documentOptions(Document document) {
        if(document.status()!=Document.Status.FETCHED)return List.of();
        List<LocatedOption> result=new ArrayList<>();walk(document.raw(),"","",result);return List.copyOf(result);
    }
    private static String scalar(RawValue v) {return v instanceof RawValue.StringValue s?s.value():v instanceof RawValue.NumberValue n?n.token():null;}
    private static void add(String text,String path,List<LocatedOption> result) {decode(text).forEach(o->result.add(new LocatedOption(path,o)));}
    private static void walk(RawValue raw,String path,String parent,List<LocatedOption> result) {
        if(raw instanceof RawValue.ObjectValue object) {
            var t=object.fields().get("option_type");var v=object.fields().get("option_value");
            boolean structured=t instanceof RawValue.StringValue && scalar(v)!=null;
            if(structured)for(var o:decode(scalar(t)+" "+scalar(v))) {
                String value=scalar(v);
                result.add(new LocatedOption(path+"/option_value",new Option(value,0,value.codePointCount(0,value.length()),o.status(),o.targets(),o.value(),o.unit(),o.conditions(),o.expression(),"structured:"+o.ruleId())));
            }
            object.fields().forEach((key,value)->{
                String p=path+"/"+EntityView.escape(key);
                boolean effect=EFFECT_KEYS.contains(key)||key.matches("(?:additional_)?potential_option_[1-3]|soul_potential_option_[1-3]");
                if(key.equals("name"))effect=path.contains("union_artifact_effect/");
                if(key.equals("option_value")&&structured)return;
                if(value instanceof RawValue.StringValue s && effect)add(s.value(),p,result);else walk(value,p,key,result);
            });
        } else if(raw instanceof RawValue.ArrayValue a)for(int i=0;i<a.values().size();i++) {
            RawValue value=a.values().get(i);String p=path+"/"+i;
            if(value instanceof RawValue.StringValue s && Set.of("union_raider_stat","union_state_stat","union_occupied_stat").contains(parent))add(s.value(),p,result);else walk(value,p,parent,result);
        }
    }
}
