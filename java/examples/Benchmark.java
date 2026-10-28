import org.whitedoggy.maplelibrary.*;
import java.util.*;
/** Synthetic baseline only; heap occupancy is not peak allocation. */
public class Benchmark {
    public static void main(String[] args) {
        var rows=new ArrayList<String>();
        for(int i=0;i<1000;i++)rows.add("{\"item_equipment_slot\":\""+i+"\",\"item_base_option\":{\"str\":\"12345\"},\"potential_option_1\":\"STR : +12%\"}");
        String payload="{\"item_equipment\":["+String.join(",",rows)+"]}";
        long start=System.nanoTime();
        for(int i=0;i<10;i++) {
            var d=Document.parse(Source.EQUIPMENT,payload);var options=d.decodedOptions();
            var s=new CharacterSheet("synthetic",Map.of(Source.EQUIPMENT,d));
            if(options.size()!=1000||!s.compareTo(s).changes().isEmpty())throw new AssertionError();
        }
        System.out.printf(Locale.ROOT,"{\"iterations\":10,\"entities_per_iteration\":1000,\"seconds\":%.4f,\"heap_used_bytes\":%d,\"note\":\"synthetic cold parse+options+self-diff; heap occupancy not peak; no SLA\"}%n",(System.nanoTime()-start)/1e9,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
    }
}
