import java.util.Set;
import org.whitedoggy.maplelibrary.CharacterRequest;
import org.whitedoggy.maplelibrary.CharacterSheet;
import org.whitedoggy.maplelibrary.Document;
import org.whitedoggy.maplelibrary.FieldValue;
import org.whitedoggy.maplelibrary.MapleClient;
import org.whitedoggy.maplelibrary.Source;

public class Quickstart {
    public static void printSummary(CharacterSheet sheet) {
        var equipment = sheet.document(Source.EQUIPMENT);
        if (equipment.status() == Document.Status.FETCHED) {
            var items = equipment.equipment();
            if (items.state() == FieldValue.State.VALUE) {
                for (var item : items.value()) {
                    var slot = item.text("item_equipment_slot");
                    var name = item.text("item_name");
                    if (slot.state() == FieldValue.State.VALUE && name.state() == FieldValue.State.VALUE) {
                        System.out.println(slot.value() + " " + name.value());
                    }
                }
            }
        }
        var symbols = sheet.document(Source.SYMBOLS);
        if (symbols.status() == Document.Status.FETCHED) {
            var entries = symbols.symbols();
            if (entries.state() == FieldValue.State.VALUE) {
                for (var symbol : entries.value()) {
                    var name = symbol.text("symbol_name");
                    var level = symbol.number("symbol_level");
                    if (name.state() == FieldValue.State.VALUE && level.state() == FieldValue.State.VALUE) {
                        System.out.println(name.value() + " " + level.value());
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        try (var client = new MapleClient(System.getenv("MAPLE_OPEN_API_KEY"))) {
            var sheet = client.getCharacterSelected(
                CharacterRequest.nickname(System.getenv("MAPLE_CHARACTER_NAME")),
                Set.of(Source.EQUIPMENT, Source.SYMBOLS)
            ).join();
            printSummary(sheet);
        }
    }
}
