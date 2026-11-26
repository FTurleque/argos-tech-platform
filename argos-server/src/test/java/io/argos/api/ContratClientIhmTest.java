package io.argos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;

/**
 * Contrat entre l'API et l'IHM (AUD-ARC-12 ; décision du porteur du projet, 2026-11-26) : l'instantané
 * {@code argos-ui/src/api/openapi.json}, sur lequel l'IHM vérifie chacun de ses appels
 * ({@code argos-ui/tests/contrat-api.test.mjs}), est identique au document servi par {@code /q/openapi}. Toute
 * évolution de l'API fait échouer ce test tant que l'instantané n'est pas régénéré :
 * {@code mvn test -Dtest=ContratClientIhmTest -Dargos.contrat.regenerer=true}.
 */
@QuarkusTest
class ContratClientIhmTest {

    /** Instantané du contrat, versionné avec l'IHM. */
    static final Path INSTANTANE = Path.of("..", "argos-ui", "src", "api", "openapi.json");

    /** Propriété système qui réécrit l'instantané au lieu de le comparer. */
    static final String REGENERER = "argos.contrat.regenerer";

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Adresse de découverte OIDC, propre à l'environnement (port du Keycloak de test) : hors du contrat. */
    private static final Pattern DECOUVERTE_OIDC = Pattern.compile("\"openIdConnectUrl\" : \"[^\"]*\"");

    private static final String DECOUVERTE_NORMALISEE = "\"openIdConnectUrl\" : \"<émetteur>/.well-known/openid-configuration\"";

    /**
     * Document JSON aux clés triées, indenté, terminé par un saut de ligne, adresse de découverte OIDC remplacée :
     * comparable ligne à ligne.
     */
    static String normaliser(String document) throws IOException {
        Object arbre = JSON.readValue(document, Object.class);
        String trie = JSON.writeValueAsString(arbre).replace("\r\n", "\n") + "\n";
        return DECOUVERTE_OIDC.matcher(trie).replaceAll(Matcher.quoteReplacement(DECOUVERTE_NORMALISEE));
    }

    @Test
    void lInstantaneDeLIhmEstLeContratServi() throws IOException {
        String servi = normaliser(Api.enTantQue("alice").accept("application/json").when().get("/q/openapi?format=json")
                .then().statusCode(200).extract().asString());
        if (Boolean.getBoolean(REGENERER)) {
            Files.writeString(INSTANTANE, servi, StandardCharsets.UTF_8);
        }
        String versionne = Files.exists(INSTANTANE) ? Files.readString(INSTANTANE, StandardCharsets.UTF_8) : "";
        assertEquals(servi, versionne, "contrat de l'API modifié : régénérer l'instantané de l'IHM (-D" + REGENERER
                + "=true) et vérifier les appels du client");
    }
}
