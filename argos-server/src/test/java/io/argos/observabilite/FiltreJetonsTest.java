package io.argos.observabilite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

/** Masquage des jetons JWT dans les messages de journal (EXP-13-S4). */
class FiltreJetonsTest {

    private static final String JETON = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.c2lnbmF0dXJlLWR1LWpldG9u";

    @Test
    void leJetonCiteDansUnMessageEstMasque() {
        ExtLogRecord evenement = new ExtLogRecord(Level.WARNING, "jeton refusé : %s", ExtLogRecord.FormatStyle.PRINTF,
                FiltreJetonsTest.class.getName());
        evenement.setParameters(new Object[] {JETON});

        assertTrue(new FiltreJetons().isLoggable(evenement), "aucun événement retiré");
        assertEquals("jeton refusé : " + FiltreJetons.MASQUE, evenement.getFormattedMessage());
    }

    @Test
    void unMessageSansJetonEstInchange() {
        assertEquals("synchronisation de la source OSV terminée",
                FiltreJetons.masquer("synchronisation de la source OSV terminée"));
        assertEquals("version 1.2.3", FiltreJetons.masquer("version 1.2.3"));
    }
}
