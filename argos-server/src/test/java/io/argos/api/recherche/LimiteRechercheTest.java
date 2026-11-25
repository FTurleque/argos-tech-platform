package io.argos.api.recherche;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.commun.securite.LimiteDebitAtteinte;
import io.argos.commun.securite.LimiteurDebit;

/** RCH-06-S3 : la limite de la recherche se relâche quand la fenêtre d'une minute s'écoule (horloge injectée). */
class LimiteRechercheTest {

    private static final int LIMITE = 2;
    private static final long UNE_MINUTE_MS = 60_000L;
    private static final long DEBUT_MS = 1_000_000L;

    @Test
    void rch06S3FenetreEcoulee() {
        long[] maintenant = {DEBUT_MS};
        LimiteRecherche limite = new LimiteRecherche();
        limite.limiteur = new LimiteurDebit(LIMITE, () -> maintenant[0]);
        UUID compte = UUID.randomUUID();

        assertDoesNotThrow(() -> limite.admettre(compte));
        assertDoesNotThrow(() -> limite.admettre(compte));
        assertThrows(LimiteDebitAtteinte.class, () -> limite.admettre(compte));

        maintenant[0] += UNE_MINUTE_MS + 1;
        assertDoesNotThrow(() -> limite.admettre(compte), "la fenêtre écoulée libère le compte");
    }
}
