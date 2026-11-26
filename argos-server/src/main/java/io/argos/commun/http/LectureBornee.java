package io.argos.commun.http;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Lecture d'un corps de réponse sous une taille maximale (ADR-0039, AUD-PER-11) : la lecture s'arrête dès que la
 * taille est dépassée et lève {@link ReponseTropVolumineuse}. Trois formes : octets en mémoire, lignes en flux,
 * copie vers un fichier.
 */
public final class LectureBornee {

    private static final int TAMPON = 64 * 1024;

    private LectureBornee() {
    }

    /** Corps entier en mémoire, au plus {@code tailleMax} octets. */
    public static byte[] octets(InputStream flux, long tailleMax) throws IOException {
        byte[] corps = flux.readNBytes((int) Math.min(Integer.MAX_VALUE - 8L, tailleMax + 1));
        if (corps.length > tailleMax) {
            throw new ReponseTropVolumineuse(tailleMax);
        }
        return corps;
    }

    /** Corps lu ligne à ligne en UTF-8, sans le garder en mémoire ; au plus {@code tailleMax} octets en tout. */
    public static void lignes(InputStream flux, long tailleMax, Consumer<String> consommateur) throws IOException {
        try (BufferedReader lecteur = new BufferedReader(new InputStreamReader(new FluxBorne(flux, tailleMax),
                StandardCharsets.UTF_8))) {
            String ligne = lecteur.readLine();
            while (ligne != null) {
                consommateur.accept(ligne);
                ligne = lecteur.readLine();
            }
        }
    }

    /** Copie le corps dans {@code cible} (remplacé), au plus {@code tailleMax} octets ; renvoie la taille copiée. */
    public static long copier(InputStream flux, Path cible, long tailleMax) throws IOException {
        try (InputStream borne = new FluxBorne(flux, tailleMax); OutputStream sortie = Files.newOutputStream(cible)) {
            return borne.transferTo(sortie);
        }
    }

    /** Flux qui lève {@link ReponseTropVolumineuse} au premier octet au-delà de la taille maximale. */
    private static final class FluxBorne extends FilterInputStream {

        private final long tailleMax;
        private long lus;

        FluxBorne(InputStream flux, long tailleMax) {
            super(flux);
            this.tailleMax = tailleMax;
        }

        @Override
        public int read() throws IOException {
            int octet = super.read();
            if (octet >= 0) {
                compter(1);
            }
            return octet;
        }

        @Override
        public int read(byte[] tampon, int debut, int longueur) throws IOException {
            int nombre = super.read(tampon, debut, Math.min(longueur, TAMPON));
            if (nombre > 0) {
                compter(nombre);
            }
            return nombre;
        }

        private void compter(int nombre) throws ReponseTropVolumineuse {
            lus += nombre;
            if (lus > tailleMax) {
                throw new ReponseTropVolumineuse(tailleMax);
            }
        }
    }
}
