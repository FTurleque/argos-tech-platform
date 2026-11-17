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
    public void lire(InputStream gz, BiConsumer<String, byte[]> consommateur) {
        try (InputStream in = new GZIPInputStream(gz)) {
            byte[] entete = new byte[BLOC];
            long total = 0;
            int entrees = 0;
            String nomLong = null;
            while (true) {
                if (!lireBloc(in, entete)) {
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
                    byte[] meta = lireContenu(in, taille, Math.min(taille, 65536));
                    if (type == 'L') {
                        nomLong = texte(meta, 0, meta.length);
                    } else {
                        nomLong = cheminPax(meta);
                    }
                    continue;
                }
                boolean regulier = type == '0' || type == '\0';
                if (regulier && taille <= fichierMax && nom.endsWith(".xml")) {
                    consommateur.accept(nom, lireContenu(in, taille, taille));
                } else {
                    sauter(in, taille);
                }
            }
        } catch (IOException e) {
            throw new ArchiveInvalide("archive illisible : " + e.getMessage(), e);
        }
    }

    private static boolean lireBloc(InputStream in, byte[] bloc) throws IOException {
        int lu = in.readNBytes(bloc, 0, BLOC);
        if (lu == 0) {
            return false;
        }
        if (lu < BLOC) {
            throw new IOException("bloc tronqué");
        }
        return true;
    }

    private static boolean estVide(byte[] bloc) {
        for (byte b : bloc) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /** Lit le contenu de l'entrée et son remplissage ; ne garde que {@code garde} octets. */
    private static byte[] lireContenu(InputStream in, long taille, long garde) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(garde, 1 << 20));
        byte[] tampon = new byte[8192];
        long reste = taille;
        while (reste > 0) {
            int n = in.read(tampon, 0, (int) Math.min(tampon.length, reste));
            if (n < 0) {
                throw new IOException("entrée tronquée");
            }
            long gardes = Math.max(0, Math.min(n, garde - (taille - reste)));
            out.write(tampon, 0, (int) gardes);
            reste -= n;
        }
        sauterRemplissage(in, taille);
        return out.toByteArray();
    }

    private static void sauter(InputStream in, long taille) throws IOException {
        long reste = taille;
        while (reste > 0) {
            long n = in.skip(reste);
            if (n <= 0) {
                if (in.read() < 0) {
                    throw new IOException("entrée tronquée");
                }
                n = 1;
            }
            reste -= n;
        }
        sauterRemplissage(in, taille);
    }

    private static void sauterRemplissage(InputStream in, long taille) throws IOException {
        long remplissage = (BLOC - taille % BLOC) % BLOC;
        if (remplissage > 0 && in.readNBytes((int) remplissage).length < remplissage) {
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

    private static String texte(byte[] b, int debut, int longueur) {
        int fin = debut;
        while (fin < debut + longueur && fin < b.length && b[fin] != 0) {
            fin++;
        }
        return new String(b, debut, fin - debut, StandardCharsets.UTF_8).strip();
    }

    private static long octal(byte[] b, int debut, int longueur) {
        String s = texte(b, debut, longueur).strip();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
