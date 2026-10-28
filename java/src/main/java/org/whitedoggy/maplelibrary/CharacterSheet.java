package org.whitedoggy.maplelibrary;

import java.util.*;

/** Offline sheet of supplied documents. Query/fetch metadata will be added with collection. */
public final class CharacterSheet {
    private final String ocid;
    private final Map<Source, Document> documents;
    private final String contractVersion, parserRulesVersion;
    private final Set<Source> requestedSources; private final java.time.LocalDate queryTime;private final List<String> fetchDiagnostics;
    public CharacterSheet(String ocid, Map<Source, Document> documents) { this(ocid, documents, "0.1", "0.2"); }
    public CharacterSheet(String ocid, Map<Source, Document> documents, String contractVersion, String parserRulesVersion) { this(ocid,documents,contractVersion,parserRulesVersion,documents.keySet(),null,List.of()); }
    public CharacterSheet(String ocid, Map<Source, Document> documents, String contractVersion, String parserRulesVersion,Set<Source> requestedSources,java.time.LocalDate queryTime,List<String> fetchDiagnostics) {
        if(ocid == null || ocid.isBlank() || contractVersion == null || contractVersion.isBlank() || parserRulesVersion == null || parserRulesVersion.isBlank())
            throw new MapleException("INVALID_REQUEST");
        for(var entry: documents.entrySet()) if(entry.getValue() == null || entry.getKey() != entry.getValue().source()) throw new MapleException("INVALID_REQUEST");
        this.requestedSources=Set.copyOf(requestedSources);this.queryTime=queryTime;this.fetchDiagnostics=List.copyOf(fetchDiagnostics);
        this.ocid=ocid; this.documents=Map.copyOf(documents); this.contractVersion=contractVersion; this.parserRulesVersion=parserRulesVersion;
    }
    public String ocid() { return ocid; }
    public String contractVersion() { return contractVersion; }
    public String parserRulesVersion() { return parserRulesVersion; }
    public Map<Source, Document> documents() { return documents; }
    public Document document(Source source) { return documents.getOrDefault(source, new Document(source, Document.Status.NOT_REQUESTED, null)); }
    public MapleCharacter asCharacter() { return new MapleCharacter(this); }
    public SheetDiff compareTo(CharacterSheet after) { return SheetDiff.compare(this, after); }
    @Override public String toString() { return "CharacterSheet[ocid=<redacted>, sources="+documents.keySet()+"]"; }
    public Set<Source> requestedSources(){return requestedSources;}
    public java.time.LocalDate queryTime(){return queryTime;}
    public List<String> fetchDiagnostics(){return fetchDiagnostics;}
    public Map<String,Boolean> quality(){
        Set<Source> expanded=requestedSources.isEmpty()?Set.of():SourceRegistry.expand(requestedSources);
        List<Document> docs=expanded.stream().map(this::document).toList();
        return Map.of("requested_complete",docs.stream().allMatch(d->d.status()==Document.Status.FETCHED),
            "structure_valid",docs.stream().allMatch(Document::structurallyValid),"all_sources",expanded.equals(SourceRegistry.allSources()),
            "interpretation_complete",docs.stream().allMatch(d->d.status()==Document.Status.FETCHED) && docs.stream().filter(d->d.status()==Document.Status.FETCHED).flatMap(d->d.decodedOptions().stream()).allMatch(o->o.option().status().equals("KNOWN")));
    }
}
