package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration V18 sur des données réelles (changement {@code automatiser-chaine-veille}, tâche 1.1) : une base
 * jetable est migrée jusqu'à V17, peuplée d'abonnements {@code ACTIVE} et {@code INACTIVE} et d'un signal
 * antérieur à l'empreinte normalisée, puis migrée en V18. Les abonnements {@code INACTIVE} deviennent
 * {@code SUSPENDED} avec leurs critères, canal, fréquence et retraits intacts (ABO-07, invariant INV-A2) ;
 * le signal garde sa version, sans empreinte normalisée jusqu'au premier passage (COL-10-S4).
 *
 * <p>Créer, migrer et supprimer une base relève de l'administration de l'instance : le test emploie le
 * superutilisateur de la base de test (Dev Services), non le rôle de connexion de l'application (ADR-0035).
 */
@QuarkusTest
class MigrationV18Test {

    @Test
    void inactifsDevenusSuspendusCriteresIntacts() throws Exception {
        Config config = ConfigProvider.getConfig();
        String url = config.getValue("quarkus.datasource.jdbc.url", String.class);
        String user = config.getValue("quarkus.datasource.devservices.username", String.class);
        String password = config.getValue("quarkus.datasource.devservices.password", String.class);
        String base = "argos_migration_v18_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection connexion = DriverManager.getConnection(url, user, password);
                Statement requete = connexion.createStatement()) {
            requete.execute("CREATE DATABASE " + base);
        }
        String urlJetable = url.replaceFirst("/[^/?]+(\\?|$)", "/" + base + "$1");
        try {
            flyway(urlJetable, user, password, "17").migrate();
            UUID compte = UUID.randomUUID();
            UUID espace = UUID.randomUUID();
            UUID actif = UUID.randomUUID();
            UUID inactif = UUID.randomUUID();
            UUID team = UUID.randomUUID();
            try (Connection connexion = DriverManager.getConnection(urlJetable, user, password);
                    Statement requete = connexion.createStatement()) {
                requete.execute("INSERT INTO compte(id, sujet_oidc, nom_affiche, email) VALUES ('" + compte
                        + "', 'sujet-v18', 'Compte V18', 'v18@argos.test')");
                requete.execute("INSERT INTO espace(id, nom, cree_par) VALUES ('" + espace + "', 'Espace V18', '" + compte + "')");
                requete.execute(abonnement(actif, "PERSONAL", null, compte, "ACTIVE", false));
                requete.execute(abonnement(inactif, "PERSONAL", null, compte, "INACTIVE", false));
                requete.execute(abonnement(team, "TEAM", espace, null, "ACTIVE", false));
                requete.execute("INSERT INTO subscription_opt_out(subscription_id, user_id, opted_out_at) VALUES ('" + team
                        + "', '" + compte + "', now())");
                requete.execute("""
                        BEGIN;
                        INSERT INTO signal(id, source, external_id, modified_at, collected_at, updated_at, content_hash,
                                           affected, current_version_id)
                        VALUES ('00000000-0000-0000-0000-0000000000a1', 'OSV', 'GHSA-V18', now(), now(), now(),
                                repeat('a', 64), '[]', '00000000-0000-0000-0000-0000000000b1');
                        INSERT INTO signal_version(id, signal_id, version_no, content_hash, source_modified_at,
                                                   collected_at, raw_content)
                        VALUES ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', 1,
                                repeat('a', 64), now(), now(), '{}');
                        COMMIT;
                        """);
            }

            flyway(urlJetable, user, password, "18").migrate();

            try (Connection connexion = DriverManager.getConnection(urlJetable, user, password);
                    Statement requete = connexion.createStatement()) {
                assertEquals("ACTIVE", valeur(requete, "SELECT state FROM subscription WHERE id = '" + actif + "'"));
                assertEquals("SUSPENDED", valeur(requete, "SELECT state FROM subscription WHERE id = '" + inactif + "'"));
                assertEquals("PACKAGE|pkg:maven/org.exemple/lib|EMAIL|IMMEDIATE",
                        valeur(requete, "SELECT filters->0->>'type' || '|' || (filters->0->>'value') || '|' || channel || '|' || frequency"
                                + " FROM subscription WHERE id = '" + inactif + "'"));
                assertEquals("1", valeur(requete, "SELECT count(*) FROM subscription_opt_out WHERE subscription_id = '" + team + "'"));
                assertEquals("0", valeur(requete, "SELECT count(*) FROM subscription WHERE state NOT IN ('ACTIVE', 'SUSPENDED')"));
                SQLException refus = assertThrows(SQLException.class, () -> requete.execute(
                        "UPDATE subscription SET state = 'INACTIVE' WHERE id = '" + actif + "'"));
                assertTrue(refus.getMessage().contains("subscription_state_check"), refus.getMessage());
                assertEquals("1", valeur(requete, "SELECT count(*) FROM signal_version WHERE normalized_hash IS NULL"));
                assertEquals("1", valeur(requete, "SELECT count(*) FROM signal WHERE normalized_hash IS NULL"));
                assertEquals("18", valeur(requete, "SELECT max(version::int) FROM flyway_schema_history WHERE success"));
            }
        } finally {
            try (Connection connexion = DriverManager.getConnection(url, user, password);
                    Statement requete = connexion.createStatement()) {
                requete.execute("DROP DATABASE IF EXISTS " + base + " WITH (FORCE)");
            }
        }
    }

    private static Flyway flyway(String url, String user, String password, String cible) {
        return Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").target(cible)
                .load();
    }

    private static String abonnement(UUID id, String portee, UUID espace, UUID compte, String etat, boolean obligatoire) {
        return """
                INSERT INTO subscription(id, scope, owner_workspace_id, owner_user_id, filters, channel, frequency, mandatory,
                                         state, created_at)
                VALUES ('%s', '%s', %s, %s, '[{"type":"PACKAGE","value":"pkg:maven/org.exemple/lib"}]', 'EMAIL', 'IMMEDIATE',
                        %s, '%s', now())
                """.formatted(id, portee, espace == null ? "NULL" : "'" + espace + "'",
                compte == null ? "NULL" : "'" + compte + "'", obligatoire, etat);
    }

    private static String valeur(Statement requete, String sql) throws SQLException {
        try (ResultSet resultat = requete.executeQuery(sql)) {
            resultat.next();
            return resultat.getString(1);
        }
    }
}
