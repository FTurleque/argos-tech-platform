package io.argos.observabilite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void leJetonCiteDansLExceptionEtSaCauseEstMasque() {
        ExtLogRecord evenement = new ExtLogRecord(Level.SEVERE, "erreur", ExtLogRecord.FormatStyle.NO_FORMAT,
                FiltreJetonsTest.class.getName());
        IllegalStateException erreur = new IllegalStateException("jeton refusé " + JETON,
                new IllegalArgumentException("cause citant " + JETON));
        evenement.setThrown(erreur);

        new FiltreJetons().isLoggable(evenement);

        Throwable masquee = evenement.getThrown();
        assertTrue(masquee.getMessage().contains(IllegalStateException.class.getName()), masquee.getMessage());
        assertFalse(masquee.getMessage().contains(JETON));
        assertFalse(masquee.getCause().getMessage().contains(JETON));
        assertArrayEquals(erreur.getStackTrace(), masquee.getStackTrace(), "pile conservée");
    }

    @Test
    void uneExceptionSansJetonEstConservee() {
        IllegalStateException erreur = new IllegalStateException("panne d'accès aux données");
        assertSame(erreur, FiltreJetons.masquerPile(erreur));
    }

    @Test
    void unMessageSansJetonEstInchange() {
        assertEquals("synchronisation de la source OSV terminée",
                FiltreJetons.masquer("synchronisation de la source OSV terminée"));
        assertEquals("version 1.2.3", FiltreJetons.masquer("version 1.2.3"));
    }
}
