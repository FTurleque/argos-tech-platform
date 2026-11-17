package io.argos.api.veille;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
 * <p>Les sources des abonnements {@code PERSONAL} de l'utilisateur s'ajoutent à cette union (décision Q-3
 * de {@code profils-veille}, ABO-04) : {@link #unir(List, List, List)}. Leurs candidats sont, eux aussi, déjà
 * autorisés par les droits.
 *
 * <p>Fonctions pures, sans état ni accès aux données.
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

    /**
     * Élément retenu par un ou plusieurs abonnements {@code PERSONAL} de l'utilisateur, déjà autorisé.
     *
     * @param candidat    élément, identifié par sa clé comme un candidat des profils
     * @param abonnements abonnements {@code PERSONAL} qui l'ont retenu
     */
    public record CandidatAbonnement<T>(Candidat<T> candidat, Set<UUID> abonnements) {
        public CandidatAbonnement {
            abonnements = Set.copyOf(abonnements);
        }
    }

    /** Rubrique d'un profil portée par un élément retenu. */
    public record RubriqueRetenue(String profil, String identifiant, String libelle) {
    }

    /**
     * Élément retenu, une seule fois.
     *
     * @param cle         identité de l'élément
     * @param contenu     contenu du premier candidat de cette clé
     * @param profils     profils actifs dont l'élément relève, dans l'ordre des profils actifs
     * @param rubriques   rubriques de ces profils, dans l'ordre des profils puis de chaque manifeste
     * @param gardeFous   textes des garde-fous des profils actifs qui rangent l'élément, à restituer avec lui
     * @param abonnements abonnements {@code PERSONAL} qui ont retenu l'élément, triés par leur forme textuelle ; vide s'il ne vient que
     *                    des profils
     */
    public record Retenu<T>(String cle, T contenu, List<String> profils, List<RubriqueRetenue> rubriques,
            List<String> gardeFous, List<UUID> abonnements) {
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
            fusionnes.merge(c.cle(), c, UnionProfils::fusionner);
        }
        List<Retenu<T>> retenus = new ArrayList<>();
        for (Candidat<T> c : fusionnes.values()) {
            List<String> profils = new ArrayList<>();
            List<RubriqueRetenue> rubriques = new ArrayList<>();
            Set<String> gardeFous = new LinkedHashSet<>();
            boolean bloque = false;
            for (ManifesteProfil profil : actifs) {
                List<Rubrique> duProfil = rubriquesQuiRangent(profil, c);
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
                retenus.add(new Retenu<>(c.cle(), c.contenu(), List.copyOf(profils), List.copyOf(rubriques),
                        List.copyOf(gardeFous), List.of()));
            }
        }
        return retenus;
    }

    /**
     * Union sans doublon des éléments des profils actifs et des éléments des abonnements {@code PERSONAL}
     * (décision Q-3) : les éléments des profils d'abord, dans l'ordre de {@link #unir(List, List)}, puis ceux
     * que seuls des abonnements retiennent, dans l'ordre de première apparition.
     *
     * <p>Un élément retenu à la fois par un profil et par un abonnement est restitué une fois, avec les
     * rubriques de ses profils et les abonnements qui l'ont retenu. Un élément que seuls des abonnements
     * retiennent n'a ni profil ni rubrique : aucun profil ne l'a sélectionné. Les garde-fous des profils actifs
     * qui rangent l'une de ses catégories s'appliquent dans les deux cas : un élément qu'un tel profil ne peut
     * pas diffuser n'est restitué par aucune voie, et leurs textes accompagnent l'élément. Un abonnement
     * n'ajoute que des éléments déjà autorisés : il n'élargit jamais les données visibles (ABO-05, PRF-05).
     *
     * @param actifs               manifestes des profils actifs publiés, dans l'ordre du catalogue
     * @param candidatsProfils     éléments déjà autorisés, candidats des profils actifs
     * @param candidatsAbonnements éléments déjà autorisés retenus par des abonnements {@code PERSONAL}
     */
    public static <T> List<Retenu<T>> unir(List<ManifesteProfil> actifs, List<Candidat<T>> candidatsProfils,
            List<CandidatAbonnement<T>> candidatsAbonnements) {
        Map<String, Candidat<T>> parCle = new LinkedHashMap<>();
        Map<String, Set<UUID>> abonnementsParCle = new LinkedHashMap<>();
        for (CandidatAbonnement<T> ca : candidatsAbonnements) {
            Candidat<T> c = ca.candidat();
            parCle.merge(c.cle(), c, UnionProfils::fusionner);
            abonnementsParCle.computeIfAbsent(c.cle(), k -> new LinkedHashSet<>()).addAll(ca.abonnements());
        }
        Map<String, Retenu<T>> retenus = new LinkedHashMap<>();
        for (Retenu<T> r : unir(actifs, candidatsProfils)) {
            retenus.put(r.cle(), r);
        }
        for (Map.Entry<String, Candidat<T>> e : parCle.entrySet()) {
            Candidat<T> c = e.getValue();
            List<UUID> abonnements = abonnementsParCle.get(e.getKey()).stream()
                    .sorted(Comparator.comparing(UUID::toString)).toList();
            Set<String> gardeFous = new LinkedHashSet<>();
            boolean bloque = false;
            for (ManifesteProfil profil : actifs) {
                if (rubriquesQuiRangent(profil, c).isEmpty()) {
                    continue;
                }
                if (!profil.diffusable(c.valideParUnExpert())) {
                    bloque = true;
                    break;
                }
                profil.gardeFous().stream().map(GardeFou::description).forEach(gardeFous::add);
            }
            Retenu<T> parUnProfil = retenus.get(c.cle());
            if (bloque) {
                // Prudence : un élément qu'un profil actif ne peut pas diffuser n'est restitué par aucune voie.
                retenus.remove(c.cle());
            } else if (parUnProfil != null) {
                retenus.put(c.cle(), new Retenu<>(parUnProfil.cle(), parUnProfil.contenu(), parUnProfil.profils(),
                        parUnProfil.rubriques(), parUnProfil.gardeFous(), abonnements));
            } else {
                retenus.put(c.cle(), new Retenu<>(c.cle(), c.contenu(), List.of(), List.of(), List.copyOf(gardeFous),
                        abonnements));
            }
        }
        return List.copyOf(retenus.values());
    }

    private static <T> List<Rubrique> rubriquesQuiRangent(ManifesteProfil profil, Candidat<T> c) {
        return profil.rubriques().stream()
                .filter(r -> r != null && c.categories().stream().anyMatch(r::range))
                .toList();
    }

    private static <T> Candidat<T> fusionner(Candidat<T> premier, Candidat<T> autre) {
        Set<String> categories = new LinkedHashSet<>(premier.categories());
        categories.addAll(autre.categories());
        // Une seule occurrence non validée suffit à ne pas considérer l'élément comme validé.
        return new Candidat<>(premier.cle(), categories, premier.valideParUnExpert() && autre.valideParUnExpert(),
                premier.contenu());
    }
}
