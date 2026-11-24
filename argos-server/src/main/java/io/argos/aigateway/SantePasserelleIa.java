package io.argos.aigateway;

import java.util.Locale;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Contrôle de disponibilité {@value #NOM} (EXP-15-S3, design.md DC-2) : passerelle désactivée, ou fournisseur
 * joignable, injoignable ou pas encore appelé, d'après le dernier appel transmis (aucun appel réseau pendant le
 * contrôle). Le contrôle reste {@code UP} : sans IA, Argos fonctionne en mode déterministe (ADR-0029).
 */
@Readiness
@ApplicationScoped
public class SantePasserelleIa implements HealthCheck {

    /** Nom du contrôle de disponibilité. */
    public static final String NOM = "passerelle-ia";

    @Inject
    PasserelleIa passerelle;

    @Override
    public HealthCheckResponse call() {
        if (!passerelle.active()) {
            return HealthCheckResponse.named(NOM).up().withData("etat", "desactivee").build();
        }
        PasserelleIa.EtatFournisseur fournisseur = passerelle.etatFournisseur();
        return HealthCheckResponse.named(NOM).up()
                .withData("etat", fournisseur == PasserelleIa.EtatFournisseur.INJOIGNABLE ? "degrade" : "nominal")
                .withData("fournisseur", fournisseur.name().toLowerCase(Locale.ROOT))
                .build();
    }
}
