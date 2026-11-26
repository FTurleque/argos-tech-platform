package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Format des liens des alertes vers les vues de l'IHM (ALR-09-S1, ALR-09-S2). */
class LiensIhmTest {

    private static final String ADRESSE_PUBLIQUE = "https://argos.example";
    private static final UUID IDENTIFIANT = UUID.fromString("0b6f8f9e-2a43-4a59-9f0c-5d3c2f1e7a10");

    @Test
    void alr09S1LienDUnSignal() {
        assertEquals("https://argos.example/#/signaux/" + IDENTIFIANT,
                LiensIhm.lien(ADRESSE_PUBLIQUE, LiensIhm.SIGNAL, IDENTIFIANT));
    }

    @Test
    void alr09S2LiensDuRadarEtDesOpportunites() {
        assertEquals("https://argos.example/#/espaces/" + IDENTIFIANT + "/radar",
                LiensIhm.lien(ADRESSE_PUBLIQUE, LiensIhm.RADAR_ESPACE, IDENTIFIANT));
        assertEquals("https://argos.example/#/profils/artiste/opportunites/" + IDENTIFIANT,
                LiensIhm.lien(ADRESSE_PUBLIQUE, LiensIhm.OPPORTUNITE, IDENTIFIANT));
    }

    @Test
    void lesBarresObliquesFinalesDeLAdresseSontRetirees() {
        assertEquals("https://argos.example/#/signaux/" + IDENTIFIANT,
                LiensIhm.lien(ADRESSE_PUBLIQUE + "//", LiensIhm.SIGNAL, IDENTIFIANT));
    }
}
