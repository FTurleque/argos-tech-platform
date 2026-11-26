package io.argos.api;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.argos.commun.DonneeInvalide;
import io.argos.inventory.DepotProjet;

/**
 * Saisies de l'administration des projets (INV-12) : création d'un projet et rattachement d'un dépôt, validées avant
 * tout appel au module {@code inventory}. Une saisie invalide donne 400 {@code DONNEE_INVALIDE} avec l'attribut en
 * cause (INV-12-S6).
 */
final class SaisieProjet {

    /** Forges dont la plateforme sait lire les dépôts (module {@code scm}). */
    static final Set<String> FORGES = Set.of("GITHUB", "GITLAB");

    /** Longueur maximale d'un nom de projet, d'un identifiant de dépôt, d'une branche ou d'un chemin. */
    static final int LONGUEUR_MAX = 200;

    /** Nombre maximal de manifestes déclarés pour un dépôt. */
    static final int MANIFESTES_MAX = 20;

    private static final String BRANCHE_PAR_DEFAUT = "main";

    /** Dépôt à rattacher ; seuls {@code forge} et {@code depot} sont obligatoires. */
    @Schema(name = "DepotProjet", description = "Dépôt GitHub ou GitLab d'un projet")
    public record Depot(
            @Schema(required = true, enumeration = { "GITHUB", "GITLAB" }) String forge,
            @Schema(required = true, description = "owner/repo (GitHub), chemin ou identifiant numérique (GitLab)") String depot,
            @Schema(description = "Branche principale ; main par défaut") String branche,
            @Schema(description = "Chemin de la SBOM : dans le dépôt (GitHub) ou dans les artefacts du job (GitLab)")
            String cheminSbom,
            @Schema(description = "Job CI GitLab qui publie la SBOM") String jobSbom,
            @Schema(description = "Manifestes lus pour l'inventaire de repli ; package.json et requirements.txt par défaut")
            List<String> manifestes) {
    }

    /** Projet à créer dans un espace, avec son premier dépôt facultatif. */
    @Schema(name = "NouveauProjet")
    public record NouveauProjet(@Schema(required = true) UUID espaceId, @Schema(required = true) String nom, Depot depot) {
    }

    /** Projet créé. */
    @Schema(name = "ProjetCree")
    public record ProjetCree(UUID id, UUID espaceId, String nom) {
    }

    /** Dépôt rattaché ; {@code principal} est vrai si le projet n'avait pas de dépôt principal. */
    @Schema(name = "DepotRattache")
    public record DepotRattache(UUID id, boolean principal) {
    }

    private SaisieProjet() {
    }

    /** Espace de la demande de création, obligatoire. */
    static UUID espace(NouveauProjet demande) {
        if (demande == null || demande.espaceId() == null) {
            throw invalide("L'espace du projet est obligatoire.", "espaceId");
        }
        return demande.espaceId();
    }

    /** Nom du projet, sans espaces de tête ni de fin, de 1 à {@value #LONGUEUR_MAX} caractères. */
    static String nom(NouveauProjet demande) {
        return texteObligatoire(demande.nom(), "Le nom du projet est obligatoire.", "nom");
    }

    /** Dépôt saisi, forge en majuscules et branche {@code main} par défaut. */
    static DepotProjet depot(Depot depot) {
        if (depot == null) {
            throw invalide("Le dépôt est obligatoire.", "depot");
        }
        String forge = depot.forge() == null ? "" : depot.forge().strip().toUpperCase(Locale.ROOT);
        if (!FORGES.contains(forge)) {
            throw invalide("La forge doit être GITHUB ou GITLAB.", "forge");
        }
        String identifiant = texteObligatoire(depot.depot(), "L'identifiant du dépôt est obligatoire.", "depot");
        String branche = texteFacultatif(depot.branche(), "branche");
        return new DepotProjet(forge, identifiant, branche == null ? BRANCHE_PAR_DEFAUT : branche,
                texteFacultatif(depot.jobSbom(), "jobSbom"), texteFacultatif(depot.cheminSbom(), "cheminSbom"), manifestes(depot));
    }

    /** Manifestes déclarés, ou {@code null} pour les manifestes par défaut du module {@code inventory}. */
    private static List<String> manifestes(Depot depot) {
        if (depot.manifestes() == null || depot.manifestes().isEmpty()) {
            return null;
        }
        if (depot.manifestes().size() > MANIFESTES_MAX) {
            throw invalide("Au plus " + MANIFESTES_MAX + " manifestes par dépôt.", "manifestes");
        }
        return depot.manifestes().stream().map(chemin -> texteObligatoire(chemin, "Chemin de manifeste vide.", "manifestes")).toList();
    }

    private static String texteObligatoire(String valeur, String message, String attribut) {
        String texte = texteFacultatif(valeur, attribut);
        if (texte == null) {
            throw invalide(message, attribut);
        }
        return texte;
    }

    private static String texteFacultatif(String valeur, String attribut) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String texte = valeur.strip();
        if (texte.length() > LONGUEUR_MAX || texte.contains(",")) {
            throw invalide("Valeur trop longue (" + LONGUEUR_MAX + " caractères au plus) ou contenant une virgule.", attribut);
        }
        return texte;
    }

    private static DonneeInvalide invalide(String message, String attribut) {
        return new DonneeInvalide("DONNEE_INVALIDE", message, attribut);
    }
}
