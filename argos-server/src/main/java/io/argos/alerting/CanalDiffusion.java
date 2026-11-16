package io.argos.alerting;

/**
 * Interface d'émission d'une alerte ou d'un résumé sur un canal (ALR-06). Le module en fournit
 * l'implémentation {@code EMAIL} ({@link CanalEmail}) ; le changement {@code notifier-sur-le-poste}
 * peut fournir une implémentation {@code DESKTOP} en poussée. Sans implémentation pour un canal, ses
 * messages restent {@code EN_ATTENTE} jusqu'à leur remise, confirmée par le client desktop
 * ({@code POST /api/v1/alertes/{id}/remise}).
 *
 * <p>{@link EnvoiAlertes} appelle {@link #diffuser(MessageAlerte)}, retente en cas d'échec avec un
 * délai croissant, puis marque le message {@code EN_ECHEC} au-delà du nombre maximal de tentatives.
 */
public interface CanalDiffusion {

    /** Code du canal, tel qu'il figure dans les abonnements : {@code EMAIL}, {@code DESKTOP}. */
    String code();

    /**
     * Diffuse le message ; lève {@link EchecDiffusion} en cas d'échec. Un échec définitif (adresse
     * absente, par exemple) n'est pas retenté.
     */
    void diffuser(MessageAlerte message) throws EchecDiffusion;

    /** Échec de diffusion ; {@code definitif} : inutile de retenter. */
    class EchecDiffusion extends Exception {

        private final boolean definitif;

        public EchecDiffusion(String message, boolean definitif, Throwable cause) {
            super(message, cause);
            this.definitif = definitif;
        }

        public boolean definitif() {
            return definitif;
        }
    }
}
