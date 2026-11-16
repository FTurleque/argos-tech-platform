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

/** Remise à zéro des données entre deux tests et requêtes de vérification. */
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

    public void reset() {
        exec("""
                TRUNCATE subscription_opt_out, subscription, access_refusal, project_impact, inventory_import_failure,
                         component, inventory, project, workspace, enrichment, signal_package, signal_alias,
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
        StubServer.reset();
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
