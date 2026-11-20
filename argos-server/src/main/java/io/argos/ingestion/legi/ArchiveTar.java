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
            long total = 0;
            int entrees = 0;
            String nomLong = null;
            while (true) {
                if (!lireBloc(flux, entete)) {
                    return;
                }
                if (estVide(entete)) {
                    return;
                }
                if (++entrees > entreesMax) {
                    throw new ArchiveInvalide("archive : plus de " + entreesMax + " entrées", null);
                }
                long taille = octal(entete, 124, 12);
                if (taille < 0) {
                    throw new ArchiveInvalide("archive : taille d'entrée invalide", null);
                }
                total += taille;
                if (total > totalMax) {
                    throw new ArchiveInvalide("archive : volume décompressé supérieur à " + totalMax + " octets", null);
                }
                char type = (char) entete[156];
                String nom = nomLong != null ? nomLong : nom(entete);
                nomLong = null;
                if (type == 'L' || type == 'x') {
                    byte[] meta = lireContenu(flux, taille, Math.min(taille, 65536));
                    if (type == 'L') {
                        nomLong = texte(meta, 0, meta.length);
                    } else {
                        nomLong = cheminPax(meta);
                    }
                    continue;
                }
                boolean regulier = type == '0' || type == '\0';
                if (regulier && taille <= fichierMax && nom.endsWith(".xml")) {
                    consommateur.accept(nom, lireContenu(flux, taille, taille));
                } else {
                    sauter(flux, taille);
                }
            }
        } catch (IOException erreur) {
            throw new ArchiveInvalide("archive illisible : " + erreur.getMessage(), erreur);
        }
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
