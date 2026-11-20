package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.workspace.subscription.SubscriptionRequest.Filter;

/** Correspondance d'une règle d'alerte avec un impact (tâche 2.2), sans base de données. */
class RegleAlerteTest {

    private static final String PAQUET = "pkg:maven/org.exemple/lib";

    private static RegleAlerte regle(String canal, String frequence, Filter... filtres) {
        return RegleAlerte.depuis(canal, frequence, List.of(filtres)).orElseThrow();
    }

    @Test
    void canauxEtFrequencesDiffuses() {
        assertTrue(RegleAlerte.depuis("IN_APP", "DAILY", List.of(new Filter("SEVERITY", "HIGH"))).isEmpty(),
                "aucun canal de messagerie d'équipe ni canal inconnu (Q-2)");
        assertTrue(RegleAlerte.depuis("EMAIL", "MONTHLY", List.of(new Filter("SEVERITY", "HIGH"))).isEmpty());
        assertEquals("DAILY", regle("email", "immediate", new Filter("SEVERITY", "HIGH")).frequenceResume());
        assertEquals("WEEKLY", regle("DESKTOP", "WEEKLY", new Filter("SEVERITY", "HIGH")).frequenceResume());
    }

    @Test
    void graviteMinimale() {
        RegleAlerte regle = regle("EMAIL", "IMMEDIATE", new Filter("SEVERITY", "HIGH"));
        assertTrue(regle.seuilAtteint("CRITICAL"));
        assertTrue(regle.seuilAtteint("high"));
        assertFalse(regle.seuilAtteint("MODERATE"));
        assertFalse(regle.seuilAtteint(null), "gravité inconnue : seuil non atteint");
        assertFalse(regle.seuilAtteint("CVSS_V3:CVSS:3.1/AV:N/AC:L"), "vecteur non gradué");
        assertTrue(regle("EMAIL", "IMMEDIATE", new Filter("PROJECT", "x")).seuilAtteint(null), "sans minimum");
    }

    @Test
    void criteresCumulatifsEntreTypesEtAlternatifsDansUnType() {
        UUID projet = UUID.randomUUID();
        RegleAlerte regle = regle("EMAIL", "IMMEDIATE", new Filter("PROJECT", projet.toString()),
                new Filter("PROJECT", UUID.randomUUID().toString()), new Filter("TECHNOLOGY", "java"));
        assertTrue(regle.concerne(projet.toString(), PAQUET, "OSV"));
        assertFalse(regle.concerne(UUID.randomUUID().toString(), PAQUET, "OSV"), "autre projet");
        assertFalse(regle.concerne(projet.toString(), "pkg:npm/lodash", "OSV"), "autre technologie");

        RegleAlerte paquet = regle("EMAIL", "DAILY", new Filter("PACKAGE", PAQUET + "@1.2.0"), new Filter("SOURCE", "osv"));
        assertTrue(paquet.concerne(projet.toString(), PAQUET, "OSV"));
        assertFalse(paquet.concerne(projet.toString(), PAQUET, "CERTFR"));
    }

    @Test
    void criteresDActualiteSansEffetSurLesImpacts() {
        assertFalse(regle("EMAIL", "IMMEDIATE", new Filter("THEME", "IA")).concerne(UUID.randomUUID().toString(), PAQUET, "OSV"));
        assertTrue(regle("EMAIL", "IMMEDIATE", new Filter("THEME", "IA"), new Filter("SEVERITY", "LOW"))
                .concerne(UUID.randomUUID().toString(), PAQUET, "OSV"));
    }

    @Test
    void delaiDesNouvellesTentativesCroissantEtPlafonne() {
        EnvoiAlertes envoi = new EnvoiAlertes();
        envoi.delaiInitial = Duration.ofMinutes(1);
        envoi.delaiMax = Duration.ofMinutes(30);
        assertEquals(Duration.ofMinutes(1), envoi.delai(1));
        assertEquals(Duration.ofMinutes(2), envoi.delai(2));
        assertEquals(Duration.ofMinutes(16), envoi.delai(5));
        assertEquals(Duration.ofMinutes(30), envoi.delai(9));
    }

    @Test
    void sujetSansRetourALaLigneVenuDuTitreExterne() {
        MessageAlerte message = new MessageAlerte(UUID.randomUUID(), "IMMEDIATE", UUID.randomUUID(), "a@argos.test", "EMAIL",
                "Faille\r\nBcc: intrus@exemple.test", "CRITICAL", new ContenuAlerte(List.of(), null, "SANS_OBJET"));
        assertEquals("[Argos] Alerte CRITICAL : Faille Bcc: intrus@exemple.test", CanalEmail.sujet(message));
    }
}
