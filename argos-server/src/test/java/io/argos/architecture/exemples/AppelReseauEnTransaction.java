package io.argos.architecture.exemples;

import jakarta.transaction.Transactional;

import io.argos.vulnintel.EnrichmentSource;
import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * Appels à une source d'enrichissement dans une transaction, contre lesquels {@code AppelsReseauHorsTransactionTest}
 * vérifie que sa règle les refuse : méthode transactionnelle, transaction ouverte autour d'un appelant. Jamais exécutée.
 */
public final class AppelReseauEnTransaction {

    private final EnrichmentSource source;

    public AppelReseauEnTransaction(EnrichmentSource source) {
        this.source = source;
    }

    @Transactional
    public int consulterEnTransaction() {
        return source.lookup("CVE-2026-0001").size();
    }

    public void consulterDansUneTransactionOuverte() {
        QuarkusTransaction.requiringNew().run(this::consulter);
    }

    private void consulter() {
        source.lookup("CVE-2026-0002");
    }
}
