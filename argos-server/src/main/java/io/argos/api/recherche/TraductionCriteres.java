package io.argos.api.recherche;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.alerting.Gravite;
import io.argos.api.ParametresPagination;
import io.argos.api.security.Caller;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.impact.RechercheVeille;
import io.argos.inventory.InventoryQueries;
import io.argos.workspace.AccesRefuse;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.Introuvable;
import io.argos.workspace.MotifRefus;

/**
 * Validation des mots et filtres saisis (entrées non fiables) et traduction en critères de
 * {@link RechercheVeille}. Un filtre inconnu ou mal formé est refusé (400) ; un projet d'un autre
 * espace est refusé sans être révélé (404 journalisé, ACC-06).
 */
@ApplicationScoped
public class TraductionCriteres {

    static final int TEXTE_MAX = 500;
    static final int VALEURS_MAX = 20;
    static final int TECHNOLOGIE_MAX = 100;
    static final int HORIZON_MAX = 3650;

    @Inject
    CatalogueProfils profils;

    @Inject
    InventoryQueries inventaires;

    @Inject
    RechercheVeille recherche;

    /**
     * Recherche commune au point d'accès {@code /api/v1/recherche} et au rappel d'une vue enregistrée. Les critères,
     * dont le contrôle des projets filtrés, sont validés avant la lecture de {@code page} et {@code taille} (décision
     * H5 du porteur du projet, 2026-11-19) : un projet filtré inconnu ou d'un autre espace reçoit 404 même avec un
     * paramètre de pagination non entier ou hors bornes.
     */
    public RechercheVeille.Resultats executer(CriteresRecherche criteres, Caller appelant, boolean verifierProjets,
            String page, String taille) {
        RechercheVeille.Criteres criteresVeille = traduire(criteres, appelant, verifierProjets);
        int numero = ParametresPagination.entier(page, "page", 0);
        int nombre = ParametresPagination.entier(taille, "taille", RechercheResource.TAILLE_PAR_DEFAUT);
        if (numero < 0 || nombre < 1 || nombre > 100) {
            throw invalide("taille", "page ≥ 0 et taille comprise entre 1 et 100.");
        }
        return recherche.rechercher(criteresVeille, appelant.accountId(), appelant.memberWorkspaces(), numero, nombre);
    }

    /**
     * Critères validés. Avec {@code verifierProjets}, chaque projet doit appartenir à un espace de
     * l'appelant ; sans, un projet devenu invisible ne fait que restreindre les résultats (rappel d'une
     * vue enregistrée : la requête n'expose de toute façon que le périmètre courant).
     */
    public RechercheVeille.Criteres traduire(CriteresRecherche criteresRecus, Caller appelant, boolean verifierProjets) {
        CriteresRecherche criteres = criteresRecus == null
                ? new CriteresRecherche(null, null, null, null, null, null, null) : criteresRecus;
        String texte = criteres.texte() == null || criteres.texte().isBlank() ? null : criteres.texte().strip();
        if (texte != null && texte.length() > TEXTE_MAX) {
            throw invalide("texte", "Le texte recherché compte au plus " + TEXTE_MAX + " caractères.");
        }
        Set<UUID> projets = new LinkedHashSet<>(liste("projets", criteres.projets()));
        if (verifierProjets) {
            projets.forEach(projet -> exigerProjetVisible(appelant, projet));
        }
        Set<String> technologies = new LinkedHashSet<>();
        for (String technologie : liste("technologies", criteres.technologies())) {
            if (technologie == null || technologie.isBlank() || technologie.strip().length() > TECHNOLOGIE_MAX) {
                throw invalide("technologies", "Chaque technologie compte de 1 à " + TECHNOLOGIE_MAX + " caractères.");
            }
            technologies.add(technologie.strip());
        }
        Set<String> types = null;
        if (!liste("types", criteres.types()).isEmpty()) {
            types = new LinkedHashSet<>();
            for (String valeur : criteres.types()) {
                String type = valeur == null ? "" : valeur.strip().toUpperCase(Locale.ROOT);
                if (!RechercheVeille.TYPES.contains(type)) {
                    throw invalide("types", "Type inconnu ; types admis : " + String.join(", ", RechercheVeille.TYPES) + ".");
                }
                types.add(type);
            }
        }
        if (!liste("profils", criteres.profils()).isEmpty()) {
            Set<String> categories = new LinkedHashSet<>();
            for (String identifiant : criteres.profils()) {
                ManifesteProfil profil = Optional.ofNullable(identifiant).map(String::strip).flatMap(profils::trouver)
                        .orElseThrow(() -> invalide("profils", "Profil de veille inconnu ou retiré du catalogue."));
                categories.addAll(profil.categoriesSources());
            }
            Set<String> duProfil = RechercheVeille.typesDesCategories(categories);
            if (types == null) {
                types = duProfil;
            } else {
                types.retainAll(duProfil);
            }
        }
        Set<String> gravites = null;
        if (criteres.gravite() != null && !criteres.gravite().isBlank()) {
            if (!Gravite.reconnue(criteres.gravite())) {
                throw invalide("gravite", "Gravité inconnue ; gravités admises : " + String.join(", ", Gravite.GRADUEES) + ".");
            }
            gravites = Gravite.auMoins(criteres.gravite());
        }
        Instant depuis = null;
        if (criteres.horizonJours() != null) {
            if (criteres.horizonJours() < 1 || criteres.horizonJours() > HORIZON_MAX) {
                throw invalide("horizonJours", "L'horizon est compris entre 1 et " + HORIZON_MAX + " jours.");
            }
            depuis = Instant.now().minus(Duration.ofDays(criteres.horizonJours()));
        }
        return new RechercheVeille.Criteres(texte, types, projets, technologies, gravites, depuis, null, null);
    }

    private void exigerProjetVisible(Caller appelant, UUID projet) {
        Optional<UUID> espace = inventaires.workspaceOf(projet);
        if (espace.isEmpty()) {
            throw new Introuvable("Ressource introuvable.");
        }
        if (!appelant.memberOf(espace.get())) {
            throw new AccesRefuse(MotifRefus.NON_MEMBRE);
        }
    }

    private static <T> List<T> liste(String attribut, List<T> valeurs) {
        if (valeurs == null) {
            return List.of();
        }
        if (valeurs.size() > VALEURS_MAX) {
            throw invalide(attribut, VALEURS_MAX + " valeurs au plus.");
        }
        if (valeurs.stream().anyMatch(valeur -> valeur == null)) {
            throw invalide(attribut, "Valeur absente.");
        }
        return valeurs;
    }

    private static DonneeInvalide invalide(String attribut, String message) {
        return new DonneeInvalide("CRITERE_INVALIDE", message, attribut);
    }
}
