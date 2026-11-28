package io.argos.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** ACC-12-S3 : origine de l'émetteur OIDC nommée par la politique de contenu, sans chemin ni port par défaut. */
class EnTetesSecuriteUnitaireTest {

    @Test
    void origineSansCheminNiPortParDefaut() {
        assertEquals("https://auth.exemple.fr", EnTetesSecurite.origine("https://auth.exemple.fr/realms/argos"));
        assertEquals("http://nas:8180", EnTetesSecurite.origine("http://nas:8180/realms/argos"));
        assertEquals("http://192.168.1.20:8180", EnTetesSecurite.origine("http://192.168.1.20:8180"));
    }

    @Test
    void emetteurRelatifOuIllisibleRefuse() {
        assertThrows(IllegalArgumentException.class, () -> EnTetesSecurite.origine("/realms/argos"));
        assertThrows(IllegalArgumentException.class, () -> EnTetesSecurite.origine("realms/argos"));
        assertThrows(IllegalArgumentException.class, () -> EnTetesSecurite.origine("http://exemple.fr:port/x"));
    }
}
