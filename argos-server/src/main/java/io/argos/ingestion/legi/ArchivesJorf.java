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
import java.util.Set;
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
 * <p>Pour un jour de publication : lecture de l'index de l'adresse configurée ({@link #index()}, lu une
 * fois par collecte), téléchargement de chaque archive du jour qui n'a pas déjà été lue (requêtes
 * {@code GET} sans redirection, délai et taille bornés), lecture bornée des fichiers XML
 * ({@link ArchiveTar}, {@link LecteurJorf}). Un fichier XML refusé est ignoré et compté. Une archive en
 * erreur permanente (absente, trop volumineuse) est écartée et signalée sans bloquer le jour ; une erreur
 * transitoire (source injoignable, archive illisible) lève une {@link SourceException} : le jour reste à
 * collecter. Ce module ne connaît ni les espaces ni les domaines.
 */
@ApplicationScoped
public class ArchivesJorf {

    private static final Logger LOG = Logger.getLogger(ArchivesJorf.class);
    private static final Pattern ARCHIVE = Pattern.compile("JORF_(\\d{8})-(\\d{6})\\.tar\\.gz");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long INDEX_MAX = 4L * 1024 * 1024;

    /**
     * Résultat de la lecture d'un jour : textes assemblés, archives lues, fichiers refusés et archives
     * écartées pour une erreur permanente ({@code nom : motif}), à relire tant que le jour reste relu.
     */
    public record Lecture(List<TexteJorf> textes, List<String> archives, int fichiersRefuses, List<String> archivesEcartees) {
        public Lecture {
            textes = List.copyOf(textes);
            archives = List.copyOf(archives);
            archivesEcartees = List.copyOf(archivesEcartees);
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

    /** Noms de toutes les archives quotidiennes de l'index, dans l'ordre chronologique. */
    public List<String> index() {
        String index = new String(get(URI.create(base + "/"), INDEX_MAX), StandardCharsets.UTF_8);
        TreeSet<String> noms = new TreeSet<>();
        Matcher correspondance = ARCHIVE.matcher(index);
        while (correspondance.find()) {
            noms.add(correspondance.group());
        }
        return List.copyOf(noms);
    }

    /** Noms des archives de l'index publiées pour le jour, dans l'ordre chronologique. */
    public static List<String> archivesDu(List<String> index, LocalDate jour) {
        String prefixe = "JORF_" + jour.format(JOUR) + "-";
        return index.stream().filter(nom -> nom.startsWith(prefixe)).sorted().toList();
    }

    /** Textes publiés le jour donné, lus dans toutes ses archives (index relu). */
    public Lecture lire(LocalDate jour) {
        return lire(jour, index(), Set.of());
    }

    /**
     * Textes publiés le jour donné, lus dans les archives de l'index qui ne figurent pas parmi
     * {@code dejaLues}.
     */
    public Lecture lire(LocalDate jour, List<String> index, Set<String> dejaLues) {
        List<String> archives = new ArrayList<>();
        List<String> ecartees = new ArrayList<>();
        List<LecteurJorf.Element> elements = new ArrayList<>();
        int[] refuses = {0};
        for (String nom : archivesDu(index, jour)) {
            if (dejaLues.contains(nom)) {
                continue;
            }
            byte[] archiveCompressee;
            try {
                archiveCompressee = get(URI.create(base + "/" + nom), tailleMax);
            } catch (SourceException erreur) {
                if (erreur.kind() != SourceException.Kind.PERMANENT) {
                    throw erreur;
                }
                LOG.warnf(erreur, "archive %s écartée : %s", nom, erreur.getMessage());
                ecartees.add(nom + " : " + erreur.getMessage());
                continue;
            }
            archives.add(nom);
            try (InputStream flux = new ByteArrayInputStream(archiveCompressee)) {
                lecteurArchive.lire(flux, (chemin, xml) -> {
                    try {
                        LecteurJorf.lire(xml, nom).ifPresent(elements::add);
                    } catch (LecteurJorf.XmlRefuse refus) {
                        refuses[0]++;
                        LOG.infof(refus, "fichier %s de l'archive %s refusé : %s", chemin, nom, refus.getMessage());
                    }
                });
            } catch (ArchiveTar.ArchiveInvalide erreur) {
                throw SourceException.transientError("archive " + nom + " illisible : " + erreur.getMessage(), erreur);
            } catch (IOException erreur) {
                throw SourceException.transientError("archive " + nom + " illisible", erreur);
            }
        }
        return new Lecture(LecteurJorf.assembler(elements), archives, refuses[0], ecartees);
    }

    private byte[] get(URI uri, long max) {
        HttpRequest requete = HttpRequest.newBuilder(uri).timeout(delai).GET().build();
        try {
            HttpResponse<InputStream> reponse = client.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream corps = reponse.body()) {
                if (reponse.statusCode() != 200) {
                    throw new SourceException(reponse.statusCode() >= 500 || reponse.statusCode() == 429
                            ? SourceException.Kind.TRANSIENT : SourceException.Kind.PERMANENT,
                            reponse.statusCode(), null, "HTTP " + reponse.statusCode() + " de " + uri.getHost(), null);
                }
                byte[] octets = corps.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
                if (octets.length > max) {
                    throw new SourceException(SourceException.Kind.PERMANENT, 200, null,
                            "réponse de " + uri.getHost() + " supérieure à " + max + " octets", null);
                }
                return octets;
            }
        } catch (IOException erreur) {
            throw SourceException.transientError(uri.getHost() + " injoignable : " + erreur.getClass().getSimpleName(), erreur);
        } catch (InterruptedException erreur) {
            Thread.currentThread().interrupt();
            throw SourceException.transientError("lecture interrompue", erreur);
        }
    }
}
