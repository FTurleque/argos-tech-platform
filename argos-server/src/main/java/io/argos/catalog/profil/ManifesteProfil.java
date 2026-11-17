package io.argos.catalog.profil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
 * @param listes            listes fermées des valeurs suivies par type d'objet (facultatif), par exemple
 *                          les disciplines et les territoires du profil {@code artiste}
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
        List<GardeFou> gardeFous,
        List<ListeObjets> listes) {

    /** Seule version de format acceptée. */
    public static final int FORMAT_COURANT = 1;

    public ManifesteProfil {
        categoriesSources = copie(categoriesSources);
        connecteurs = copie(connecteurs);
        rubriques = copie(rubriques);
        typesObjetsSuivis = copie(typesObjetsSuivis);
        strategies = copie(strategies);
        gardeFous = copie(gardeFous);
        listes = copie(listes);
    }

    /** Manifeste sans liste de valeurs suivies. */
    public ManifesteProfil(int formatManifeste, String identifiant, String version, String libelle,
            String description, List<String> categoriesSources, List<String> connecteurs, List<Rubrique> rubriques,
            List<String> typesObjetsSuivis, List<StrategieDeclaree> strategies, List<GardeFou> gardeFous) {
        this(formatManifeste, identifiant, version, libelle, description, categoriesSources, connecteurs, rubriques,
                typesObjetsSuivis, strategies, gardeFous, List.of());
    }

    /** Liste des valeurs admises pour un type d'objet suivi. */
    public Optional<ListeObjets> liste(String typeObjet) {
        return listes.stream().filter(l -> l != null && typeObjet.equals(l.typeObjet())).findFirst();
    }

    /** Vrai si le profil déclare ce garde-fou. */
    public boolean impose(TypeGardeFou type) {
        return gardeFous.stream().anyMatch(g -> g != null && g.type() == type);
    }

    /**
     * Garde-fou {@link TypeGardeFou#VALIDATION_HUMAINE} (PRF-06) : vrai si un élément de ce profil peut
     * être diffusé, c'est-à-dire si le profil n'impose pas de validation humaine ou si l'élément a été
     * validé par un expert.
     *
     * @param valideParUnExpert vrai si l'élément a été validé par un expert
     */
    public boolean diffusable(boolean valideParUnExpert) {
        return valideParUnExpert || !impose(TypeGardeFou.VALIDATION_HUMAINE);
    }

    private static <T> List<T> copie(List<T> valeurs) {
        // List.copyOf refuse les éléments null : le validateur doit pouvoir les signaler.
        return valeurs == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(valeurs));
    }
}
