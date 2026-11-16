package io.argos.catalog.profil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manifeste d'un profil de veille : pack déclaratif publié au catalogue
 * (ADR-0024 point 1, exigences PRF-03 et PRF-04).
 *
 * <p>Format version {@value #FORMAT_COURANT}. Un manifeste assemble des briques existantes
 * (connecteurs, stratégies, garde-fous) ; il ne contient aucun code et n'accorde aucun droit
 * (PRF-05).
 *
 * @param formatManifeste   version du format de manifeste, {@value #FORMAT_COURANT} aujourd'hui
 * @param identifiant       identifiant stable du profil, par exemple {@code developpeur}
 * @param version           version du pack
 * @param libelle           nom affiché
 * @param description       description affichée dans le catalogue
 * @param categoriesSources catégories de sources surveillées
 * @param connecteurs       connecteurs de collecte utilisés
 * @param rubriques         rubriques ajoutées aux éléments de veille du profil
 * @param typesObjetsSuivis types d'objets auxquels les signaux sont rapprochés
 * @param strategies        stratégies de rapprochement, toutes déterministes
 * @param gardeFous         garde-fous appliqués avant diffusion
 */
public record ManifesteProfil(
        int formatManifeste,
        String identifiant,
        String version,
        String libelle,
        String description,
        List<String> categoriesSources,
        List<String> connecteurs,
        List<Rubrique> rubriques,
        List<String> typesObjetsSuivis,
        List<StrategieDeclaree> strategies,
        List<GardeFou> gardeFous) {

    /** Seule version de format acceptée. */
    public static final int FORMAT_COURANT = 1;

    public ManifesteProfil {
        categoriesSources = copie(categoriesSources);
        connecteurs = copie(connecteurs);
        rubriques = copie(rubriques);
        typesObjetsSuivis = copie(typesObjetsSuivis);
        strategies = copie(strategies);
        gardeFous = copie(gardeFous);
    }

    private static <T> List<T> copie(List<T> valeurs) {
        // List.copyOf refuse les éléments null : le validateur doit pouvoir les signaler.
        return valeurs == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(valeurs));
    }
}
