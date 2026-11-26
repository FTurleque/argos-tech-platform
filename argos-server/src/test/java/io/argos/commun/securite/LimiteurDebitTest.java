package io.argos.commun.securite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Limiteur de débit commun au serveur MCP (MCP-06) et à la veille web : fenêtre glissante par compte. */
class LimiteurDebitTest {

    /** Fenêtre glissante : un appel redevient possible une minute après le plus ancien. */
    @Test
    void fenetreGlissante() {
        long[] maintenant = {1_000_000L};
        LimiteurDebit limiteur = new LimiteurDebit(2, () -> maintenant[0]);
        UUID compte = UUID.randomUUID();
        assertTrue(limiteur.admettre(compte));
        maintenant[0] += 30_000;
        assertTrue(limiteur.admettre(compte));
        assertFalse(limiteur.admettre(compte));
        maintenant[0] += 30_001;
        assertTrue(limiteur.admettre(compte));
        assertFalse(limiteur.admettre(compte));
        assertTrue(limiteur.admettre(UUID.randomUUID()));
    }

    /** Les fenêtres échues sont retirées : la mémoire du limiteur ne croît pas avec les comptes inactifs. */
    @Test
    void fenetresEchuesRetirees() {
        long[] maintenant = {0};
        LimiteurDebit limiteur = new LimiteurDebit(60, () -> maintenant[0]);
        for (int tentative = 0; tentative < 255; tentative++) {
            limiteur.admettre(UUID.randomUUID());
        }
        assertEquals(255, limiteur.comptesSuivis());
        maintenant[0] = 61_000;
        limiteur.admettre(UUID.randomUUID());
        assertEquals(1, limiteur.comptesSuivis());
    }

    @Test
    void limiteNulleRefusee() {
        assertThrows(IllegalArgumentException.class, () -> new LimiteurDebit(0));
    }
}
