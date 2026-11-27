package io.argos.vulnintel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/** Source EPSS (COL-04) : CVE inconnue, liste vide, première donnée retenue. */
class EpssSourceTest {

    private static final String CVE = "CVE-2026-1234";
    private static final String EPSS = "/epss?cve=" + CVE;
    private static final double PROBABILITE = 0.42;
    private static final double PERCENTILE = 0.97;

    private ServeurHttpSimule serveur;
    private EpssSource epss;

    @BeforeEach
    void demarrer() {
        serveur = ServeurHttpSimule.demarrer();
        epss = new EpssSource();
        epss.http = new EnrichmentHttp(EnrichmentHttpTest.DELAI, EnrichmentHttpTest.TAILLE_MAX);
        epss.url = serveur.base() + "/epss";
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    @Test
    void epssSansDonneeNeDonneRienEtAvecDonneeRetientLaPremiere() {
        assertEquals(List.of(), epss.lookup(CVE), "404 : CVE inconnue");
        serveur.programmer(EPSS, Stub.succes("{\"data\": []}"));
        assertEquals(List.of(), epss.lookup(CVE));

        serveur.programmer(EPSS, Stub.succes("""
                {"data": [{"epss": "0.42", "percentile": "0.97", "date": "2026-11-20"}, {"epss": "0.01"}]}
                """));
        JsonNode donnees = epss.lookup(CVE).get(0).data();
        assertEquals(PROBABILITE, donnees.path("epss").asDouble());
        assertEquals(PERCENTILE, donnees.path("percentile").asDouble());
        assertEquals("2026-11-20", donnees.path("date").asText());
        assertEquals("EPSS", epss.id());
    }
}
