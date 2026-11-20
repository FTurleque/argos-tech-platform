package io.argos.alerting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;

import io.argos.impact.ImpactsTraites;

/**
 * Aucune alerte pour un impact écarté ou résolu (ALR-07, décision J5 du porteur du projet, 2026-11-19).
 *
 * <p>Les impacts {@code ECARTE} et {@code RESOLU} ne sont jamais évalués ni cités : {@link MoteurAlertes} et
 * {@link ResumesPeriodiques} ne relisent que les impacts actifs ({@code ImpactQueries#forSignals}). Une alerte
 * immédiate déjà enregistrée et encore en attente d'envoi (canal e-mail en nouvelle tentative, alerte du poste pas
 * encore remise) dont tous les impacts visibles du destinataire ont depuis été écartés ou résolus passe
 * {@code ANNULEE} : elle n'est plus envoyée ni remise au poste, et reste consultable (ALR-07-S1). Traitement technique,
 * sous l'utilisateur de connexion, comme l'évaluation des règles.
 */
@ApplicationScoped
public class AlertesSansObjet {

    private static final Logger LOG = Logger.getLogger(AlertesSansObjet.class);

    /**
     * Condition sur l'alerte {@code a} : le destinataire voit au moins un impact du signal, et aucun n'est actif. Un
     * destinataire qui ne voit plus aucun impact (retrait d'un espace) relève des règles de visibilité existantes.
     */
    static final String SANS_OBJET = """
            a.nature = 'IMMEDIATE' AND a.etat = 'EN_ATTENTE'
            AND EXISTS (SELECT 1 FROM project_impact i JOIN appartenance ap ON ap.espace_id = i.workspace_id
                        WHERE i.signal_id = a.signal_id AND ap.compte_id = a.compte_id)
            AND NOT EXISTS (SELECT 1 FROM project_impact i JOIN appartenance ap ON ap.espace_id = i.workspace_id
                            WHERE i.signal_id = a.signal_id AND ap.compte_id = a.compte_id
                              AND i.state IN ('OUVERT', 'CONFIRME'))
            """;

    static final String ANNULER = "UPDATE alerte a SET etat = 'ANNULEE', prochain_essai = NULL, "
            + "derniere_erreur = 'impacts écartés ou résolus avant l''envoi' WHERE ";

    @Inject
    DataSource dataSource;

    void surImpactsTraites(@Observes(during = TransactionPhase.AFTER_SUCCESS) ImpactsTraites evenement) {
        annulerSansPropager(evenement.signalId());
    }

    /**
     * Observateur d'une transaction déjà validée : l'échec de l'annulation est journalisé avec sa cause et ne
     * remonte pas au traitement des impacts, qui reste acquis ; l'envoi revérifie l'objet de chaque alerte.
     */
    private void annulerSansPropager(UUID signalId) {
        try {
            annuler(signalId);
        } catch (RuntimeException erreur) {
            LOG.errorf(erreur, "annulation des alertes sans objet du signal %s en échec", signalId);
        }
    }

    /** Annule les alertes immédiates en attente du signal qui n'ont plus d'impact actif ; renvoie leur nombre. */
    public int annuler(UUID signalId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            try (Connection connexion = dataSource.getConnection();
                    PreparedStatement requete = connexion.prepareStatement(ANNULER + "a.signal_id = ? AND " + SANS_OBJET)) {
                requete.setObject(1, signalId);
                return requete.executeUpdate();
            }
        });
    }

    /** Annule l'alerte donnée si elle n'a plus d'objet, dans la transaction de l'appelant ; vrai si annulée. */
    static boolean annulerSiSansObjet(Connection connexion, UUID alerteId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(ANNULER + "a.id = ? AND " + SANS_OBJET)) {
            requete.setObject(1, alerteId);
            return requete.executeUpdate() == 1;
        }
    }
}
