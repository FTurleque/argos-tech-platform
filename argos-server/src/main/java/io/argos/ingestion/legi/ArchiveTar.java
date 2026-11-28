package io.argos.ingestion.legi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;

/**
 * Lecture bornée d'une archive {@code .tar.gz} des jeux de données de la DILA (format ustar, noms longs
 * GNU et en-têtes pax). Seuls les fichiers réguliers dont le nom se termine par {@code .xml} sont
 * remis au consommateur ; les liens, répertoires et fichiers trop gros sont ignorés. Rien n'est écrit
 * sur le disque : un chemin d'archive ne peut donc pas sortir d'un répertoire.
 *
 * <p>Bornes : nombre d'entrées lues, taille d'un fichier remis et volume décompressé total ; au-delà,
 * la lecture échoue ({@link ArchiveInvalide}) : une archive hostile ne peut pas épuiser la mémoire.
 */
public final class ArchiveTar {

    private static final int BLOC = 512;
    /** Position et longueur du champ « taille » d'un en-tête ustar (octal). */
    private static final int POSITION_TAILLE = 124;
    private static final int LONGUEUR_TAILLE = 12;
    /** Position du type d'entrée dans l'en-tête ustar. */
    private static final int POSITION_TYPE = 156;
    /** Taille lue au plus d'une entrée de métadonnées (nom long GNU, en-tête pax). */
    private static final int METADONNEES_MAX = 65_536;
    private static final char NOM_LONG_GNU = 'L';
    private static final char ENTETE_PAX = 'x';
    private static final char FICHIER_REGULIER = '0';
    private static final char FICHIER_REGULIER_ANCIEN = '\0';

    /** Archive illisible ou hors des bornes. */
    public static final class ArchiveInvalide extends RuntimeException {
        public ArchiveInvalide(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final int entreesMax;
    private final long fichierMax;
    private final long totalMax;

    public ArchiveTar(int entreesMax, long fichierMax, long totalMax) {
        this.entreesMax = entreesMax;
        this.fichierMax = fichierMax;
        this.totalMax = totalMax;
    }

    /** Lit l'archive compressée et remet chaque fichier XML (chemin, contenu) au consommateur. */
    public void lire(InputStream archiveCompressee, BiConsumer<String, byte[]> consommateur) {
        try (InputStream flux = new GZIPInputStream(archiveCompressee)) {
            byte[] entete = new byte[BLOC];
            Parcours parcours = new Parcours();
            while (lireBloc(flux, entete) && !estVide(entete)) {
                lireEntree(flux, entete, parcours, consommateur);
            }
        } catch (IOException erreur) {
            throw new ArchiveInvalide("archive illisible : " + erreur.getMessage(), erreur);
        }
    }

    /** État du parcours d'une archive : entrées lues, volume décompressé, nom long en attente de son entrée. */
    private static final class Parcours {
        private int entrees;
        private long total;
        private String nomLong;
    }

    /** Traite l'entrée dont l'en-tête vient d'être lu : métadonnées retenues, fichier XML remis ou contenu sauté. */
    private void lireEntree(InputStream flux, byte[] entete, Parcours parcours, BiConsumer<String, byte[]> consommateur)
            throws IOException {
        long taille = tailleControlee(entete, parcours);
        char type = (char) entete[POSITION_TYPE];
        String nom = parcours.nomLong != null ? parcours.nomLong : nom(entete);
        parcours.nomLong = null;
        if (type == NOM_LONG_GNU || type == ENTETE_PAX) {
            byte[] meta = lireContenu(flux, taille, Math.min(taille, METADONNEES_MAX));
            parcours.nomLong = type == NOM_LONG_GNU ? texte(meta, 0, meta.length) : cheminPax(meta);
            return;
        }
        boolean regulier = type == FICHIER_REGULIER || type == FICHIER_REGULIER_ANCIEN;
        if (regulier && taille <= fichierMax && nom.endsWith(".xml")) {
            consommateur.accept(nom, lireContenu(flux, taille, taille));
        } else {
            sauter(flux, taille);
        }
    }

    /** Taille de l'entrée, comptée dans le parcours après contrôle des bornes : nombre d'entrées, taille lisible, volume total. */
    private long tailleControlee(byte[] entete, Parcours parcours) {
        if (++parcours.entrees > entreesMax) {
            throw new ArchiveInvalide("archive : plus de " + entreesMax + " entrées", null);
        }
        long taille = octal(entete, POSITION_TAILLE, LONGUEUR_TAILLE);
        if (taille < 0) {
            throw new ArchiveInvalide("archive : taille d'entrée invalide", null);
        }
        parcours.total += taille;
        if (parcours.total > totalMax) {
            throw new ArchiveInvalide("archive : volume décompressé supérieur à " + totalMax + " octets", null);
        }
        return taille;
    }

    private static boolean lireBloc(InputStream flux, byte[] bloc) throws IOException {
        int octetsLus = flux.readNBytes(bloc, 0, BLOC);
        if (octetsLus == 0) {
            return false;
        }
        if (octetsLus < BLOC) {
            throw new IOException("bloc tronqué");
        }
        return true;
    }

    private static boolean estVide(byte[] bloc) {
        for (byte octet : bloc) {
            if (octet != 0) {
                return false;
            }
        }
        return true;
    }

    /** Lit le contenu de l'entrée et son remplissage ; ne garde que {@code garde} octets. */
    private static byte[] lireContenu(InputStream flux, long taille, long garde) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(garde, 1 << 20));
        byte[] tampon = new byte[8192];
        long reste = taille;
        while (reste > 0) {
            int octetsLus = flux.read(tampon, 0, (int) Math.min(tampon.length, reste));
            if (octetsLus < 0) {
                throw new IOException("entrée tronquée");
            }
            long gardes = Math.max(0, Math.min(octetsLus, garde - (taille - reste)));
            out.write(tampon, 0, (int) gardes);
            reste -= octetsLus;
        }
        sauterRemplissage(flux, taille);
        return out.toByteArray();
    }

    private static void sauter(InputStream flux, long taille) throws IOException {
        long reste = taille;
        while (reste > 0) {
            long octetsSautes = flux.skip(reste);
            if (octetsSautes <= 0) {
                if (flux.read() < 0) {
                    throw new IOException("entrée tronquée");
                }
                octetsSautes = 1;
            }
            reste -= octetsSautes;
        }
        sauterRemplissage(flux, taille);
    }

    private static void sauterRemplissage(InputStream flux, long taille) throws IOException {
        long remplissage = (BLOC - taille % BLOC) % BLOC;
        if (remplissage > 0 && flux.readNBytes((int) remplissage).length < remplissage) {
            throw new IOException("remplissage tronqué");
        }
    }

    private static String nom(byte[] entete) {
        String nom = texte(entete, 0, 100);
        String prefixe = "ustar".equals(texte(entete, 257, 5)) ? texte(entete, 345, 155) : "";
        return prefixe.isEmpty() ? nom : prefixe + "/" + nom;
    }

    private static String cheminPax(byte[] meta) {
        for (String ligne : new String(meta, StandardCharsets.UTF_8).split("\n")) {
            int egal = ligne.indexOf(" path=");
            if (egal > 0) {
                return ligne.substring(egal + 6);
            }
        }
        return null;
    }

    private static String texte(byte[] octets, int debut, int longueur) {
        int fin = debut;
        while (fin < debut + longueur && fin < octets.length && octets[fin] != 0) {
            fin++;
        }
        return new String(octets, debut, fin - debut, StandardCharsets.UTF_8).strip();
    }

    private static long octal(byte[] octets, int debut, int longueur) {
        String valeur = texte(octets, debut, longueur).strip();
        if (valeur.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(valeur, 8);
        } catch (NumberFormatException illisible) {
            return -1;
        }
    }
}
