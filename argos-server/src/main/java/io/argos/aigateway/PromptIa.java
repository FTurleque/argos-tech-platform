package io.argos.aigateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Prompt versionné d'une tâche : consignes système et schéma de la sortie attendue (IAG-01, IAG-04).
 * Un texte publié sous une version n'en change plus ; toute modification exige une nouvelle version
 * (vérifié au démarrage par {@link RegistrePrompts}).
 */
public record PromptIa(String tache, int version, String consignes, List<ChampSortie> champs) {

    /** Type d'un champ de sortie. */
    public enum TypeChamp {
        /** Texte non vide, au plus {@code longueurMax} caractères. */
        TEXTE,
        /** Date ISO-8601 ({@code AAAA-MM-JJ}). */
        DATE,
        /** Liste d'entiers positifs, au plus {@code longueurMax} éléments. */
        ENTIERS
    }

    /** Champ de la sortie JSON attendue. */
    public record ChampSortie(String nom, TypeChamp type, boolean obligatoire, int longueurMax) {
    }

    public PromptIa {
        Objects.requireNonNull(tache, "tache");
        Objects.requireNonNull(consignes, "consignes");
        champs = List.copyOf(champs);
        if (version < 1 || champs.isEmpty()) {
            throw new IllegalArgumentException("prompt invalide : " + tache);
        }
    }

    /** Empreinte SHA-256 des consignes et du schéma : identifie le texte publié sous cette version. */
    public String empreinte() {
        StringBuilder contenu = new StringBuilder(consignes);
        champs.forEach(champ -> contenu.append('\n').append(champ.nom()).append(':').append(champ.type()).append(':')
                .append(champ.obligatoire()).append(':').append(champ.longueurMax()));
        return sha256(contenu.toString());
    }

    static String sha256(String texte) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texte.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException algorithmeAbsent) {
            throw new IllegalStateException(algorithmeAbsent);
        }
    }
}
