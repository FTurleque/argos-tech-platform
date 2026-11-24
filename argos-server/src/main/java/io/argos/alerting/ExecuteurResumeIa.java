package io.argos.alerting;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.execution.ExecuteurBorne;

/**
 * Exécuteur des demandes de résumé IA des alertes (AUD-QUA-11) : hors du pool commun, deux fils et une file de seize
 * demandes par défaut ({@code argos.alerting.resume-ia.fils}, {@code .fils-max}, {@code .file}) ; une file pleine vaut
 * résumé indisponible.
 */
@ApplicationScoped
public class ExecuteurResumeIa extends ExecuteurBorne {

    public ExecuteurResumeIa() {
        super("argos-resume-ia", "argos.alerting.resume-ia");
    }
}
