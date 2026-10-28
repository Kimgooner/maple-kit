import org.whitedoggy.maplelibrary.*;
import java.util.Map;
import java.math.BigDecimal;

/** Standalone consumer of the built library, using synthetic documents only. */
public class Offline {
    public static void main(String[] args) {
        var before = new CharacterSheet("synthetic-example", Map.of(Source.SYMBOLS,
            Document.parse(Source.SYMBOLS, """
                {"symbol":[{"symbol_name":"합성 심볼","symbol_str":"10","symbol_dex":"20"}]}
                """)));
        var after = new CharacterSheet("synthetic-example", Map.of(Source.SYMBOLS,
            Document.parse(Source.SYMBOLS, """
                {"symbol":[{"symbol_name":"합성 심볼","symbol_str":"10","symbol_dex":"25"}]}
                """)));
        if (!before.document(Source.SYMBOLS).symbols().value().getFirst().number("symbol_str").value().equals(new BigDecimal("10")))
            throw new AssertionError("raw symbol stat changed");
        var changes = before.compareTo(after).changes();
        if (changes.size()!=1 || changes.getFirst().delta().compareTo(new BigDecimal("5"))!=0)
            throw new AssertionError("incorrect delta");
        System.out.println("symbol_dex: +5 (reported data; no stat aggregation)");
    }
}
