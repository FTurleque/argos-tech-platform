package io.argos.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Point d'entrée technique du socle : permet à l'IHM web et au client desktop
 * de vérifier qu'ils joignent bien un serveur ARGOS et de quelle version il s'agit.
 *
 * <p>Ne porte aucune règle métier. L'état de santé détaillé (base de données, etc.)
 * est exposé séparément par SmallRye Health sur {@code /q/health}.
 */
@Path("/api/v1/system/info")
@Tag(name = "Système")
public class SystemInfoResource {

    /**
     * Réponse publique : aucune donnée de projet ni d'utilisateur. {@code oidcIssuer} et {@code oidcClientId}
     * désignent l'émetteur public et le client public de l'IHM : l'IHM les lit avant d'ouvrir sa session
     * (ACC-11) ; ils sont déjà visibles de quiconque lance une connexion et ne portent aucun secret.
     */
    public record SystemInfo(String application, String version, String apiVersion, String oidcIssuer,
            String oidcClientId) {
    }

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "argos-server")
    String applicationName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "inconnue")
    String applicationVersion;

    @ConfigProperty(name = "argos.ui.oidc.emetteur")
    String emetteurPublic;

    @ConfigProperty(name = "argos.ui.oidc.client-id")
    String clientIdIhm;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Identité et version du serveur ARGOS")
    public SystemInfo lireIdentiteServeur() {
        return new SystemInfo(applicationName, applicationVersion, "v1", emetteurPublic, clientIdIhm);
    }
}
