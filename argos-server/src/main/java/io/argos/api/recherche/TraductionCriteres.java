package io.argos.api.recherche;

import java.time.Clock;
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
import io.argos.commun.securite.Caller;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.impact.ProjetsConcernes;
import io.argos.signal.recherche.RechercheVeille;
import io.argos.inventory.InventoryQueries;
import io.argos.commun.AccesRefuse;
import io.argos.commun.DonneeInvalide;
import io.argos.commun.Introuvable;
import io.argos.commun.MotifRefus;

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
    Clock horloge;

    @Inject
    CatalogueProfils profils;

    @Inject
    InventoryQueries inventaires;

    @Inject
    RechercheVeille recherche;

    @Inject
    ProjetsConcernes projetsConcernes;

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
        return projetsConcernes.completer(recherche.rechercher(criteresVeille, appelant.accountId(), numero, nombre),
                appelant.accountId(), appelant.espacesMembre());
    }

    /**
     * Critères validés. Avec {@code verifierProjets}, chaque projet doit appartenir à un espace de
     * l'appelant ; sans, un projet devenu invisible ne fait que restreindre les résultats (rappel d'une
     * vue enregistrée : la requête n'expose de toute façon que le périmètre courant).
     */
    public RechercheVeille.Criteres traduire(CriteresRecherche criteresRecus, Caller appelant, boolean verifierProjets) {
        CriteresRecherche criteres = criteresRecus == null
                ? new CriteresRecherche(null, null, null, null, null, null, null) : criteresRecus;
        String texte = texte(criteres).orElse(null);
        Set<UUID> projets = projets(criteres, appelant, verifierProjets);
        Set<String> technologies = technologies(criteres);
        Set<String> types = typesRestreintsAuxProfils(criteres).orElse(null);
        Set<String> gravites = gravites(criteres).orElse(null);
        Instant depuis = depuis(criteres).orElse(null);
        return new RechercheVeille.Criteres(texte, types, projets, technologies, gravites, depuis, null, null);
    }

    /** Texte recherché sans espaces de bord ; vide s'il est absent ou blanc. */
    private static Optional<String> texte(CriteresRecherche criteres) {
        String texte = criteres.texte() == null || criteres.texte().isBlank() ? null : criteres.texte().strip();
        if (texte != null && texte.length() > TEXTE_MAX) {
            throw invalide("texte", "Le texte recherché compte au plus " + TEXTE_MAX + " caractères.");
        }
        return Optional.ofNullable(texte);
    }

    /** Projets filtrés ; avec {@code verifierProjets}, chacun doit être visible de l'appelant. */
    private Set<UUID> projets(CriteresRecherche criteres, Caller appelant, boolean verifierProjets) {
        Set<UUID> projets = new LinkedHashSet<>(validerValeurs("projets", criteres.projets()));
        if (verifierProjets) {
            projets.forEach(projet -> exigerProjetVisible(appelant, projet));
        }
        return projets;
    }

    private static Set<String> technologies(CriteresRecherche criteres) {
        Set<String> technologies = new LinkedHashSet<>();
        for (String technologie : validerValeurs("technologies", criteres.technologies())) {
            if (technologie == null || technologie.isBlank() || technologie.strip().length() > TECHNOLOGIE_MAX) {
                throw invalide("technologies", "Chaque technologie compte de 1 à " + TECHNOLOGIE_MAX + " caractères.");
            }
            technologies.add(technologie.strip());
        }
        return technologies;
    }

    /** Types demandés, en majuscules ; vide si aucun type n'est filtré. */
    private static Optional<Set<String>> types(CriteresRecherche criteres) {
        if (validerValeurs("types", criteres.types()).isEmpty()) {
            return Optional.empty();
        }
        Set<String> types = new LinkedHashSet<>();
        for (String valeur : criteres.types()) {
            String type = valeur == null ? "" : valeur.strip().toUpperCase(Locale.ROOT);
            if (!RechercheVeille.TYPES.contains(type)) {
                throw invalide("types", "Type inconnu ; types admis : " + String.join(", ", RechercheVeille.TYPES) + ".");
            }
            types.add(type);
        }
        return Optional.of(types);
    }

    /**
     * Types demandés restreints à ceux des catégories de sources des profils demandés ; sans type demandé, les types
     * des profils. Sans profil demandé, les types demandés tels quels ; vide si aucun type n'est filtré.
     */
    private Optional<Set<String>> typesRestreintsAuxProfils(CriteresRecherche criteres) {
        Optional<Set<String>> types = types(criteres);
        if (validerValeurs("profils", criteres.profils()).isEmpty()) {
            return types;
        }
        Set<String> categories = new LinkedHashSet<>();
        for (String identifiant : criteres.profils()) {
            ManifesteProfil profil = Optional.ofNullable(identifiant).map(String::strip).flatMap(profils::trouver)
                    .orElseThrow(() -> invalide("profils", "Profil de veille inconnu ou retiré du catalogue."));
            categories.addAll(profil.categoriesSources());
        }
        Set<String> duProfil = RechercheVeille.typesDesCategories(categories);
        if (types.isEmpty()) {
            return Optional.of(duProfil);
        }
        types.get().retainAll(duProfil);
        return types;
    }

    /** Gravités au moins égales à celle demandée ; vide si aucune gravité n'est filtrée. */
    private static Optional<Set<String>> gravites(CriteresRecherche criteres) {
        if (criteres.gravite() == null || criteres.gravite().isBlank()) {
            return Optional.empty();
        }
        if (!Gravite.reconnue(criteres.gravite())) {
            throw invalide("gravite", "Gravité inconnue ; gravités admises : " + String.join(", ", Gravite.GRADUEES) + ".");
        }
        return Optional.of(Gravite.auMoins(criteres.gravite()));
    }

    /** Instant de début de l'horizon demandé ; vide sans horizon. */
    private Optional<Instant> depuis(CriteresRecherche criteres) {
        if (criteres.horizonJours() == null) {
            return Optional.empty();
        }
        if (criteres.horizonJours() < 1 || criteres.horizonJours() > HORIZON_MAX) {
            throw invalide("horizonJours", "L'horizon est compris entre 1 et " + HORIZON_MAX + " jours.");
        }
        return Optional.of(horloge.instant().minus(Duration.ofDays(criteres.horizonJours())));
    }

    private void exigerProjetVisible(Caller appelant, UUID projet) {
        Optional<UUID> espace = inventaires.espaceDe(projet);
        if (espace.isEmpty()) {
            throw new Introuvable("Ressource introuvable.");
        }
        if (!appelant.memberOf(espace.get())) {
            throw new AccesRefuse(MotifRefus.NON_MEMBRE);
        }
    }

    /** Valeurs d'un filtre : au plus {@link #VALEURS_MAX}, aucune absente ; liste vide si le filtre est absent. */
    private static <T> List<T> validerValeurs(String attribut, List<T> valeurs) {
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
