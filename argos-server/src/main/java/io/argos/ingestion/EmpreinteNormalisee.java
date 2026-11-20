package io.argos.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Empreinte du contenu normalisé d'un avis (COL-10, décision J2 du porteur du projet, 2026-11-19 ;
 * définition du contenu normalisé : proposition P-2 du changement {@code automatiser-chaine-veille}).
 *
 * <p>Le contenu normalisé comprend l'identifiant externe, le titre, le résumé, la gravité, les alias
 * triés, les paquets affectés triés avec leurs plages et versions triées, la date de publication et la
 * date de retrait. La date de modification déclarée par la source et la date de collecte en sont
 * exclues : une simple republication ou une réorganisation du contenu brut ne change pas l'empreinte.
 * Le contenu est sérialisé en JSON canonique (clés triées, sans espaces) puis haché en SHA-256.
 */
public final class EmpreinteNormalisee {

    private static final ObjectMapper CANONIQUE = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    private static final JsonNodeFactory NOEUDS = JsonNodeFactory.instance;

    private EmpreinteNormalisee() {
    }

    /** Empreinte SHA-256, en hexadécimal minuscule (64 caractères), du contenu normalisé de l'avis. */
    public static String calculer(NormalizedAdvisory avis) {
        return sha256(canonique(avis));
    }

    /** JSON canonique du contenu normalisé : clés triées à chaque niveau, listes triées, sans espaces. */
    public static String canonique(NormalizedAdvisory avis) {
        ObjectNode racine = NOEUDS.objectNode();
        texte(racine, "externalId", avis.externalId());
        texte(racine, "title", avis.title());
        texte(racine, "summary", avis.summary());
        texte(racine, "severity", avis.severity());
        texte(racine, "publishedAt", instant(avis.publishedAt()));
        texte(racine, "withdrawnAt", instant(avis.withdrawnAt()));
        racine.set("aliases", triee(avis.aliases()));
        List<String> paquets = new ArrayList<>();
        for (NormalizedAdvisory.Affected paquetAffecte : avis.affected() == null ? List.<NormalizedAdvisory.Affected>of()
                : avis.affected()) {
            paquets.add(ecrire(paquet(paquetAffecte)));
        }
        ArrayNode affected = NOEUDS.arrayNode();
        paquets.stream().sorted().forEach(paquetCanonique -> affected.add(lire(paquetCanonique)));
        racine.set("affected", affected);
        return ecrire(racine);
    }

    private static ObjectNode paquet(NormalizedAdvisory.Affected affecte) {
        ObjectNode noeud = NOEUDS.objectNode();
        texte(noeud, "ecosystem", affecte.ecosystem());
        texte(noeud, "name", affecte.name());
        texte(noeud, "purl", affecte.purl());
        List<String> plages = new ArrayList<>();
        for (NormalizedAdvisory.Range plage : affecte.ranges() == null ? List.<NormalizedAdvisory.Range>of() : affecte.ranges()) {
            ObjectNode noeudPlage = NOEUDS.objectNode();
            texte(noeudPlage, "type", plage.type());
            texte(noeudPlage, "introduced", plage.introduced());
            texte(noeudPlage, "fixed", plage.fixed());
            texte(noeudPlage, "lastAffected", plage.lastAffected());
            plages.add(ecrire(noeudPlage));
        }
        ArrayNode ranges = NOEUDS.arrayNode();
        plages.stream().sorted().forEach(plageCanonique -> ranges.add(lire(plageCanonique)));
        noeud.set("ranges", ranges);
        noeud.set("versions", triee(affecte.versions()));
        return noeud;
    }

    private static ArrayNode triee(List<String> valeurs) {
        ArrayNode tableau = NOEUDS.arrayNode();
        if (valeurs != null) {
            valeurs.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).forEach(tableau::add);
        }
        return tableau;
    }

    private static void texte(ObjectNode noeud, String champ, String valeur) {
        if (valeur == null) {
            noeud.putNull(champ);
        } else {
            noeud.put(champ, valeur);
        }
    }

    private static String instant(Instant valeur) {
        return valeur == null ? null : valeur.toString();
    }

    private static String ecrire(Object noeud) {
        try {
            return CANONIQUE.writeValueAsString(CANONIQUE.convertValue(noeud, Object.class));
        } catch (JsonProcessingException erreur) {
            throw new IllegalStateException("sérialisation canonique impossible", erreur);
        }
    }

    private static ObjectNode lire(String json) {
        try {
            return (ObjectNode) CANONIQUE.readTree(json);
        } catch (JsonProcessingException erreur) {
            throw new IllegalStateException("relecture canonique impossible", erreur);
        }
    }

    static String sha256(String contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contenu.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException("SHA-256 indisponible", erreur);
        }
    }
}
