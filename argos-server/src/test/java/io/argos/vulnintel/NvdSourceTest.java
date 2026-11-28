package io.argos.vulnintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.argos.testsupport.Fixtures;
import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/** Source NVD (COL-04) : CVE inconnue, choix de la métrique CVSS et de la description, source en panne. */
class NvdSourceTest {

    private static final String CVE = "CVE-2026-1234";
    private static final String NVD = "/nvd?cveId=" + CVE;
    private static final int SERVICE_INDISPONIBLE = 503;
    private static final double SCORE_CVSS2 = 7.5;
    private static final double SCORE_LOG4SHELL = 10.0;

    private ServeurHttpSimule serveur;
    private NvdSource nvd;

    @BeforeEach
    void demarrer() {
        serveur = ServeurHttpSimule.demarrer();
        nvd = new NvdSource();
        nvd.http = new EnrichmentHttp(EnrichmentHttpTest.DELAI, EnrichmentHttpTest.TAILLE_MAX);
        nvd.url = serveur.base() + "/nvd";
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    @Test
    void nvdSansVulnerabiliteOuInconnueNeDonneRien() {
        assertEquals(List.of(), nvd.lookup(CVE), "404 : CVE inconnue");
        serveur.programmer(NVD, Stub.succes("{\"vulnerabilities\": []}"));
        assertEquals(List.of(), nvd.lookup(CVE));
        serveur.programmer(NVD, Stub.succes("{\"vulnerabilities\": {}}"));
        assertEquals(List.of(), nvd.lookup(CVE));
    }

    @Test
    void nvdRetientLaPremiereMetriqueDisponibleEtLaDescriptionAnglaise() {
        serveur.programmer(NVD, Stub.succes("""
                {"vulnerabilities": [{"cve": {"published": "2026-01-02T00:00:00", "lastModified": "2026-01-03T00:00:00",
                  "descriptions": [{"lang": "es", "value": "descripción"}, {"lang": "en", "value": "description"}],
                  "metrics": {"cvssMetricV40": [], "cvssMetricV2": [{"baseSeverity": "HIGH",
                    "cvssData": {"version": "2.0", "baseScore": 7.5}}]}}}]}
                """));

        List<EnrichmentSource.Finding> resultats = nvd.lookup(CVE);

        assertEquals(1, resultats.size());
        JsonNode donnees = resultats.get(0).data();
        assertEquals("description", donnees.path("description").asText());
        assertEquals("2.0", donnees.path("cvssVersion").asText());
        assertEquals(SCORE_CVSS2, donnees.path("baseScore").asDouble());
        assertEquals("HIGH", donnees.path("baseSeverity").asText(), "gravité lue dans la métrique quand cvssData n'en porte pas");
        assertEquals("NVD", nvd.id());
    }

    @Test
    void nvdSansMetriqueNiDescriptionGardeLesDates() {
        serveur.programmer(NVD, Stub.succes("{\"vulnerabilities\": [{\"cve\": {\"published\": \"2026-01-02T00:00:00\"}}]}"));

        JsonNode donnees = nvd.lookup(CVE).get(0).data();

        assertEquals("2026-01-02T00:00:00", donnees.path("published").asText());
        assertFalse(donnees.has("baseScore"));
        assertFalse(donnees.has("description"));
    }

    /** COL-04-S2 sur la réponse enregistrée de NVD : score CVSS de Log4Shell retenu. */
    @Test
    void col04S2ReponseNvdEnregistreeLue() {
        serveur.programmer("/nvd?cveId=CVE-2021-44228", Stub.succes(Fixtures.resource("enrichment/nvd-CVE-2021-44228.json")));

        List<EnrichmentSource.Finding> resultats = nvd.lookup("CVE-2021-44228");

        assertEquals(1, resultats.size());
        assertEquals(SCORE_LOG4SHELL, resultats.get(0).data().path("baseScore").asDouble());
    }

    @Test
    void nvdEnPanneIndisponible() {
        serveur.programmer(NVD, Stub.status(SERVICE_INDISPONIBLE));
        assertThrows(EnrichmentUnavailableException.class, () -> nvd.lookup(CVE));
    }
}
