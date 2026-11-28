package io.argos.ingestion.osv;

import java.util.List;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Paramètres de la source OSV ({@code argos.sources.osv.*}).
 *
 * @param exportUrl adresse de l'export public OSV, sans barre oblique finale
 * @param apiUrl adresse de l'API OSV, sans barre oblique finale
 * @param ecosystemes écosystèmes suivis
 * @param seuilArchive nombre d'avis à lire pour un écosystème au-delà duquel ils sont lus dans l'archive de l'export
 * @param archiveTailleMax taille maximale d'une archive téléchargée, en octets
 */
public record ParametresOsv(String exportUrl, String apiUrl, List<String> ecosystemes, int seuilArchive, long archiveTailleMax) {

    public ParametresOsv {
        exportUrl = sansBarreFinale(exportUrl);
        apiUrl = sansBarreFinale(apiUrl);
        ecosystemes = List.copyOf(ecosystemes);
    }

    private static String sansBarreFinale(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Lecture des paramètres dans la configuration, pour l'injection dans {@link OsvAdvisoryFeed}. */
    @Dependent
    static class LectureConfiguration {

        @Inject
        @ConfigProperty(name = "argos.sources.osv.export-url")
        String exportUrl;

        @Inject
        @ConfigProperty(name = "argos.sources.osv.api-url")
        String apiUrl;

        @Inject
        @ConfigProperty(name = "argos.sources.osv.ecosystems")
        List<String> ecosystemes;

        @Inject
        @ConfigProperty(name = "argos.sources.osv.seuil-archive")
        int seuilArchive;

        @Inject
        @ConfigProperty(name = "argos.sources.osv.archive-taille-max-octets")
        long archiveTailleMax;

        @Produces
        @Dependent
        ParametresOsv parametres() {
            return new ParametresOsv(exportUrl, apiUrl, ecosystemes, seuilArchive, archiveTailleMax);
        }
    }
}
