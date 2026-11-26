package io.argos.scm;

/**
 * Accès refusé par la forge : jeton refusé (401) ou droits insuffisants (403 hors quota épuisé,
 * {@link QuotaEpuise}). Le message nomme l'hôte et le statut, jamais le jeton (QS-09).
 */
public class AccesRefuse extends ScmException {

    private static final long serialVersionUID = 1L;

    public AccesRefuse(String hote, int status) {
        super((status == 401 ? "jeton d'accès refusé par " : "accès refusé par ") + hote + " (HTTP " + status + ")", null);
    }
}
