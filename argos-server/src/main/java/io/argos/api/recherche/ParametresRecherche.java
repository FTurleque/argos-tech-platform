package io.argos.api.recherche;

import java.util.List;

import jakarta.ws.rs.QueryParam;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

/**
 * Paramètres de requête de {@code GET /api/v1/recherche} ({@link RechercheResource#rechercher}), dans l'ordre et
 * avec les descriptions du contrat OpenAPI. Valeurs brutes : les identifiants de projet sont lus par la ressource,
 * la pagination ({@code page}, {@code taille}) après le contrôle des projets filtrés.
 */
public class ParametresRecherche {

    @Parameter(description = "Mots recherchés (syntaxe web : mots, \"expression\", or, -exclusion)")
    @QueryParam("q")
    String texte;

    @Parameter(description = "Identifiant de projet (répétable)")
    @QueryParam("projet")
    List<String> projets;

    @Parameter(description = "Technologie (répétable)")
    @QueryParam("technologie")
    List<String> technologies;

    @Parameter(description = "AVIS_SECURITE, ARTICLE ou CHANGEMENT_PAGE (répétable)")
    @QueryParam("type")
    List<String> types;

    @Parameter(description = "Gravité minimale : LOW, MODERATE, MEDIUM, HIGH, CRITICAL")
    @QueryParam("gravite")
    String gravite;

    @Parameter(description = "Éléments des N derniers jours (1 à 3 650)")
    @QueryParam("horizon")
    Integer horizon;

    @Parameter(description = "Profil de veille (répétable)")
    @QueryParam("profil")
    List<String> profils;

    @Parameter(description = RechercheResource.PAGE, schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
    @QueryParam("page")
    String page;

    @Parameter(description = RechercheResource.TAILLE, schema = @Schema(type = SchemaType.INTEGER, defaultValue = "20"))
    @QueryParam("taille")
    String taille;
}
