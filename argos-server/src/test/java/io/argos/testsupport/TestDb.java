package io.argos.testsupport;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.vulnintel.CertFrSource;
import io.argos.vulnintel.KevSource;

/**
 * Remise à zéro des données de veille entre deux tests et requêtes de vérification, sous
 * l'utilisateur de connexion (hors RLS). Les comptes et les espaces, créés par l'API avec des
 * jetons réels, sont conservés : chaque test crée ses propres espaces.
 */
@ApplicationScoped
public class TestDb {

    @Inject
    DataSource dataSource;

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
    jakarta.enterprise.inject.Instance<io.quarkus.mailer.MockMailbox> mailbox;

    public void reset() {
        exec("""
                TRUNCATE dette_version, standard_historique, standard_suggestion, standard_technologie, radar_parametres,
                         cycle_vie, cycle_vie_produit, workspace_vue, actualite_impact, actualite_source, actualite, source_web, resume_element, alerte, subscription_opt_out, subscription, refus_acces, mcp_appel, project_impact, inventory_import_failure,
                         component, inventory, project, enrichment, signal_package, signal_alias,
                         signal_version, signal, ops_event, ingestion_failure, sync_attempt, sync_run, sync_cursor CASCADE
                """);
        exec("DELETE FROM source WHERE id <> 'OSV'");
        exec("""
                UPDATE source SET state = 'NOMINAL', consecutive_failures = 0, last_success_at = NULL,
                       last_failure_at = NULL, last_error = NULL
                """);
        kev.invalidate();
        certFr.invalidate();
        sleeper.reset();
        analysis.configure(SimulatedAnalysis.Mode.AVAILABLE, java.util.List.of());
        resumeIa.configure(SimulatedResumeIa.Mode.AVAILABLE);
        if (mailbox.isResolvable()) {
            mailbox.get().clear();
        }
        StubServer.reset();
        ReferentielsSimules.vider();
    }

    public void exec(String sql) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    public long count(String sql) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    public String string(String sql) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    public UUID uuid(String sql) {
        String value = string(sql);
        return value == null ? null : UUID.fromString(value);
    }
}
