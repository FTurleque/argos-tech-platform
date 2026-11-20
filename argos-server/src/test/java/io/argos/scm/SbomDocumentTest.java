package io.argos.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Reconnaissance du format d'une SBOM sur son en-tête (INV-01). */
class SbomDocumentTest {

    private static SbomDocument.Format format(String json) {
        return SbomDocument.reconnaitre(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Objet {@code sbom} de plus de 64 champs : ses champs imbriqués ne sont pas lus comme des champs de premier niveau. */
    @Test
    void objetSbomTropLongNeDebordePasSurLePremierNiveau() {
        StringBuilder sbom = new StringBuilder("{\"sbom\": {");
        for (int indice = 0; indice < 70; indice++) {
            sbom.append("\"c").append(indice).append("\": 1, ");
        }
        sbom.append("\"bomFormat\": \"CycloneDX\", \"spdxVersion\": \"SPDX-2.3\"}}");
        assertEquals(SbomDocument.Format.INCONNU, format(sbom.toString()));
    }

    @Test
    void objetSbomSansSpdxPuisCycloneDxAuPremierNiveau() {
        assertEquals(SbomDocument.Format.CYCLONEDX_JSON,
                format("{\"sbom\": {\"name\": \"x\", \"items\": [1, {\"a\": 2}]}, \"bomFormat\": \"CycloneDX\"}"));
    }

    @Test
    void exportGithubReconnuCommeSpdx() {
        assertEquals(SbomDocument.Format.SPDX_JSON, format("{\"sbom\": {\"name\": \"x\", \"spdxVersion\": \"SPDX-2.3\"}}"));
    }
}
