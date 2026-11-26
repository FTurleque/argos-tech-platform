package io.argos.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.vulnintel.CertFrSource;
import io.argos.vulnintel.KevSource;

/**
 * Remise à zéro des données entre deux tests et requêtes de vérification, sous le propriétaire de la
 * base (hors RLS, droit de vider une table), comme les migrations.
 *
 * <p>Les tables vidées se lisent dans le catalogue ({@code pg_tables}, schéma {@code public}) : toute
 * table y est vidée, en un seul {@code TRUNCATE … CASCADE}, sauf les {@link #TABLES_CONSERVEES} et les
 * {@link #TABLES_REMISES_A_ZERO}, qui gardent leurs lignes de référence. Une table ajoutée par une
 * migration est donc vidée sans autre déclaration ; {@code NettoyageBaseTest} vérifie la partition.
 */
@ApplicationScoped
public class TestDb {

    /**
     * Tables conservées d'un test à l'autre : historique des migrations ; comptes, espaces,
     * appartenances et invitations créés par l'API avec des jetons réels (chaque test crée ses propres
     * espaces) ; préférences des utilisateurs (retirées par utilisateur, {@code ArtisteFixtures}) ;
     * prompts IA inscrits au démarrage du serveur ({@code RegistrePrompts}).
     */
    public static final Set<String> TABLES_CONSERVEES = Set.of("flyway_schema_history", "compte", "espace",
            "appartenance", "invitation", "workspace_preference_utilisateur", "ia_prompt");

    /**
     * Référentiels des sources, initialisés par les migrations : seules les sources ajoutées par un
     * test sont retirées, et l'état de santé de chaque source revient à l'état nominal.
     */
    public static final Set<String> TABLES_REMISES_A_ZERO = Set.of("source", "reg_source");

    private static final Pattern NOM_DE_TABLE = Pattern.compile("[a-z_][a-z0-9_]*");

    /** Tables vidées, lues une fois dans le catalogue : les migrations ne changent plus pendant les tests. */
    private volatile List<String> tablesVidees;

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
    Asynchrone asynchrone;

    @Inject
    SimulatedAnalysis analysis;

    @Inject
    SimulatedResumeIa resumeIa;

    @Inject
    SimulatedSyntheseIa syntheseIa;

    @Inject
    jakarta.enterprise.inject.Instance<io.quarkus.mailer.MockMailbox> mailbox;

    public void reset() {
        // Les observateurs asynchrones du test précédent (alertes, annonces) se terminent avant la remise à zéro.
        asynchrone.attendre();
        exec("TRUNCATE " + String.join(", ", tablesVidees()) + " CASCADE");
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

    /** Tables du schéma {@code public}, par ordre alphabétique, lues dans le catalogue. */
    public List<String> tablesDuSchema() {
        List<String> tables = new ArrayList<>();
        try (Connection connection = connexionProprietaire();
                PreparedStatement requete = connection.prepareStatement(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename");
                ResultSet resultSet = requete.executeQuery()) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("catalogue des tables illisible", exception);
        }
        return tables;
    }

    /** Tables vidées par {@link #reset()} : toutes celles du schéma, hors tables conservées ou remises à zéro. */
    public List<String> tablesVidees() {
        List<String> connues = tablesVidees;
        if (connues == null) {
            connues = tablesDuSchema().stream()
                    .filter(table -> !TABLES_CONSERVEES.contains(table) && !TABLES_REMISES_A_ZERO.contains(table))
                    .map(TestDb::identifiant)
                    .toList();
            tablesVidees = connues;
        }
        return connues;
    }

    private static String identifiant(String table) {
        if (!NOM_DE_TABLE.matcher(table).matches()) {
            throw new IllegalStateException("nom de table inattendu : " + table);
        }
        return table;
    }

    /** Instruction, avec ou sans paramètres liés. */
    public void exec(String sql, Object... parametres) {
        try (Connection connection = connexionProprietaire();
                PreparedStatement requete = ParametresSql.preparer(connection, sql, parametres)) {
            requete.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException(sql, exception);
        }
    }

    /** Comptage, avec ou sans paramètres liés. */
    public long count(String sql, Object... parametres) {
        try (Connection connection = connexionProprietaire();
                PreparedStatement requete = ParametresSql.preparer(connection, sql, parametres);
                ResultSet resultSet = requete.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException(sql, exception);
        }
    }

    /** Première colonne de la première ligne, avec ou sans paramètres liés ; nul sans ligne. */
    public String string(String sql, Object... parametres) {
        try (Connection connection = connexionProprietaire();
                PreparedStatement requete = ParametresSql.preparer(connection, sql, parametres);
                ResultSet resultSet = requete.executeQuery()) {
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
