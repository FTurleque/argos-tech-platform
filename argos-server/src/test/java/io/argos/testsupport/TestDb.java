package io.argos.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.vulnintel.CertFrSource;
import io.argos.vulnintel.KevSource;

/**
 * Remise à zéro des données de veille entre deux tests et requêtes de vérification, sous le
 * propriétaire de la base (hors RLS, droit de vider une table), comme les migrations. Les comptes et
 * les espaces, créés par l'API avec des jetons réels, sont conservés : chaque test crée ses propres
 * espaces.
 */
@ApplicationScoped
public class TestDb {

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String urlBase;

    @ConfigProperty(name = "quarkus.flyway.username")
    String proprietaire;

    /** Absent pour la base jetable des tests (authentification « trust »). */
    @ConfigProperty(name = "quarkus.flyway.password")
    Optional<String> motDePasseProprietaire;

    @Inject
    KevSource kev;

    @Inject
    CertFrSource certFr;

    @Inject
    RecordingSleeper sleeper;

    @Inject
    SimulatedAnalysis analysis;

    @Inject
    SimulatedResumeIa resumeIa;

    @Inject
    SimulatedSyntheseIa syntheseIa;

    @Inject
    jakarta.enterprise.inject.Instance<io.quarkus.mailer.MockMailbox> mailbox;

    public void reset() {
        exec("""
                TRUNCATE fiche_historique, fiche_domaine, fiche_reglementaire, texte_officiel, reg_collecte_jour,
                         reg_parametres, regle_dictionnaire, expert_domaine, domaine_metier, dette_version, standard_historique, standard_suggestion, standard_technologie, radar_parametres,
                         cycle_vie, cycle_vie_produit, activite_compteur, activite_jalon, activite_collecte, workspace_vue, actualite_impact, actualite_source, actualite, source_web, resume_element, alerte, subscription_opt_out, subscription, refus_acces, mcp_appel, ia_appel, ia_quota_jour, project_impact, inventory_import_failure,
                         component_occurrence, repository_inventory, project_repository, component, inventory, project, enrichment, signal_package, signal_alias,
                         signal_version, signal, ops_event, ingestion_failure, sync_attempt, sync_run, sync_cursor CASCADE
                """);
        exec("DELETE FROM source WHERE id <> 'OSV'");
        exec("""
                UPDATE reg_source SET etat = 'NOMINAL', echecs_consecutifs = 0, dernier_succes_le = NULL,
                       dernier_echec_le = NULL, derniere_erreur = NULL
                """);
        exec("""
                UPDATE source SET state = 'NOMINAL', consecutive_failures = 0, last_success_at = NULL,
                       last_failure_at = NULL, last_error = NULL
                """);
        kev.invalidate();
        certFr.invalidate();
        sleeper.reset();
        analysis.configure(SimulatedAnalysis.Mode.AVAILABLE, java.util.List.of());
        resumeIa.configure(SimulatedResumeIa.Mode.AVAILABLE);
        syntheseIa.configure(SimulatedSyntheseIa.Mode.FIDELE);
        if (mailbox.isResolvable()) {
            mailbox.get().clear();
        }
        StubServer.reset();
    }

    public void exec(String sql) {
        try (Connection connection = connexionProprietaire(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(sql, exception);
        }
    }

    public long count(String sql) {
        try (Connection connection = connexionProprietaire(); Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException(sql, exception);
        }
    }

    /** Comptage paramétré : les valeurs sont liées, jamais insérées dans le texte SQL. */
    public long count(String sql, Object... parametres) {
        try (Connection connection = connexionProprietaire(); PreparedStatement requete = connection.prepareStatement(sql)) {
            for (int rang = 0; rang < parametres.length; rang++) {
                requete.setObject(rang + 1, parametres[rang]);
            }
            try (ResultSet resultSet = requete.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(sql, exception);
        }
    }

    public String string(String sql) {
        try (Connection connection = connexionProprietaire(); Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        } catch (SQLException exception) {
            throw new IllegalStateException(sql, exception);
        }
    }

    public UUID uuid(String sql) {
        String value = string(sql);
        return value == null ? null : UUID.fromString(value);
    }

    private Connection connexionProprietaire() throws SQLException {
        return DriverManager.getConnection(urlBase, proprietaire, motDePasseProprietaire.orElse(""));
    }
}
