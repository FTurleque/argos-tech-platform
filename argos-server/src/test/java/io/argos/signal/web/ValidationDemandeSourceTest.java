package io.argos.signal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.commun.DonneeInvalide;
import io.argos.signal.web.SourcesWeb.Demande;

/**
 * Demande de source de veille web (WEB-01) : portée, espace, type et libellé contrôlés et normalisés avant toute
 * écriture ({@link SourcesWeb#valider}).
 */
class ValidationDemandeSourceTest {

    private static final UUID ESPACE = UUID.fromString("0e2e0000-0000-4000-8000-000000000001");
    private static final String ADRESSE = "https://veille.exemple.fr/flux.xml";

    private static void refusSur(String attribut, Demande demande) {
        DonneeInvalide refus = assertThrows(DonneeInvalide.class, () -> SourcesWeb.valider(demande));
        assertEquals("DONNEE_INVALIDE", refus.code());
        assertEquals(attribut, refus.attribut());
    }

    @Test
    void demandeVideOuPorteeInconnueRefusee() {
        refusSur("adresse", null);
        refusSur("portee", new Demande("PUBLIQUE", null, null, ADRESSE, null));
        refusSur("portee", new Demande(null, null, null, ADRESSE, null));
    }

    @Test
    void sourceDEspaceAvecSonEspaceSourcePersonnelleSans() {
        refusSur("espaceId", new Demande(SourceWeb.ESPACE, null, null, ADRESSE, null));
        refusSur("espaceId", new Demande(SourceWeb.PERSONNELLE, ESPACE, null, ADRESSE, null));
        assertEquals(ESPACE, SourcesWeb.valider(new Demande(" espace ", ESPACE, null, ADRESSE, null)).espaceId());
    }

    @Test
    void typeFluxParDefautPageAdmiseAutreRefuse() {
        assertEquals(SourceWeb.FLUX, SourcesWeb.valider(new Demande("personnelle", null, null, ADRESSE, null)).type());
        assertEquals(SourceWeb.PAGE, SourcesWeb.valider(new Demande("PERSONNELLE", null, " page ", ADRESSE, null)).type());
        refusSur("type", new Demande(SourceWeb.PERSONNELLE, null, "API", ADRESSE, null));
    }

    @Test
    void libelleRogneOuAbsent() {
        Demande validee = SourcesWeb.valider(new Demande(SourceWeb.PERSONNELLE, null, null, ADRESSE, "  Veille RH  "));
        assertEquals("Veille RH", validee.libelle());
        assertEquals(SourceWeb.PERSONNELLE, validee.portee());
        assertNull(SourcesWeb.valider(new Demande(SourceWeb.PERSONNELLE, null, null, ADRESSE, "   ")).libelle());
    }
}
