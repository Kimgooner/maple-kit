package org.whitedoggy.maplelibrary;

import java.util.*;
import java.math.BigDecimal;
import java.util.function.Supplier;

/** JSON-compatible display contract shared with Python. No aggregation or effect application. */
public final class Presentation {
    private Presentation() {}
    record Spec(String path,String type) {}
    private static final RawValue.ObjectValue CONTRACT=SourceRegistry.resource("presentation.json");
    static final Map<String,Map<String,Spec>> MODELS=models();
    static final Map<String,List<String>> SOURCES=stringLists("sources");
    static final Map<String,List<String>> PRESETS=stringLists("presets");
    private static final List<Map<String,String>> STATS=stats();
    private static Map<String,Map<String,Spec>> models() {
        Map<String,Map<String,Spec>> result=new LinkedHashMap<>();
        ((RawValue.ObjectValue)CONTRACT.fields().get("models")).fields().forEach((name,value)-> {
            Map<String,Spec> fields=new LinkedHashMap<>();
            ((RawValue.ObjectValue)value).fields().forEach((k,v)-> {
                var obj=((RawValue.ObjectValue)v).fields();fields.put(k,new Spec(text(obj.get("path")),text(obj.get("type"))));
            });result.put(name,Collections.unmodifiableMap(fields));
        });return Collections.unmodifiableMap(result);
    }
    private static Map<String,List<String>> stringLists(String key) {
        Map<String,List<String>> result=new LinkedHashMap<>();
        ((RawValue.ObjectValue)CONTRACT.fields().get(key)).fields().forEach((k,v)->result.put(k,((RawValue.ArrayValue)v).values().stream().map(Presentation::text).toList()));
        return Collections.unmodifiableMap(result);
    }
    private static List<Map<String,String>> stats() {
        return ((RawValue.ArrayValue)CONTRACT.fields().get("stats")).values().stream().map(v->{
            Map<String,String> map=new LinkedHashMap<>();((RawValue.ObjectValue)v).fields().forEach((k,x)->map.put(k,text(x)));return Map.copyOf(map);
        }).toList();
    }
    private static String text(RawValue value) { return ((RawValue.StringValue)value).value(); }
    static FieldValue<RawValue> fieldAt(EntityView entity,String path) {
        if(path.isEmpty())return FieldValue.of(entity.raw(),entity.pointer());
        FieldValue<RawValue> value=null;
        for(String alternative:path.split("\\|")) {
            String[] parts=alternative.split("/"); EntityView current=entity;
            for(int i=0;i<parts.length;i++) {
                value=current.field(parts[i]);
                if(i==parts.length-1 || value.state()!=FieldValue.State.VALUE)break;
                if(!(value.raw() instanceof RawValue.ObjectValue))return new FieldValue<>(FieldValue.State.INVALID,null,value.raw(),value.pointer());
                current=new EntityView(value.raw(),value.pointer());
            }
            if(value.state()!=FieldValue.State.MISSING)return value;
        }
        return value;
    }
    static Object convert(FieldValue<RawValue> field,String type) {
        if(field.state()==FieldValue.State.MISSING || field.state()==FieldValue.State.NULL)return null;
        if(field.state()==FieldValue.State.INVALID)throw new MapleException("INVALID_FIELD");
        RawValue raw=field.raw();
        if(type.equals("number")||type.equals("integer")) {
            var number=FieldValue.number(raw,field.pointer());
            if(number.state()!=FieldValue.State.VALUE)throw new MapleException("INVALID_FIELD");
            if(type.equals("number"))return number.value();
            try {return number.value().intValueExact();}catch(ArithmeticException e){throw new MapleException("INVALID_FIELD");}
        }
        if(type.equals("option"))return new OptionLine((String)convert(field,"text"),null);
        if(type.equals("text")) {
            if(!(raw instanceof RawValue.StringValue value))throw new MapleException("INVALID_FIELD");
            return value.value();
        }
        if(type.startsWith("list:")) {
            if(!(raw instanceof RawValue.ArrayValue array))throw new MapleException("INVALID_FIELD");
            List<Object> result=new ArrayList<>();
            for(int i=0;i<array.values().size();i++) {
                var value=array.values().get(i);
                if(value==RawValue.NullValue.INSTANCE)throw new MapleException("INVALID_FIELD");
                result.add(convert(FieldValue.of(value,field.pointer()+"/"+i),type.substring(5)));
            }return List.copyOf(result);
        }
        if(!(raw instanceof RawValue.ObjectValue))throw new MapleException("INVALID_FIELD");
        return Models.create(type,new EntityView(raw,field.pointer()));
    }
    static String decimalText(BigDecimal value) { return value.signum()==0?"0":value.stripTrailingZeros().toPlainString(); }
    private static String signed(BigDecimal value,String unit,boolean base) {
        return (base||value.signum()<0?"":"+")+decimalText(value)+(unit.equals("PERCENT")?"%":"");
    }
    static Object jsonValue(Object value) {
        if(value instanceof Preset<?> preset)return preset.toView();
        if(value instanceof OptionLine line)return line.toView();
        if(value instanceof FriendlyNode n)return n.toView();
        if(value instanceof BigDecimal n)return decimalText(n);
        if(value instanceof List<?> list)return list.stream().map(Presentation::jsonValue).toList();
        return value;
    }
    static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;
    }
    public record OptionLine(String text,Integer slot) {
        public OptionLine { Objects.requireNonNull(text); }
        public List<OptionDecoder.Option> effects() { return OptionDecoder.decode(text); }
        public String parseStatus() { var e=effects();return !e.isEmpty() && e.stream().allMatch(x->x.status().equals("KNOWN"))?"KNOWN":"UNKNOWN"; }
        public Map<String,Object> toView() { return map("slot",slot,"text",text,"parse_status",parseStatus()); }
    }
    public record Potential(String grade,List<OptionLine> lines,String flag,Integer amplificationLevel,String title) {
        public Potential { lines=List.copyOf(lines); }
        public Map<String,Object> toView() {
            String normalized=grade==null?null:switch(grade){case "레어"->"RARE";case "에픽"->"EPIC";case "유니크"->"UNIQUE";case "레전드리"->"LEGENDARY";default->grade;};
            return map("title",title,"grade",normalized,"grade_label",grade,"grade_short_label","레전드리".equals(grade)?"레전":grade,
                "flag",flag,"amplification_level",amplificationLevel,"amplification_text",amplificationLevel==null?null:"증폭 "+amplificationLevel+"단계",
                "summary_text",String.join(", ",lines.stream().map(OptionLine::text).toList()),"lines",lines.stream().map(OptionLine::toView).toList());
        }
    }
    private static String textField(FriendlyNode node,String path) { return (String)convert(fieldAt(node.detail(),path),"text"); }
    public static Potential potential(FriendlyNode node,String prefix) {
        String grade=textField(node,prefix.equals("soul_potential_option")?"soul_potential_grade":prefix+"_grade");
        List<OptionLine> lines=new ArrayList<>();
        for(int i=1;i<=3;i++){String text=textField(node,prefix+"_"+i);if(text!=null&&!text.isEmpty())lines.add(new OptionLine(text,i));}
        String flag=prefix.equals("soul_potential_option")?null:textField(node,prefix+"_flag");
        Integer amplification=prefix.equals("soul_potential_option")?(Integer)convert(fieldAt(node.detail(),"soul_potential_amplified_grade"),"integer"):null;
        if(grade==null&&lines.isEmpty()&&flag==null&&amplification==null)return null;
        return new Potential(grade,lines,flag,amplification,switch(prefix){case "potential_option"->"잠재능력";case "additional_potential_option"->"에디셔널 잠재능력";default->"소울 잠재능력";});
    }
    public record Soul(String name,String optionText,BigDecimal attackPower,BigDecimal magicPower,String activeText,Potential potential) {
        public Map<String,Object> toView() {
            List<String> summary=new ArrayList<>();if(name!=null&&!name.isEmpty())summary.add(name);if(optionText!=null&&!optionText.isEmpty())summary.add(optionText);
            return map("name",name,"summary_text",String.join(", ",summary),"option_lines",optionText==null?List.of():List.of(new OptionLine(optionText,null).toView()),
                "attack_power",jsonValue(attackPower),"magic_power",jsonValue(magicPower),"active_text",activeText,"potential",potential==null?null:potential.toView());
        }
    }
    public static Soul soul(FriendlyNode node) {
        String name=textField(node,"soul_name"),text=textField(node,"soul_option"),active=textField(node,"soul_active");
        BigDecimal attack=(BigDecimal)convert(fieldAt(node.detail(),"soul_pad"),"number"),magic=(BigDecimal)convert(fieldAt(node.detail(),"soul_mad"),"number");
        Potential pot=potential(node,"soul_potential_option");
        return name==null&&text==null&&active==null&&attack==null&&magic==null&&pot==null?null:new Soul(name,text,attack,magic,active,pot);
    }
    static List<Map<String,Object>> statRows(FriendlyNode node,boolean includeZero) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(var spec:STATS) {
            if(!MODELS.get(node.modelType()).containsKey(spec.get("name")))continue;
            BigDecimal value=(BigDecimal)node.readProperty(spec.get("name"));
            if(value==null || value.signum()==0&&!includeZero)continue;
            String formatted=signed(value,spec.get("unit"),false);
            result.add(map("key",spec.get("key"),"label",spec.get("label"),"value",decimalText(value),"unit",spec.get("unit"),"value_text",formatted,"text",spec.get("label")+" "+formatted));
        }return result;
    }
    private static List<Map<String,Object>> itemRows(FriendlyNode node) {
        FriendlyNode stats=(FriendlyNode)node.readProperty("stats");if(stats==null)return null;
        var rows=statRows(stats,false);FriendlyNode options=(FriendlyNode)node.readProperty("options");
        for(var row:rows) {
            String name=STATS.stream().filter(s->s.get("key").equals(row.get("key"))).findFirst().orElseThrow().get("name");
            List<Map<String,Object>> parts=new ArrayList<>();
            String[][] sources={{"base","기본"},{"starforce","스타포스"},{"scroll","주문서 등 기타"},{"additional","추가옵션"},{"exceptional","익셉셔널"}};
            for(var source:sources){
                FriendlyNode block=(FriendlyNode)options.readProperty(source[0]);BigDecimal value=block==null?null:(BigDecimal)block.readProperty(name);
                if(value!=null&&(value.signum()!=0||source[0].equals("base")))parts.add(map("source",source[0].toUpperCase(Locale.ROOT),"label",source[1],"value",decimalText(value),"text",signed(value,(String)row.get("unit"),source[0].equals("base"))));
            }
            row.put("components",parts);row.put("breakdown_text",String.join(" ",parts.stream().map(p->(String)p.get("text")).toList()));
        }return rows;
    }
    @SuppressWarnings("unchecked")
    private static void safely(FriendlyNode node,Map<String,Object> view,String key,Supplier<Object> fn) {
        try {view.put(key,fn.get());}catch(MapleException error){view.put(key,null);((List<String>)view.get("diagnostics")).add("INVALID_FIELD:"+node.detail().pointer()+"/"+key);}
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> decorate(FriendlyNode node,Map<String,Object> view) {
        String type=node.modelType();
        if(view.containsKey("icon"))view.put("icon_url",view.remove("icon"));
        if(type.equals("StatBlock")||type.equals("SymbolStats"))safely(node,view,"rows",()->statRows(node,false));
        if(type.equals("Item")) {
            safely(node,view,"stat_rows",()->itemRows(node));
            safely(node,view,"potential",()->{var p=potential(node,"potential_option");return p==null?null:p.toView();});
            safely(node,view,"additional_potential",()->{var p=potential(node,"additional_potential_option");return p==null?null:p.toView();});
            safely(node,view,"soul",()->{var s=soul(node);return s==null?null:s.toView();});
            view.remove("stats");view.remove("options");
            view.put("category",view.get("part"));Map<String,Object> scrolls=(Map<String,Object>)view.get("scrolls");Integer upgrade=(Integer)scrolls.get("upgrade_count");
            view.put("display_name",view.get("name")==null?null:view.get("name")+(upgrade!=null&&upgrade>0?" (+"+upgrade+")":""));
            List<Map<String,Object>> badges=new ArrayList<>();
            if(view.get("starforce")!=null)badges.add(map("key","STARFORCE","label","스타포스","value",view.get("starforce"),"text",view.get("starforce")+"성"));
            if(upgrade!=null)badges.add(map("key","SCROLL_UPGRADE","label","주문서 강화","value",upgrade,"text",(upgrade>=0?"+":"")+upgrade));
            view.put("badges",badges);view.put("enhancement",view.remove("scrolls"));
        }
        if(type.equals("Enhancement")) {
            Object u=view.get("upgrade_count"),r=view.get("remaining_count"),c=view.get("resilience_count");
            view.put("text",u==null||r==null||c==null?null:"주문서 강화 "+u+"회 (잔여 "+r+"회, 복구 가능 "+c+"회)");
        }
        if(type.equals("Symbol")) {
            safely(node,view,"stat_rows",()->statRows((FriendlyNode)node.readProperty("stats"),false));
            view.remove("stats");
            view.put("force_text",view.get("force")==null?null:"포스 "+view.get("force"));
        }
        if(view.containsKey("level"))view.put("level_text",view.get("level")==null?null:"Lv. "+view.get("level"));
        if(node instanceof Models.Pet pet)view.put("slot",pet.slot());
        if(MODELS.get(type).containsKey("text"))safely(node,view,"parse_status",()->{String t=(String)node.readProperty("text");return t==null?null:new OptionLine(t,null).parseStatus();});
        if(type.equals("NamedOption"))view.put("text",view.get("name")==null||view.get("value")==null?null:view.get("name")+" "+view.get("value"));
        if(type.equals("Pets")) {
            safely(node,view,"items",()->jsonValue(pets(node,false)));
            safely(node,view,"shared",()->jsonValue(pets(node,true)));
        }
        Map<String,List<String>> groups=new LinkedHashMap<>();
        var extras=(RawValue.ObjectValue)CONTRACT.fields().get("extra_presets");
        if(extras.fields().get(type) instanceof RawValue.ObjectValue extra)extra.fields().forEach((k,v)->groups.put(k,((RawValue.ArrayValue)v).values().stream().map(Presentation::text).toList()));
        if(PRESETS.containsKey(type))groups.put("presets",PRESETS.get(type));
        if(node instanceof Models.HyperStats hyper)safely(node,view,"items",()->jsonValue(hyper.items()));
        for(var group:groups.entrySet()) {
            String prefix=group.getValue().get(0);Map<String,Object> presets=new LinkedHashMap<>();view.put(group.getKey(),presets);
            for(String key:((RawValue.ObjectValue)node.detail().raw()).fields().keySet()) {
                if(!key.startsWith(prefix))continue;String suffix=key.substring(prefix.length());if(!suffix.matches("[0-9]+"))continue;
                int number;try{number=Integer.parseInt(suffix);}catch(NumberFormatException e){continue;}if(number<1)continue;
                try{presets.put(suffix,jsonValue(preset(node,number,group.getKey())));}
                catch(MapleException error){presets.put(suffix,null);((List<String>)view.get("diagnostics")).add("INVALID_FIELD:"+node.detail().pointer()+"/"+key);}
            }
        }return view;
    }
    public record Preset<T extends FriendlyNode>(int number,List<T> items,BigDecimal remainingPoints) {
        public Preset { if(items!=null)items=List.copyOf(items); }
        public Map<String,Object> toView() { return map("number",number,"items",jsonValue(items),"remaining_points",jsonValue(remainingPoints)); }
    }
    @SuppressWarnings("unchecked")
    static Object preset(FriendlyNode node,int number) { return preset(node,number,"presets"); }
    @SuppressWarnings("unchecked")
    static Object preset(FriendlyNode node,int number,String group) {
        if(number<1)throw new MapleException("INVALID_REQUEST");
        List<String> spec;
        if(group.equals("presets"))spec=PRESETS.get(node.modelType());
        else {
            var extras=(RawValue.ObjectValue)((RawValue.ObjectValue)CONTRACT.fields().get("extra_presets")).fields().get(node.modelType());
            spec=((RawValue.ArrayValue)extras.fields().get(group)).values().stream().map(Presentation::text).toList();
        }
        var field=fieldAt(node.detail(),spec.get(0)+number);
        if(field.state()==FieldValue.State.MISSING)throw new MapleException("FIELD_UNAVAILABLE");
        boolean list=spec.get(2).equals("list");Object value=convert(field,(list?"list:":"")+spec.get(1));
        BigDecimal remaining=node.modelType().equals("HyperStats")?(BigDecimal)convert(fieldAt(node.detail(),spec.get(0)+number+"_remain_point"),"number"):null;
        if(list)return new Preset<>(number,(List<FriendlyNode>)value,remaining);
        return value;
    }
    static List<Models.Pet> pets(FriendlyNode node,boolean shared) {
        String namePrefix=shared?"world_share_pet_":"pet_";
        var rawFields=((RawValue.ObjectValue)node.detail().raw()).fields();
        if(java.util.stream.IntStream.rangeClosed(1,3).noneMatch(slot->rawFields.containsKey(namePrefix+slot+"_name")))return null;
        List<Models.Pet> result=new ArrayList<>();
        for(int slot=1;slot<=3;slot++) {
            String prefix=(shared?"world_share_pet_":"pet_")+slot+"_";Map<String,RawValue> fields=new LinkedHashMap<>();
            ((RawValue.ObjectValue)node.detail().raw()).fields().forEach((k,v)->{if(k.startsWith(prefix))fields.put(k.substring(prefix.length()),v);});
            if(fields.isEmpty())continue;
            Models.Pet pet=new Models.Pet(node.detail(),prefix);
            if(pet.name()!=null)result.add(pet);
        }return List.copyOf(result);
    }
    static Object sourceView(MapleCharacter character,Source source) {
        return character.sheet().document(source).status()==Document.Status.FETCHED?character.source(source).toView():null;
    }
    static Map<String,Object> characterView(MapleCharacter character) {
        Map<String,Object> result=map("view_version","1"),statuses=new LinkedHashMap<>();result.put("sources",statuses);
        SOURCES.keySet().stream().sorted().forEach(s->{var d=character.sheet().document(Source.valueOf(s));statuses.put(s,map("status",d.status().name(),"error_code",d.error()==null?null:d.error().code(),"diagnostics",d.diagnostics()));});
        SOURCES.forEach((s,spec)->{if(!s.matches("(?:UNION|SKILL|HEXA|RING)_.*"))result.put(spec.get(0),sourceView(character,Source.valueOf(s)));});
        result.put("union",map("basic",sourceView(character,Source.UNION_BASIC),"raider",sourceView(character,Source.UNION_RAIDER),"artifact",sourceView(character,Source.UNION_ARTIFACT),"champion",sourceView(character,Source.UNION_CHAMPION)));
        result.put("skills",map("zero",sourceView(character,Source.SKILL_0),"hyper_passive",sourceView(character,Source.SKILL_HYPER_PASSIVE),"hyper_active",sourceView(character,Source.SKILL_HYPER_ACTIVE),"fifth",sourceView(character,Source.SKILL_5),"sixth",sourceView(character,Source.SKILL_6)));
        result.put("hexa",map("cores",sourceView(character,Source.HEXA_CORES),"stats",sourceView(character,Source.HEXA_STATS)));
        result.put("special_rings",map("exchange",sourceView(character,Source.RING_EXCHANGE),"reserve",sourceView(character,Source.RING_RESERVE)));
        return result;
    }
    /** Serialize view maps without requiring a web framework or Jackson databind. */
    public static String toJson(Object value) {
        StringBuilder out=new StringBuilder();writeJson(out,value);return out.toString();
    }
    private static void writeJson(StringBuilder out,Object value) {
        if(value==null){out.append("null");return;}
        if(value instanceof String text){
            out.append('"');for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<32||java.lang.Character.isSurrogate(c))out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}}
            }out.append('"');return;
        }
        if(value instanceof Integer || value instanceof Boolean){out.append(value);return;}
        if(value instanceof Map<?,?> map){out.append('{');boolean first=true;for(var e:map.entrySet()){if(!first)out.append(',');first=false;writeJson(out,e.getKey());out.append(':');writeJson(out,e.getValue());}out.append('}');return;}
        if(value instanceof List<?> list){out.append('[');boolean first=true;for(var entry:list){if(!first)out.append(',');first=false;writeJson(out,entry);}out.append(']');return;}
        throw new MapleException("INVALID_VIEW");
    }
}
