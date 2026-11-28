package io.argos.workspace.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Contrôle d'une demande d'abonnement avant tout enregistrement (ABO-01, ABO-02) : chaque attribut manquant ou invalide
 * est refusé et nommé, dans l'ordre de la spécification. Le refus précède l'écriture : aucun abonnement n'est créé.
 */
class SubscriptionServiceTest {

    private static final List<SubscriptionRequest.Filter> RUST = List.of(new SubscriptionRequest.Filter("TECHNOLOGY", "rust"));

    private final UUID alice = UUID.randomUUID();

    private static String attributRefuse(SubscriptionRequest demande) {
        return assertThrows(SubscriptionRejectedException.class, () -> SubscriptionService.validate(demande)).attribute();
    }

    /** ABO-01-S1 : un abonnement sans portée est refusé. */
    @Test
    void abo01S1AbonnementSansPorteeRefuse() {
        assertEquals("scope", attributRefuse(new SubscriptionRequest(null, null, alice, RUST, "in_app", "daily", false)));
        assertEquals("scope", attributRefuse(null));
    }

    /** ABO-02-S2 : filtres vides, canal absent ou caractère obligatoire absent : l'attribut manquant est nommé. */
    @Test
    void abo02S2AttributManquant() {
        assertEquals("filters", attributRefuse(new SubscriptionRequest("PERSONAL", null, alice, List.of(), "in_app", "daily", false)));
        assertEquals("channel", attributRefuse(new SubscriptionRequest("PERSONAL", null, alice, RUST, null, "daily", false)));
        assertEquals("mandatory", attributRefuse(new SubscriptionRequest("PERSONAL", null, alice, RUST, "x", "daily", null)));
    }

    /** ALR-01 : une gravité minimale inconnue est refusée dès la création de la règle. */
    @Test
    void graviteMinimaleInconnueRefuseeALaCreationDeLaRegle() {
        assertEquals("filters", attributRefuse(new SubscriptionRequest("PERSONAL", null, alice,
                List.of(new SubscriptionRequest.Filter("SEVERITY", "URGENTE")), "email", "immediate", false)));
    }
}
