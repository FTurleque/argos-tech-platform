package io.argos.ingestion.osv;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jboss.logging.Logger;

import io.argos.commun.http.ClientsHttp;
import io.argos.commun.http.LectureBornee;
import io.argos.ingestion.AdvisoryFeed;
import io.argos.ingestion.FeedEntry;
import io.argos.ingestion.RawAdvisory;
import io.argos.ingestion.SourceException;

/**
 * Lecture OSV d'une synchronisation (design D8 du changement {@code rendre-ingestion-asynchrone}) : avis annoncés
 * par les index et, pour un écosystème lu par archive, le fichier {@code all.zip} téléchargé dans un fichier temporaire
 * borné. Un avis absent de l'archive, ou une archive indisponible, est lu par l'API. Les noms d'entrée de l'archive ne
 * servent jamais de chemin : un avis est cherché par son nom {@code <identifiant>.json}. La fermeture supprime les
 * fichiers temporaires.
 */
final class LectureOsv implements AdvisoryFeed.Lecture {

    private static final Logger LOG = Logger.getLogger(LectureOsv.class);

    /** Archive d'un écosystème : fichier temporaire et son accès. */
    private record Archive(Path fichier, ZipFile zip) {
    }

    private final OsvAdvisoryFeed feed;
    private final long archiveTailleMax;
    private final List<FeedEntry> entrees = new ArrayList<>();
    private final List<Archive> archives = new ArrayList<>();
    private final Map<String, Archive> archiveParAvis = new HashMap<>();

    LectureOsv(OsvAdvisoryFeed feed, long archiveTailleMax) {
        this.feed = feed;
        this.archiveTailleMax = archiveTailleMax;
    }

    /** Télécharge l'archive de l'écosystème pour ces avis ; archive indisponible : les avis seront lus par l'API. */
    void archiver(String ecosystem, URI uri, List<FeedEntry> avis) {
        telecharger(ecosystem, uri).ifPresent(archive -> {
            archives.add(archive);
            avis.forEach(entree -> archiveParAvis.putIfAbsent(entree.externalId(), archive));
            LOG.infof("OSV %s : %d avis lus dans l'archive", ecosystem, avis.size());
        });
    }

    void ajouter(List<FeedEntry> avis) {
        entrees.addAll(avis);
    }

    LectureOsv trier() {
        entrees.sort(FeedEntry.ORDER);
        return this;
    }

    @Override
    public List<FeedEntry> entrees() {
        return List.copyOf(entrees);
    }

    @Override
    public RawAdvisory avis(FeedEntry entree) {
        Archive archive = archiveParAvis.get(entree.externalId());
        if (archive != null) {
            Optional<String> contenu = lireDansArchive(archive, entree.externalId());
            if (contenu.isPresent()) {
                return new RawAdvisory(entree.externalId(), contenu.get());
            }
        }
        return feed.fetch(entree);
    }

    @Override
    public void close() {
        for (Archive archive : archives) {
            fermer(archive);
        }
        archives.clear();
        archiveParAvis.clear();
    }

    private Optional<Archive> telecharger(String ecosystem, URI uri) {
        Path fichier = null;
        try {
            fichier = Files.createTempFile("argos-osv-", ".zip");
            feed.http().telechargerVers(uri, fichier, archiveTailleMax);
            return Optional.of(new Archive(fichier, new ZipFile(fichier.toFile())));
        } catch (SourceException indisponible) {
            LOG.warnf(indisponible, "archive OSV %s indisponible : avis lus par l'API (%s)", ecosystem, indisponible.getMessage());
        } catch (IOException illisible) {
            LOG.warnf(illisible, "archive OSV %s illisible : avis lus par l'API", ecosystem);
        }
        supprimer(fichier);
        return Optional.empty();
    }

    private static Optional<String> lireDansArchive(Archive archive, String identifiant) {
        ZipEntry entree = archive.zip().getEntry(identifiant + ".json");
        if (entree == null) {
            return Optional.empty();
        }
        try (InputStream flux = archive.zip().getInputStream(entree)) {
            return Optional.of(new String(LectureBornee.octets(flux, ClientsHttp.TAILLE_MAX_PAR_DEFAUT), StandardCharsets.UTF_8));
        } catch (IOException illisible) {
            LOG.warnf(illisible, "avis OSV %s illisible dans l'archive : lu par l'API", identifiant);
            return Optional.empty();
        }
    }

    private static void fermer(Archive archive) {
        try {
            archive.zip().close();
        } catch (IOException erreur) {
            LOG.warnf(erreur, "fermeture de l'archive OSV %s impossible", archive.fichier());
        }
        supprimer(archive.fichier());
    }

    private static void supprimer(Path fichier) {
        if (fichier == null) {
            return;
        }
        try {
            Files.deleteIfExists(fichier);
        } catch (IOException erreur) {
            LOG.warnf(erreur, "fichier temporaire %s non supprimé", fichier);
        }
    }
}
