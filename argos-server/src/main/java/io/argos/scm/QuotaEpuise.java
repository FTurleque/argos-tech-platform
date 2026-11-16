package io.argos.scm;

import java.time.Instant;

/** Quota d'API de la forge épuisé (PIN-05, risque R-14) : la collecte reprend après {@link #reprise()}. */
public class QuotaEpuise extends ScmException {

    private static final long serialVersionUID = 1L;

    private final transient Instant reprise;

    public QuotaEpuise(String hote, Instant reprise) {
        super("quota d'API épuisé sur " + hote + ", reprise après " + reprise, null);
        this.reprise = reprise;
    }

    /** Date de réinitialisation du quota annoncée par la forge (bornée à 24 heures). */
    public Instant reprise() {
        return reprise;
    }
}
