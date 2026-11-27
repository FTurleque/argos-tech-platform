package io.argos.vulnintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/**
 * Appels HTTP des sources d'enrichissement (COL-04) face à des réponses limites servies par un serveur simulé :
 * 404, erreur HTTP, JSON illisible, source injoignable.
 */
class EnrichmentHttpTest {

    /** Délai d'attente des appels, large devant un serveur local. */
    static final Duration DELAI = Duration.ofSeconds(5);
    /** Taille maximale lue d'une réponse. */
    static final long TAILLE_MAX = 1_048_576;

    private static final int PASSERELLE_EN_ERREUR = 502;
    /** Port réservé où aucun serveur n'écoute : connexion refusée. */
    private static final String ADRESSE_INJOIGNABLE = "http://localhost:1/injoignable";

    private ServeurHttpSimule serveur;
    private EnrichmentHttp http;

    @BeforeEach
    void demarrer() {
        serveur = ServeurHttpSimule.demarrer();
        http = new EnrichmentHttp(DELAI, TAILLE_MAX);
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    @Test
    void reponse404ValantAbsenceEtAutreStatutIndisponibilite() {
        assertEquals(Optional.empty(), http.get(URI.create(serveur.base() + "/absent")));

        serveur.programmer("/panne", Stub.status(PASSERELLE_EN_ERREUR));
        EnrichmentUnavailableException panne = assertThrows(EnrichmentUnavailableException.class,
                () -> http.get(URI.create(serveur.base() + "/panne")));
        assertTrue(panne.getMessage().startsWith("HTTP 502"), panne.getMessage());
    }

    @Test
    void jsonIllisibleEtSourceInjoignableRendentLaSourceIndisponible() {
        serveur.programmer("/illisible", Stub.succes("{pas du json"));
        assertTrue(assertThrows(EnrichmentUnavailableException.class,
                () -> http.getJson(URI.create(serveur.base() + "/illisible"))).getMessage().startsWith("réponse illisible"));
        assertTrue(assertThrows(EnrichmentUnavailableException.class,
                () -> http.get(URI.create(ADRESSE_INJOIGNABLE))).getMessage().startsWith("erreur réseau"));
    }
}
