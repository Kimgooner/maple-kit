package org.whitedoggy.maplelibrary;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Source selection and packaged schema metadata. Groups never trigger duplicate calls. */
public final class SourceRegistry {
    private SourceRegistry() {}
    static RawValue.ObjectValue resource(String name) {return (RawValue.ObjectValue)resourceValue(name);}
    static RawValue resourceValue(String name) {
        try(var stream=SourceRegistry.class.getResourceAsStream(name)) {
            if(stream==null) throw new IllegalStateException("Missing library resource: "+name);
            return RawValue.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot load library resource"); }
    }
    private static final RawValue.ObjectValue DATA=resource("registry.json");
    private static final RawValue.ObjectValue OVERRIDES=resource("schema-overrides.json");
    public static boolean supportsDate(Source source,java.time.LocalDate date) {
        var fields=spec(source).fields();
        var from=fields.get("valid_from");var until=fields.get("valid_until");
        return !(from instanceof RawValue.StringValue f && date.isBefore(java.time.LocalDate.parse(f.value())))
            && !(until instanceof RawValue.StringValue u && date.isAfter(java.time.LocalDate.parse(u.value())));
    }
    public static Set<Source> allSources() {
        var sources=(RawValue.ObjectValue)DATA.fields().get("sources");
        Set<Source> result=EnumSet.noneOf(Source.class); sources.fields().keySet().forEach(s->result.add(Source.valueOf(s)));return Set.copyOf(result);
    }
    public static Set<Source> expand(Set<Source> sources) {
        if(sources==null || sources.isEmpty()) throw new MapleException("INVALID_REQUEST");
        Set<Source> result=EnumSet.noneOf(Source.class);
        for(Source s:sources) {
            if(s==null) throw new MapleException("INVALID_REQUEST");
            if(s==Source.SKILLS || s==Source.UNION) {
                String prefix=s==Source.SKILLS?"SKILL_":"UNION_";
                allSources().stream().filter(v->v.name().startsWith(prefix)).forEach(result::add);
            } else result.add(s);
        }
        return Set.copyOf(result);
    }
    private static RawValue.ObjectValue spec(Source s) {
        var raw=((RawValue.ObjectValue)DATA.fields().get("sources")).fields().get(s.name());
        if(raw==null) throw new MapleException("INVALID_REQUEST");return (RawValue.ObjectValue)raw;
    }
    public static String path(Source s) { return ((RawValue.StringValue)spec(s).fields().get("path")).value(); }
    public static String grade(Source s) { var v=spec(s).fields().get("grade");return v instanceof RawValue.StringValue t?t.value():null; }
    public static String primary(Source s) { return ((RawValue.StringValue)spec(s).fields().get("primary")).value(); }
    public static Map<String,String> fields(Source s) {
        String schema=((RawValue.StringValue)spec(s).fields().get("schema")).value();
        var raw=(RawValue.ObjectValue)((RawValue.ObjectValue)DATA.fields().get("fields")).fields().get(schema);
        Map<String,String> result=new HashMap<>();raw.fields().forEach((k,v)->{if(v instanceof RawValue.StringValue t)result.put(k,t.value());});if(OVERRIDES.fields().get(s.name()) instanceof RawValue.ObjectValue extra)extra.fields().forEach((k,v)->result.put(k,((RawValue.StringValue)v).value()));
        return Map.copyOf(result);
    }
    public static Map<String,FieldValue<RawValue>> presets(Document document) {
        var view=document.view();Map<String,FieldValue<RawValue>> result=new TreeMap<>();
        ((RawValue.ObjectValue)document.raw()).fields().keySet().stream().filter(k->k.contains("preset")).forEach(k->result.put(k,view.field(k)));
        return Collections.unmodifiableMap(result);
    }
    public static List<String> diagnostics(Document document) {
        if(document.status()!=Document.Status.FETCHED)return List.of();
        List<String> result=new ArrayList<>();var extensions=OVERRIDES.fields().get(document.source().name()) instanceof RawValue.ObjectValue e?e.fields().keySet():Set.<String>of();walk(document.raw(),"","",fields(document.source()),extensions,result);return List.copyOf(result);
    }
    private static void walk(RawValue value,String path,String pattern,Map<String,String> fields,Set<String> extensions,List<String> result) {
        String expected=fields.get(pattern);
        if(extensions.contains(pattern))result.add("OBSERVED_SCHEMA_EXTENSION:"+path);
        if(!pattern.isEmpty() && expected==null)result.add("UNKNOWN_FIELD:"+path);
        else if(expected!=null && value!=RawValue.NullValue.INSTANCE) {
            boolean valid=switch(expected) {
                case "object" -> value instanceof RawValue.ObjectValue;
                case "array" -> value instanceof RawValue.ArrayValue;
                case "string" -> value instanceof RawValue.StringValue;
                case "number","integer" -> value instanceof RawValue.NumberValue;
                case "boolean" -> value instanceof RawValue.BooleanValue;
                default -> true;
            };
            if(!valid)result.add("SCHEMA_TYPE_DIFFERENCE:"+path);
        }
        if(value instanceof RawValue.ObjectValue o)o.fields().forEach((k,v)->walk(v,path+"/"+EntityView.escape(k),pattern+"/"+EntityView.escape(k),fields,extensions,result));
        if(value instanceof RawValue.ArrayValue a)for(int i=0;i<a.values().size();i++)walk(a.values().get(i),path+"/"+i,pattern+"/*",fields,extensions,result);
    }
}
