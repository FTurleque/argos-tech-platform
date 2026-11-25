package io.argos.commun.securite;

/**
 * Requête refusée parce que le compte a atteint sa limite de débit ({@link LimiteurDebit}). La
 * couche d'exposition journalise le refus (motif {@code LIMITE_DEBIT}, ACC-06) et répond 429.
 */
public class LimiteDebitAtteinte extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LimiteDebitAtteinte() {
        super("Nombre maximal de requêtes par minute atteint, réessayez plus tard.");
    }
}
