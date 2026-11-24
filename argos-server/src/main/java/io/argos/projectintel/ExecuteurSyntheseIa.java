package io.argos.projectintel;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.execution.ExecuteurBorne;

/**
 * Exécuteur des demandes de synthèse IA de l'activité des projets (AUD-QUA-11) : un fil permanent, deux au plus et une
 * file de huit demandes par défaut ({@code argos.activite.synthese.fils}, {@code .fils-max}, {@code .file}).
 */
@ApplicationScoped
public class ExecuteurSyntheseIa extends ExecuteurBorne {

    public ExecuteurSyntheseIa() {
        super("argos-synthese-activite", "argos.activite.synthese");
    }
}
