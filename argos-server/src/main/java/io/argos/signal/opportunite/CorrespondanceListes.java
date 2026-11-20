package io.argos.signal.opportunite;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.argos.catalog.profil.ListeObjets;
import io.argos.catalog.profil.ValeurListe;

/**
 * Correspondance déterministe entre les valeurs lues dans une source (« Arts visuels »,
 * « OCCITANIE ») et les identifiants des listes du profil ({@code arts-visuels}, {@code occitanie}).
 *
 * <p>Une valeur correspond si sa forme normalisée (minuscules, accents retirés, séparateurs réduits à
 * un tiret) est celle de l'identifiant ou du libellé d'une valeur de la liste, ou un synonyme déclaré
 * ici. Une valeur sans correspondance est ignorée : aucune IA ne devine une discipline ou un territoire.
 */
final class CorrespondanceListes {

    /** Synonymes courants, forme normalisée → identifiant de la liste. */
    private static final Map<String, String> SYNONYMES = Map.of(
            "national", "france",
            "nationale", "france",
            "france-metropolitaine", "france",
            "arts-plastiques", "arts-visuels",
            "audiovisuel", "cinema-audiovisuel",
            "cinema", "cinema-audiovisuel",
            "metiers-d-art", "design-metiers-art",
            "design", "design-metiers-art",
            "livre", "litterature");

    private CorrespondanceListes() {
    }

    /** Identifiants de la liste correspondant aux valeurs lues, sans doublon, dans l'ordre de lecture. */
    static List<String> identifiants(List<String> lues, Optional<ListeObjets> liste) {
        if (liste.isEmpty() || lues == null) {
            return List.of();
        }
        Set<String> trouves = new LinkedHashSet<>();
        for (String lue : lues) {
            String normalisee = normaliser(lue);
            if (normalisee.isEmpty()) {
                continue;
            }
            String synonyme = SYNONYMES.get(normalisee);
            for (ValeurListe valeur : liste.get().valeurs()) {
                if (normalisee.equals(valeur.identifiant()) || normalisee.equals(normaliser(valeur.libelle()))
                        || valeur.identifiant().equals(synonyme)) {
                    trouves.add(valeur.identifiant());
                }
            }
        }
        return List.copyOf(trouves);
    }

    static String normaliser(String valeur) {
        if (valeur == null) {
            return "";
        }
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccent.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    /** Lien http(s) absolu conservé ; tout autre schéma ({@code javascript:}, {@code data:}…) est écarté. */
    static String lienSur(String lien) {
        if (lien == null || lien.isBlank() || lien.length() > 2000) {
            return null;
        }
        try {
            URI uri = new URI(lien.strip());
            String schema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((schema.equals("http") || schema.equals("https")) && uri.getHost() != null && uri.getRawUserInfo() == null) {
                return uri.toString();
            }
        } catch (URISyntaxException malFormee) {
            return null;
        }
        return null;
    }
}
