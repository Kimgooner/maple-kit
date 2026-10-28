import java.util.Set;
import org.whitedoggy.maplelibrary.MapleCharacter;
import org.whitedoggy.maplelibrary.MapleClient;
import org.whitedoggy.maplelibrary.Source;

public class Quickstart {
    public static void printSummary(MapleCharacter character) {
        for (var item : character.equipment().items()) {
            System.out.println(item.slot() + " " + item.name());
        }
        for (var symbol : character.symbols().items()) {
            System.out.println(symbol.name() + " " + symbol.level());
        }
    }

    public static void main(String[] args) {
        try (var maple = new MapleClient(System.getenv("MAPLE_OPEN_API_KEY"))) {
            var character = maple.character(
                System.getenv("MAPLE_CHARACTER_NAME"),
                Set.of(Source.EQUIPMENT, Source.SYMBOLS, Source.UNION)
            ).join();
            printSummary(character);
            System.out.println(character.union().basic().level());
            // Return this Map from a web handler, or use toJson() for a JSON string.
            var view = character.toView();
            System.out.println(view.get("equipment"));
        }
    }
}
