package io.argos.ingestion.legi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.ingestion.SourceException;

/**
 * Connecteur des jeux de données JORF de la DILA (module {@code source-ingestion}, REG-01) : archives
 * quotidiennes {@code JORF_AAAAMMJJ-HHMMSS.tar.gz} publiées sous Licence Ouverte / Open Licence
 * (source : DILA, https://echanges.dila.gouv.fr/OPENDATA/JORF/). L'API PISTE de Légifrance n'est pas
 * utilisée (décision Q-1 du 2026-11-17).
 *
 * <p>Pour un jour de publication : lecture de l'index de l'adresse configurée, téléchargement de chaque
 * archive du jour (requêtes {@code GET} sans redirection, délai et taille bornés), lecture bornée des
 * fichiers XML ({@link ArchiveTar}, {@link LecteurJorf}). Un fichier XML refusé est ignoré et compté ;
 * l'index ou une archive injoignable lève une {@link SourceException} : le jour reste à collecter.
 * Ce module ne connaît ni les espaces ni les domaines.
 */
@ApplicationScoped
public class ArchivesJorf {

    private static final Logger LOG = Logger.getLogger(ArchivesJorf.class);
    private static final Pattern ARCHIVE = Pattern.compile("JORF_(\\d{8})-(\\d{6})\\.tar\\.gz");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long INDEX_MAX = 4L * 1024 * 1024;

    /** Résultat de la lecture d'un jour : textes assemblés, archives lues et fichiers refusés. */
    public record Lecture(List<TexteJorf> textes, List<String> archives, int fichiersRefuses) {
        public Lecture {
            textes = List.copyOf(textes);
            archives = List.copyOf(archives);
        }
    }

    private final String base;
    private final Duration delai;
    private final long tailleMax;
    private final ArchiveTar lecteurArchive;
    private final HttpClient client;

    @Inject
    public ArchivesJorf(@ConfigProperty(name = "argos.reglementaire.jorf.url") String base,
            @ConfigProperty(name = "argos.reglementaire.jorf.delai") Duration delai,
            @ConfigProperty(name = "argos.reglementaire.jorf.taille-max-octets") long tailleMax,
            @ConfigProperty(name = "argos.reglementaire.jorf.entrees-max") int entreesMax,
            @ConfigProperty(name = "argos.reglementaire.jorf.decompresse-max-octets") long decompresseMax) {
        this.base = base.replaceAll("/+$", "");
        this.delai = delai;
        this.tailleMax = tailleMax;
        this.lecteurArchive = new ArchiveTar(entreesMax, 5L * 1024 * 1024, decompresseMax);
        this.client = HttpClient.newBuilder().connectTimeout(delai).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Noms des archives publiées pour le jour, dans l'ordre chronologique. */
    public List<String> archivesDu(LocalDate jour) {
        String index = new String(get(URI.create(base + "/"), INDEX_MAX), StandardCharsets.UTF_8);
        String cle = jour.format(JOUR);
        TreeSet<String> noms = new TreeSet<>();
        Matcher m = ARCHIVE.matcher(index);
        while (m.find()) {
            if (m.group(1).equals(cle)) {
                noms.add(m.group());
            }
        }
        return List.copyOf(noms);
    }

    /** Textes publiés le jour donné, lus dans toutes ses archives. */
    public Lecture lire(LocalDate jour) {
        List<String> archives = archivesDu(jour);
        List<LecteurJorf.Element> elements = new ArrayList<>();
        int[] refuses = {0};
        for (String nom : archives) {
            byte[] gz = get(URI.create(base + "/" + nom), tailleMax);
            try (InputStream in = new ByteArrayInputStream(gz)) {
                lecteurArchive.lire(in, (chemin, xml) -> {
                    try {
                        LecteurJorf.lire(xml, nom).ifPresent(elements::add);
                    } catch (LecteurJorf.XmlRefuse e) {
                        refuses[0]++;
                        LOG.infof("fichier %s de l'archive %s refusé : %s", chemin, nom, e.getMessage());
                    }
                });
            } catch (ArchiveTar.ArchiveInvalide e) {
                throw SourceException.transientError("archive " + nom + " illisible : " + e.getMessage(), e);
            } catch (IOException e) {
                throw SourceException.transientError("archive " + nom + " illisible", e);
            }
        }
        return new Lecture(LecteurJorf.assembler(elements), archives, refuses[0]);
    }

    private byte[] get(URI uri, long max) {
        HttpRequest requete = HttpRequest.newBuilder(uri).timeout(delai).GET().build();
        try {
            HttpResponse<InputStream> r = client.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream corps = r.body()) {
                if (r.statusCode() != 200) {
                    throw new SourceException(r.statusCode() >= 500 || r.statusCode() == 429
                            ? SourceException.Kind.TRANSIENT : SourceException.Kind.PERMANENT,
                            r.statusCode(), null, "HTTP " + r.statusCode() + " de " + uri.getHost(), null);
                }
                byte[] octets = corps.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
                if (octets.length > max) {
                    throw new SourceException(SourceException.Kind.PERMANENT, 200, null,
                            "réponse de " + uri.getHost() + " supérieure à " + max + " octets", null);
                }
                return octets;
            }
        } catch (IOException e) {
            throw SourceException.transientError(uri.getHost() + " injoignable : " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw SourceException.transientError("lecture interrompue", e);
        }
    }
}
