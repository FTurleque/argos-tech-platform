package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Clé d'idempotence des rappels et des annonces du radar, bornée à la colonne {@code cle_radar}. */
class CleRadarTest {

    @Test
    void uneCleCourteEstGardeeTelleQuelle() {
        String cle = "RAPPEL|espace|pkg:maven/org.exemple/lib|FIN_DE_SUPPORT|2.1|2026-12-16|J-30";
        assertEquals(cle, RappelsRadar.cleBornee(cle));
    }

    @Test
    void deuxClesLonguesQuiNeDifferentQueParLaFinRestentDistinctes() {
        String debut = "RAPPEL|espace|pkg:generic/" + "x".repeat(600) + "|FIN_DE_SUPPORT|1.0|2026-12-16|";
        String j30 = RappelsRadar.cleBornee(debut + "J-30");
        String cleJ7 = RappelsRadar.cleBornee(debut + "J-7");
        assertEquals(RappelsRadar.CLE_MAX, j30.length());
        assertTrue(cleJ7.length() <= RappelsRadar.CLE_MAX);
        assertNotEquals(j30, cleJ7);
        assertEquals(j30, RappelsRadar.cleBornee(debut + "J-30"), "clé stable d'une exécution à l'autre");
    }
}
