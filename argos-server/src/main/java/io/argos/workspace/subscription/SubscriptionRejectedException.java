package io.argos.workspace.subscription;

/** Demande refusée ; {@code attribute} nomme l'attribut manquant ou invalide (ABO-01-S1, ABO-02-S2). */
public class SubscriptionRejectedException extends RuntimeException {

    private final String attribute;

    public SubscriptionRejectedException(String attribute, String message) {
        super(message);
        this.attribute = attribute;
    }

    public String attribute() {
        return attribute;
    }
}
