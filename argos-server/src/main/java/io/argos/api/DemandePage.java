package io.argos.api;

import jakarta.ws.rs.QueryParam;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import io.argos.commun.Pagination;

/**
 * Paramètres de requête {@value ParametresPagination#PAGE} et {@value ParametresPagination#TAILLE} d'une liste
 * paginée de l'API REST (ADR-0040), reçus par {@code @BeanParam} avec leur description OpenAPI. Valeurs brutes :
 * {@link #pagination()} les lit et refuse une valeur non entière.
 */
public class DemandePage {

    @Parameter(description = ParametresPagination.DESCRIPTION_PAGE,
            schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
    @QueryParam(ParametresPagination.PAGE)
    String page;

    @Parameter(description = ParametresPagination.DESCRIPTION_TAILLE,
            schema = @Schema(type = SchemaType.INTEGER, defaultValue = "" + Pagination.TAILLE_DEFAUT))
    @QueryParam(ParametresPagination.TAILLE)
    String taille;

    /**
     * Demande de page lue dans les deux paramètres ; absents, la première page à la taille par défaut.
     *
     * @throws io.argos.commun.DonneeInvalide si l'un des deux n'est pas un entier décimal
     */
    public Pagination pagination() {
        return ParametresPagination.pagination(page, taille);
    }
}
