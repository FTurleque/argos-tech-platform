package io.argos.api.comptes;

import static io.argos.testsupport.Api.enTantQue;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import io.argos.testsupport.Api;

/** Contrat OpenAPI des comptes, espaces, membres et invitations (tâche 3.1). */
@QuarkusTest
class ContratOpenApiTest {

    @Test
    void contratPublie() {
        ValidatableResponse contrat = enTantQue("alice").queryParam("format", "json")
                .when().get("/q/openapi")
                .then().statusCode(200);

        operation(contrat, "/api/v1/moi", "get", "200", "401", "403");
        operation(contrat, "/api/v1/espaces", "get", "200", "401");
        operation(contrat, "/api/v1/espaces", "post", "201", "400", "401");
        operation(contrat, "/api/v1/espaces/{espaceId}", "get", "200", "401", "404");
        operation(contrat, "/api/v1/espaces/{espaceId}/membres", "get", "200", "401", "404");
        operation(contrat, "/api/v1/espaces/{espaceId}/membres/{compteId}/role", "put", "200", "401", "403", "404", "409");
        operation(contrat, "/api/v1/espaces/{espaceId}/membres/{compteId}", "delete", "204", "401", "403", "404", "409");
        operation(contrat, "/api/v1/espaces/{espaceId}/invitations", "post", "201", "400", "401", "403", "404");
        operation(contrat, "/api/v1/espaces/{espaceId}/invitations", "get", "200", "401", "403", "404");
        operation(contrat, "/api/v1/espaces/{espaceId}/invitations/{invitationId}", "delete", "204", "401", "403",
                "404", "409");
        operation(contrat, "/api/v1/invitations", "get", "200", "401");
        operation(contrat, "/api/v1/invitations/{invitationId}/acceptation", "post", "200", "401", "404", "409");

        contrat.body("components.schemas", hasKey("Role"))
                .body("components.schemas", hasKey("EspaceDeCompte"))
                .body("components.schemas", hasKey("Membre"))
                .body("components.schemas", hasKey("Invitation"))
                .body("components.securitySchemes", notNullValue());
    }

    private static void operation(ValidatableResponse contrat, String chemin, String methode, String... codes) {
        String base = "paths.'" + chemin + "'." + methode;
        contrat.body(base, notNullValue());
        for (String code : codes) {
            contrat.body(base + ".responses", hasKey(code));
        }
    }
}
