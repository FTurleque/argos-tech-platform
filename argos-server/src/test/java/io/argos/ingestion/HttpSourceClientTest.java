package io.argos.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/** Client HTTP des sources de collecte : lecture bornée des réponses (AUD-PER-11). */
class HttpSourceClientTest {

    private static final Duration DELAI = Duration.ofSeconds(5);
    private static final long TAILLE_MINUSCULE = 8;
    private static final String INDEX = "/osv-export/Maven/modified_id.csv";

    @Test
    void uneReponseAuDelaDeLaTailleMaximaleEstRefuseeCommeErreurPermanente() {
        try (ServeurHttpSimule serveur = ServeurHttpSimule.demarrer()) {
            serveur.programmer(INDEX, Stub.succes("2026-11-20T00:00:00Z,GHSA-A1\n"));
            HttpSourceClient borne = new HttpSourceClient(DELAI, TAILLE_MINUSCULE);

            SourceException refus = assertThrows(SourceException.class, () -> borne.telecharger(URI.create(serveur.base() + INDEX)));

            assertEquals(SourceException.Kind.PERMANENT, refus.kind());
        }
    }
}
