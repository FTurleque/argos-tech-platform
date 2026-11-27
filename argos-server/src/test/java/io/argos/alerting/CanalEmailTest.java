package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.argos.aigateway.ReponseIa;
import io.argos.impact.MatchRules;

/** Sujet et corps texte de l'e-mail d'une alerte (ALR-03) : nature, gravité, projets, sources, résumé IA. */
class CanalEmailTest {

    private static final UUID SIGNAL = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PROJET = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String LIEN = "https://argos.example/#/signaux/" + SIGNAL;

    private static ContenuAlerte.Element impact(String gravite) {
        return new ContenuAlerte.Element(SIGNAL, "Faille dans lib", gravite,
                List.of(ContenuAlerte.Projet.impact(PROJET, "Portail", MatchRules.Level.EXACT)),
                List.of(new ContenuAlerte.Source("OSV", "GHSA-1", "https://osv.dev/GHSA-1"), new ContenuAlerte.Source("CERT-FR",
                        "CERTFR-2026-AVI-1", null)), LIEN);
    }

    private static MessageAlerte message(String nature, String titre, String gravite, ContenuAlerte contenu) {
        return new MessageAlerte(UUID.randomUUID(), nature, UUID.randomUUID(), "alice@argos.test", "EMAIL", titre, gravite,
                contenu);
    }

    private static ContenuAlerte contenu(String resumeIa, ContenuAlerte.Element... elements) {
        return new ContenuAlerte(List.of(elements), resumeIa, resumeIa == null ? "INDISPONIBLE" : "DISPONIBLE");
    }

    @Test
    void sujetSelonLaNature() {
        ContenuAlerte vide = contenu(null);
        assertEquals("[Argos] Alerte HIGH : Faille", CanalEmail.sujet(message("IMMEDIATE", "Faille", "HIGH", vide)));
        assertEquals("[Argos] Alerte : Faille", CanalEmail.sujet(message("IMMEDIATE", "Faille", null, vide)));
        assertEquals("[Argos] Résumé quotidien : 3 éléments", CanalEmail.sujet(message("DAILY", "3 éléments", null, vide)));
        assertEquals("[Argos] Résumé hebdomadaire : 3 éléments", CanalEmail.sujet(message("WEEKLY", "3 éléments", null, vide)));
        assertEquals("[Argos] Rappel d'échéance : Angular 17", CanalEmail.sujet(message("RAPPEL", "Angular 17", null, vide)));
        assertEquals("[Argos] Standard modifié : Java", CanalEmail.sujet(message("ANNONCE", "Java", null, vide)));
    }

    /** Le titre vient d'une source externe : aucun retour à la ligne n'atteint l'en-tête du message. */
    @Test
    void sujetSansRetourALaLigne() {
        String sujet = CanalEmail.sujet(message("IMMEDIATE", "Titre\r\nBcc: pirate@exemple.test\tfin  ", "LOW", contenu(null)));
        assertEquals("[Argos] Alerte LOW : Titre Bcc: pirate@exemple.test fin", sujet);
        assertFalse(sujet.contains("\n") || sujet.contains("\r") || sujet.contains("\t"));
    }

    @Test
    void corpsDUneAlerteImmediateAvecResumeIa() {
        String corps = CanalEmail.corps(message("IMMEDIATE", "Faille", "HIGH",
                contenu("Mettre à jour lib.", impact("HIGH"))));

        assertTrue(corps.startsWith("Résumé (" + ReponseIa.MENTION + ", à vérifier sur les sources) :\nMettre à jour lib.\n\n"),
                corps);
        assertTrue(corps.contains("• Faille dans lib\n  Gravité : HIGH\n  Projets : Portail (EXACT)\n"), corps);
        assertTrue(corps.contains("  Sources : OSV GHSA-1 <https://osv.dev/GHSA-1>, CERT-FR CERTFR-2026-AVI-1\n"), corps);
        assertTrue(corps.endsWith("  Voir dans Argos : " + LIEN + "\n\n"), corps);
    }

    @Test
    void corpsDUneAlerteImmediateSansResumeIa() {
        String corps = CanalEmail.corps(message("IMMEDIATE", "Faille", null, contenu(null, impact(null))));
        assertTrue(corps.startsWith("Résumé IA non disponible.\n\n"), corps);
        assertTrue(corps.contains("  Gravité : non précisée\n"), corps);
    }

    /** Un résumé périodique ne porte pas de résumé IA ; chaque élément est listé. */
    @Test
    void corpsDUnResumeSansMentionIa() {
        String corps = CanalEmail.corps(message("DAILY", "2 éléments", null, contenu(null, impact("HIGH"), impact("LOW"))));
        assertFalse(corps.contains("Résumé"), corps);
        assertEquals(2, corps.split("• ", -1).length - 1, corps);
    }

    /** Rappel du radar : projets cités avec leur version, gravité omise quand elle est absente. */
    @Test
    void corpsDUnRappelDuRadar() {
        ContenuAlerte.Element rappel = new ContenuAlerte.Element(SIGNAL, "Angular 17 : fin de support", null,
                List.of(ContenuAlerte.Projet.radar(PROJET, "Portail", "version 17")), List.of(), LIEN);
        String corps = CanalEmail.corps(message("RAPPEL", "Angular 17", null, contenu(null, rappel)));

        assertEquals("• Angular 17 : fin de support\n  Projets : Portail (version 17)\n  Sources : \n  Voir dans Argos : "
                + LIEN + "\n\n", corps);
    }
}
