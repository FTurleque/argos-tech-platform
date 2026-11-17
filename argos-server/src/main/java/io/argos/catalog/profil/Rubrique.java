package io.argos.catalog.profil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rubrique déclarée par un profil : classe un élément de veille pour ce profil
 * (ADR-0024 point 4, exigence PRF-02).
 *
 * <p>Une rubrique range les éléments des catégories de sources qu'elle déclare. Un élément de veille
 * relève d'un profil actif quand l'une de ses rubriques déclare la catégorie de l'élément ; il porte
 * alors cette rubrique. Une rubrique sans catégorie ne classe aucun élément de la veille restituée
 * par profils ({@code GET /api/v1/me/veille}).
 *
 * @param identifiant identifiant stable, unique dans le profil
 * @param libelle     libellé affiché
 * @param categories  catégories de sources rangées dans la rubrique, toutes déclarées dans
 *                    {@code categoriesSources} du manifeste (facultatif)
 */
public record Rubrique(String identifiant, String libelle, List<String> categories) {

    public Rubrique {
        // List.copyOf refuse les éléments null : le validateur doit pouvoir les signaler.
        categories = categories == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(categories));
    }

    /** Rubrique qui ne range aucune catégorie de sources. */
    public Rubrique(String identifiant, String libelle) {
        this(identifiant, libelle, List.of());
    }

    /** Vrai si la rubrique range les éléments de cette catégorie de sources. */
    public boolean range(String categorie) {
        return categorie != null && categories.contains(categorie);
    }
}
