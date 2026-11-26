package io.argos.ingestion.osv;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.ingestion.AdvisoryFeed;
import io.argos.ingestion.FeedEntry;
import io.argos.ingestion.HttpSourceClient;
import io.argos.ingestion.NormalizedAdvisory;
import io.argos.ingestion.RawAdvisory;
import io.argos.ingestion.SourceException;

/**
 * Adapter OSV, source principale (ADR-0010).
 *
 * <p>Les avis modifiés sont lus, en flux, dans l'index {@code <écosystème>/modified_id.csv} de l'export public OSV
 * (lignes {@code <date ISO>,<identifiant>}). Chaque avis est lu par {@code GET /v1/vulns/<identifiant>} de l'API OSV ;
 * au-delà de {@code argos.sources.osv.seuil-archive} avis à lire pour un écosystème (amorçage, longue interruption),
 * ils sont lus dans l'archive {@code <écosystème>/all.zip} de l'export, téléchargée une fois par synchronisation
 * ({@link LectureOsv}).
 */
@ApplicationScoped
public class OsvAdvisoryFeed implements AdvisoryFeed {

    public static final String SOURCE_ID = "OSV";

    private final HttpSourceClient http;
    private final String exportUrl;
    private final String apiUrl;
    private final List<String> ecosystems;
    private final int seuilArchive;
    private final long archiveTailleMax;

    @Inject
    public OsvAdvisoryFeed(HttpSourceClient http,
            @ConfigProperty(name = "argos.sources.osv.export-url") String exportUrl,
            @ConfigProperty(name = "argos.sources.osv.api-url") String apiUrl,
            @ConfigProperty(name = "argos.sources.osv.ecosystems") List<String> ecosystems,
            @ConfigProperty(name = "argos.sources.osv.seuil-archive") int seuilArchive,
            @ConfigProperty(name = "argos.sources.osv.archive-taille-max-octets") long archiveTailleMax) {
        this.http = http;
        this.exportUrl = trimSlash(exportUrl);
        this.apiUrl = trimSlash(apiUrl);
        this.ecosystems = List.copyOf(ecosystems);
        this.seuilArchive = seuilArchive;
        this.archiveTailleMax = archiveTailleMax;
    }

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public List<FeedEntry> changesSince(String position) {
        List<FeedEntry> entries = new ArrayList<>();
        for (String ecosystem : ecosystems) {
            entries.addAll(index(ecosystem, position));
        }
        entries.sort(FeedEntry.ORDER);
        return entries;
    }

    @Override
    public Lecture lire(String position) {
        LectureOsv lecture = new LectureOsv(this, archiveTailleMax);
        try {
            for (String ecosystem : ecosystems) {
                List<FeedEntry> entrees = index(ecosystem, position);
                if (entrees.size() > seuilArchive) {
                    lecture.archiver(ecosystem, URI.create(exportUrl + "/" + encode(ecosystem) + "/all.zip"), entrees);
                }
                lecture.ajouter(entrees);
            }
            return lecture.trier();
        } catch (RuntimeException erreur) {
            lecture.close();
            throw erreur;
        }
    }

    @Override
    public RawAdvisory fetch(FeedEntry entry) {
        return new RawAdvisory(entry.externalId(), http.telecharger(URI.create(apiUrl + "/v1/vulns/" + encode(entry.externalId()))));
    }

    @Override
    public NormalizedAdvisory normalize(RawAdvisory raw) {
        return OsvNormalizer.normalize(raw);
    }

    HttpSourceClient http() {
        return http;
    }

    /** Avis de l'index de l'écosystème postérieurs à la position, lus ligne à ligne. */
    private List<FeedEntry> index(String ecosystem, String position) {
        List<FeedEntry> entries = new ArrayList<>();
        http.lignes(URI.create(exportUrl + "/" + encode(ecosystem) + "/modified_id.csv"), line -> {
            if (!line.isBlank()) {
                FeedEntry entry = entree(ecosystem, line);
                if (entry.isAfter(position)) {
                    entries.add(entry);
                }
            }
        });
        return entries;
    }

    private static FeedEntry entree(String ecosystem, String line) {
        int comma = line.indexOf(',');
        if (comma < 0) {
            throw new SourceException(SourceException.Kind.PERMANENT, null, null, "index OSV non conforme pour " + ecosystem, null);
        }
        try {
            Instant modified = Instant.parse(line.substring(0, comma).trim());
            return new FeedEntry(line.substring(comma + 1).trim(), modified);
        } catch (DateTimeParseException error) {
            throw new SourceException(SourceException.Kind.PERMANENT, null, null, "date illisible dans l'index OSV pour " + ecosystem,
                    error);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
