package io.argos.api.catalogue;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.argos.catalog.miseenpage.CatalogueMisesEnPage;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.GardeFou;
import io.argos.catalog.profil.ManifesteProfil;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Lecture du catalogue des mises en page et des profils de veille publiés
 * (AFF-01, PRF-03-S1). Seuls les éléments publiés sont listés.
 */
@Path("/api/v1/catalogue")
@Tag(name = "Catalogue")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CatalogueResource {

    @Schema(name = "MiseEnPageCatalogue")
    public record MiseEnPageVue(String identifiant, String libelle, String description, boolean parDefaut) {
    }

    @Schema(name = "RubriqueProfil")
    public record RubriqueVue(String identifiant, String libelle) {
    }

    @Schema(name = "ProfilCatalogue")
    public record ProfilVue(String identifiant, String libelle, String description, String version,
            List<RubriqueVue> rubriques, List<String> gardeFous) {
    }

    private final CatalogueMisesEnPage misesEnPage;
    private final CatalogueProfils profils;

    @Inject
    public CatalogueResource(CatalogueMisesEnPage misesEnPage, CatalogueProfils profils) {
        this.misesEnPage = misesEnPage;
        this.profils = profils;
    }

    @GET
    @Path("/mises-en-page")
    @Operation(summary = "Mises en page publiées au catalogue")
    public List<MiseEnPageVue> misesEnPage() {
        return misesEnPage.publiees().stream()
                .map(miseEnPage -> new MiseEnPageVue(miseEnPage.identifiant(), miseEnPage.libelle(), miseEnPage.description(),
                        CatalogueMisesEnPage.PAR_DEFAUT.equals(miseEnPage.identifiant())))
                .toList();
    }

    @GET
    @Path("/profils")
    @Operation(summary = "Profils de veille publiés au catalogue")
    public List<ProfilVue> profils() {
        return profils.publies().stream().map(CatalogueResource::vue).toList();
    }

    private static ProfilVue vue(ManifesteProfil manifeste) {
        return new ProfilVue(manifeste.identifiant(), manifeste.libelle(), manifeste.description(), manifeste.version(),
                manifeste.rubriques().stream().map(rubrique -> new RubriqueVue(rubrique.identifiant(), rubrique.libelle())).toList(),
                manifeste.gardeFous().stream().map(GardeFou::description).toList());
    }
}
