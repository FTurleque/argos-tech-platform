package io.argos.api.veille;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.argos.catalog.profil.GardeFou;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.catalog.profil.Rubrique;

/**
 * Union des veilles des profils actifs (PRF-02, ADR-0024 points 2 et 7, DC-2) : à partir des éléments
 * déjà autorisés par les droits (RBAC + RLS), retient ceux qui relèvent d'au moins un profil actif,
 * applique les garde-fous de chaque profil, puis fusionne sans doublon en ajoutant les rubriques de
 * chacun des profils dont l'élément relève.
 *
 * <p>Un élément relève d'un profil quand l'une des rubriques du profil range l'une de ses catégories
 * de sources ({@link Rubrique#range(String)}). Les garde-fous d'un profil actif s'appliquent à tout
 * élément qu'il range : un élément non validé par un expert qui relève d'un profil actif imposant
 * {@code VALIDATION_HUMAINE} n'est pas restitué du tout, même s'il relève aussi d'un autre profil actif
 * ({@link ManifesteProfil#diffusable(boolean)}). Les garde-fous d'un profil que l'utilisateur n'active pas
 * ne changent pas sa veille (PRF-04) ; la source applique ses propres règles avant (une fiche réglementaire
 * non validée n'est jamais candidate, ADR-0018). Les profils ne font que restreindre : un élément absent
 * des candidats n'est jamais ajouté (PRF-05).
 *
 * <p>Fonction pure, sans état ni accès aux données.
 */
public final class UnionProfils {

    /**
     * Élément de veille autorisé, avant application des profils.
     *
     * @param cle               identité de l'élément : deux candidats de même clé sont un seul élément
     * @param categories        catégories de sources dont relève l'élément (manifestes, {@code categoriesSources})
     * @param valideParUnExpert vrai si l'élément a été validé par un expert (garde-fou {@code VALIDATION_HUMAINE})
     * @param contenu           contenu restitué, inchangé
     */
    public record Candidat<T>(String cle, Set<String> categories, boolean valideParUnExpert, T contenu) {
        public Candidat {
            categories = Set.copyOf(categories);
        }
    }

    /** Rubrique d'un profil portée par un élément retenu. */
    public record RubriqueRetenue(String profil, String identifiant, String libelle) {
    }

    /**
     * Élément retenu, une seule fois.
     *
     * @param contenu   contenu du premier candidat de cette clé
     * @param profils   profils actifs dont l'élément relève, dans l'ordre des profils actifs
     * @param rubriques rubriques de ces profils, dans l'ordre des profils puis de chaque manifeste
     * @param gardeFous textes des garde-fous de ces profils, à restituer avec l'élément
     */
    public record Retenu<T>(T contenu, List<String> profils, List<RubriqueRetenue> rubriques, List<String> gardeFous) {
    }

    private UnionProfils() {
    }

    /**
     * Union sans doublon des éléments des profils actifs, dans l'ordre de première apparition des candidats.
     *
     * @param actifs     manifestes des profils actifs publiés, dans l'ordre du catalogue
     * @param candidats  éléments déjà autorisés pour l'utilisateur
     */
    public static <T> List<Retenu<T>> unir(List<ManifesteProfil> actifs, List<Candidat<T>> candidats) {
        Map<String, Candidat<T>> fusionnes = new LinkedHashMap<>();
        for (Candidat<T> c : candidats) {
            fusionnes.merge(c.cle(), c, (premier, autre) -> {
                Set<String> categories = new LinkedHashSet<>(premier.categories());
                categories.addAll(autre.categories());
                // Une seule occurrence non validée suffit à ne pas considérer l'élément comme validé.
                return new Candidat<>(premier.cle(), categories,
                        premier.valideParUnExpert() && autre.valideParUnExpert(), premier.contenu());
            });
        }
        List<Retenu<T>> retenus = new ArrayList<>();
        for (Candidat<T> c : fusionnes.values()) {
            List<String> profils = new ArrayList<>();
            List<RubriqueRetenue> rubriques = new ArrayList<>();
            Set<String> gardeFous = new LinkedHashSet<>();
            boolean bloque = false;
            for (ManifesteProfil profil : actifs) {
                List<Rubrique> duProfil = profil.rubriques().stream()
                        .filter(r -> r != null && c.categories().stream().anyMatch(r::range))
                        .toList();
                if (duProfil.isEmpty()) {
                    continue;
                }
                if (!profil.diffusable(c.valideParUnExpert())) {
                    bloque = true;
                    break;
                }
                profils.add(profil.identifiant());
                duProfil.forEach(r -> rubriques.add(new RubriqueRetenue(profil.identifiant(), r.identifiant(), r.libelle())));
                profil.gardeFous().stream().map(GardeFou::description).forEach(gardeFous::add);
            }
            if (!bloque && !profils.isEmpty()) {
                retenus.add(new Retenu<>(c.contenu(), List.copyOf(profils), List.copyOf(rubriques), List.copyOf(gardeFous)));
            }
        }
        return retenus;
    }
}
