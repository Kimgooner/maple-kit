import org.whitedoggy.maplelibrary.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Opt-in check: only a key-file path is supplied, never the key on the command line. */
public class LiveSmoke {
    public static void main(String[] args)throws Exception {
        String key=Files.readAllLines(Path.of(args[0])).stream().map(String::trim).map(s->s.startsWith("export ")?s.substring(7):s)
            .filter(s->s.startsWith("MAPLE_OPEN_API_KEY=")).map(s->s.substring(s.indexOf('=')+1).trim().replaceAll("^[\"']|[\"']$","")).findFirst().orElseThrow();
        try(var client=new MapleClient(key)) {
            var sheet=client.getCharacter(CharacterRequest.nickname(args[1])).join();
            for(var s:sheet.documents().keySet().stream().sorted().toList()) {
                var d=sheet.document(s);
                System.out.println(s+" status="+d.status()+" attempts="+d.attempts()+" schemaDiagnostics="+d.diagnostics().size());
            }
            System.out.println("quality="+sheet.quality());
            var historic=client.getCharacterSelected(CharacterRequest.ocid(sheet.ocid()).on(LocalDate.parse("2026-03-18")),Set.of(Source.RING_EXCHANGE)).join();
            System.out.println("historical-ring="+historic.document(Source.RING_EXCHANGE).status());
        } catch(java.util.concurrent.CompletionException e) {
            if(e.getCause() instanceof MapleException m)throw new IllegalStateException(m.code());
            throw new IllegalStateException("LIVE_CHECK_FAILED");
        }
    }
}
