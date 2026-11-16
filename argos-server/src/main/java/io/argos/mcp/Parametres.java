package io.argos.mcp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.argos.catalog.PackageKey;

/** Validation des paramètres d'outils, avant tout appel métier. */
final class Parametres {

    private Parametres() {
    }

    /**
     * Clé de paquet (PURL sans version) d'une dépendance désignée par son écosystème et son nom
     * (par exemple {@code Maven} et {@code org.exemple:lib}), ou par un PURL passé comme nom.
     */
    static String cle(String ecosysteme, String nom) {
        String n = obligatoire("name", nom);
        if (n.startsWith("pkg:")) {
            return PackageKey.parse(n).map(PackageKey.Purl::key)
                    .orElseThrow(() -> new ParametreInvalide("name : PURL non conforme."));
        }
        String e = obligatoire("ecosystem", ecosysteme);
        return PackageKey.fromEcosystem(e, n).orElseThrow(() -> new ParametreInvalide(
                "ecosystem et name : écosystème inconnu ou nom incomplet (Maven attend groupe:artefact)."));
    }

    static Optional<String> cleFacultative(String ecosysteme, String nom) {
        if ((nom == null || nom.isBlank()) && (ecosysteme == null || ecosysteme.isBlank())) {
            return Optional.empty();
        }
        return Optional.of(cle(ecosysteme, nom));
    }

    static String obligatoire(String nom, String valeur) {
        if (valeur == null || valeur.isBlank()) {
            throw new ParametreInvalide(nom + " : valeur obligatoire.");
        }
        if (valeur.length() > 500) {
            throw new ParametreInvalide(nom + " : 500 caractères au plus.");
        }
        return valeur.strip();
    }

    static UUID identifiant(String nom, String valeur) {
        try {
            return UUID.fromString(obligatoire(nom, valeur));
        } catch (IllegalArgumentException e) {
            throw new ParametreInvalide(nom + " : identifiant UUID attendu.");
        }
    }

    static Optional<UUID> identifiantFacultatif(String nom, String valeur) {
        return valeur == null || valeur.isBlank() ? Optional.empty() : Optional.of(identifiant(nom, valeur));
    }

    static int borne(String nom, Integer valeur, int defaut, int min, int max) {
        int v = valeur == null ? defaut : valeur;
        if (v < min || v > max) {
            throw new ParametreInvalide(nom + " : valeur attendue entre " + min + " et " + max + ".");
        }
        return v;
    }

    static List<String> liste(String nom, List<String> valeurs) {
        if (valeurs == null) {
            return List.of();
        }
        if (valeurs.size() > 50) {
            throw new ParametreInvalide(nom + " : 50 valeurs au plus.");
        }
        return valeurs.stream().filter(v -> v != null && !v.isBlank()).map(v -> obligatoire(nom, v)).toList();
    }
}
