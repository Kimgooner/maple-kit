package org.whitedoggy.maplelibrary;

import java.util.*;

/** Immutable friendly facade; detail() retains missing/null/invalid states and original fields. */
public abstract class FriendlyNode {
    private final String type;
    private final EntityView detail;
    private final String prefix;
    protected FriendlyNode(String type, EntityView detail) {
        this(type,detail,"");
    }
    protected FriendlyNode(String type, EntityView detail, String prefix) {
        this.type=type; this.detail=Objects.requireNonNull(detail);this.prefix=prefix;
        if(!(detail.raw() instanceof RawValue.ObjectValue))throw new MapleException("INVALID_FIELD");
    }
    public final EntityView detail() { return detail; }
    protected final String prefix() { return prefix; }
    final FieldValue<RawValue> field(String path) { return Presentation.fieldAt(detail,prefix.isEmpty()?path:String.join("|",Arrays.stream(path.split("\\|",-1)).map(p->prefix+p).toList())); }
    final String modelType() { return type; }
    protected final Object readProperty(String name) {
        var spec=Presentation.MODELS.get(type).get(name);
        return Presentation.convert(field(spec.path()),spec.type());
    }
    public Map<String,Object> toView() {
        Map<String,Object> result=new LinkedHashMap<>(); List<String> diagnostics=new ArrayList<>();
        Presentation.MODELS.get(type).forEach((name,spec)-> {
            try {result.put(name,Presentation.jsonValue(readProperty(name)));}
            catch(MapleException error) {result.put(name,null); diagnostics.add("INVALID_FIELD:"+field(spec.path()).pointer());}
        });
        result.put("diagnostics",diagnostics);
        return Presentation.decorate(this,result);
    }
    public final String toJson() { return Presentation.toJson(toView()); }
}
