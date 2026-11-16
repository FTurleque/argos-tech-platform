package io.argos.testsupport;

import static io.restassured.RestAssured.given;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Client MCP de test : requêtes JSON-RPC sans état (spécification MCP 2026-07-28) sur
 * {@code POST /mcp}, avec les en-têtes et les champs {@code _meta} exigés, et un jeton réel émis
 * par le Keycloak des Dev Services.
 */
public final class Mcp {

    public static final String VERSION = "2026-07-28";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger IDS = new AtomicInteger();

    private Mcp() {
    }

    /** {@code tools/list} pour l'utilisateur. */
    public static ExtractableResponse<Response> lister(String utilisateur) {
        return envoyer(Api.en(utilisateur), "tools/list", null, Map.of());
    }

    /** {@code tools/call} pour l'utilisateur. */
    public static ExtractableResponse<Response> appeler(String utilisateur, String outil, Map<String, ?> arguments) {
        return envoyer(Api.en(utilisateur), "tools/call", outil, Map.of("name", outil, "arguments", arguments));
    }

    /** {@code tools/call} sans aucun jeton. */
    public static ExtractableResponse<Response> appelerSansJeton(String outil, Map<String, ?> arguments) {
        return envoyer(given(), "tools/call", outil, Map.of("name", outil, "arguments", arguments));
    }

    /** {@code tools/call} avec un jeton porteur donné. */
    public static ExtractableResponse<Response> appelerAvecJeton(String jeton, String outil, Map<String, ?> arguments) {
        return envoyer(given().auth().oauth2(jeton), "tools/call", outil, Map.of("name", outil, "arguments", arguments));
    }

    public static ExtractableResponse<Response> envoyer(RequestSpecification requete, String methode, String nom,
            Map<String, ?> params) {
        Map<String, Object> p = new java.util.LinkedHashMap<>(params);
        p.put("_meta", Map.of("io.modelcontextprotocol/protocolVersion", VERSION,
                "io.modelcontextprotocol/clientInfo", Map.of("name", "argos-tests", "version", "1"),
                "io.modelcontextprotocol/clientCapabilities", Map.of()));
        Map<String, Object> corps = Map.of("jsonrpc", "2.0", "id", IDS.incrementAndGet(), "method", methode, "params", p);
        RequestSpecification r = requete.header("MCP-Protocol-Version", VERSION).header("Mcp-Method", methode)
                .contentType("application/json").accept("application/json, text/event-stream");
        if (nom != null) {
            r = r.header("Mcp-Name", nom);
        }
        try {
            return r.body(JSON.writeValueAsString(corps)).when().post("/mcp").then().extract();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
