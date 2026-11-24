package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.ParametresSql;
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

    /** Instance PostgreSQL de test (Dev Services) et identifiants de son superutilisateur. */
    private record Instance(String url, String utilisateur, String motDePasse) {

        static Instance deTest() {
            Config config = ConfigProvider.getConfig();
            return new Instance(config.getValue("quarkus.datasource.jdbc.url", String.class),
                    config.getValue("quarkus.datasource.devservices.username", String.class),
                    config.getOptionalValue("quarkus.datasource.devservices.password", String.class).orElse(""));
        }

        /** Même instance, connectée à une autre base. */
        Instance base(String nom) {
            return new Instance(url.replaceFirst("/[^/?]+(\\?|$)", "/" + nom + "$1"), utilisateur, motDePasse);
        }

        Connection connecter() throws SQLException {
            return DriverManager.getConnection(url, utilisateur, motDePasse);
        }

        /** Crée la base vide {@code nom} ; le nom est vérifié puis cité, il n'est jamais lu tel quel. */
        void creerBase(String nom) throws SQLException {
            executer("CREATE DATABASE " + identifiantCite(nom));
        }

        void supprimerBase(String nom) throws SQLException {
            executer("DROP DATABASE IF EXISTS " + identifiantCite(nom) + " WITH (FORCE)");
        }

        private void executer(String sql) throws SQLException {
            try (Connection connexion = connecter(); Statement requete = connexion.createStatement()) {
                requete.execute(sql);
            }
        }

        private static String identifiantCite(String nom) {
            if (!nom.matches("[a-z][a-z0-9_]*")) {
                throw new IllegalArgumentException("nom de base inattendu : " + nom);
            }
            return '"' + nom + '"';
        }

        void migrer(String cible) {
            Flyway.configure().dataSource(url, utilisateur, motDePasse).locations("classpath:db/migration").target(cible)
                    .load().migrate();
        }
    }

    /** Identifiants des données créées avant V18. */
    private record Jeu(UUID compte, UUID espace, UUID actif, UUID inactif, UUID equipe) {

        static Jeu nouveau() {
            return new Jeu(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        }
    }

    @Test
    void inactifsDevenusSuspendusCriteresIntacts() throws Exception {
        Instance instance = Instance.deTest();
        String nomBase = "argos_migration_v18_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        instance.creerBase(nomBase);
        try {
            Instance jetable = instance.base(nomBase);
            jetable.migrer("17");
            Jeu jeu = Jeu.nouveau();
            peuplerAvantV18(jetable, jeu);
            jetable.migrer("18");
            verifierApresV18(jetable, jeu);
        } finally {
            instance.supprimerBase(nomBase);
        }
    }

    /** Abonnements ACTIVE, INACTIVE et d'équipe avec un retrait, et un signal antérieur à l'empreinte normalisée. */
    private static void peuplerAvantV18(Instance jetable, Jeu jeu) throws SQLException {
        try (Connection connexion = jetable.connecter()) {
            executer(connexion, "INSERT INTO compte(id, sujet_oidc, nom_affiche, email) VALUES (?, 'sujet-v18', 'Compte V18',"
                    + " 'v18@argos.test')", jeu.compte());
            executer(connexion, "INSERT INTO espace(id, nom, cree_par) VALUES (?, 'Espace V18', ?)", jeu.espace(), jeu.compte());
            creerAbonnement(connexion, jeu.actif(), "PERSONAL", null, jeu.compte(), "ACTIVE");
            creerAbonnement(connexion, jeu.inactif(), "PERSONAL", null, jeu.compte(), "INACTIVE");
            creerAbonnement(connexion, jeu.equipe(), "TEAM", jeu.espace(), null, "ACTIVE");
            executer(connexion, "INSERT INTO subscription_opt_out(subscription_id, user_id, opted_out_at) VALUES (?, ?, now())",
                    jeu.equipe(), jeu.compte());
            try (Statement requete = connexion.createStatement()) {
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
        }
    }

    /** INACTIVE devenu SUSPENDED avec ses critères et retraits, ancien état refusé, signal sans empreinte normalisée. */
    private static void verifierApresV18(Instance jetable, Jeu jeu) throws SQLException {
        String etat = "SELECT state FROM subscription WHERE id = ?";
        try (Connection connexion = jetable.connecter()) {
            assertEquals("ACTIVE", valeur(connexion, etat, jeu.actif()));
            assertEquals("SUSPENDED", valeur(connexion, etat, jeu.inactif()));
            assertEquals("PACKAGE|pkg:maven/org.exemple/lib|EMAIL|IMMEDIATE", valeur(connexion,
                    "SELECT filters->0->>'type' || '|' || (filters->0->>'value') || '|' || channel || '|' || frequency"
                            + " FROM subscription WHERE id = ?", jeu.inactif()));
            assertEquals("1", valeur(connexion, "SELECT count(*) FROM subscription_opt_out WHERE subscription_id = ?",
                    jeu.equipe()));
            assertEquals("0", valeur(connexion, "SELECT count(*) FROM subscription WHERE state NOT IN ('ACTIVE', 'SUSPENDED')"));
            SQLException refus = assertThrows(SQLException.class, () -> executer(connexion,
                    "UPDATE subscription SET state = 'INACTIVE' WHERE id = ?", jeu.actif()));
            assertTrue(refus.getMessage().contains("subscription_state_check"), refus.getMessage());
            assertEquals("1", valeur(connexion, "SELECT count(*) FROM signal_version WHERE normalized_hash IS NULL"));
            assertEquals("1", valeur(connexion, "SELECT count(*) FROM signal WHERE normalized_hash IS NULL"));
            assertEquals("18", valeur(connexion, "SELECT max(version::int) FROM flyway_schema_history WHERE success"));
        }
    }

    /** Abonnement non obligatoire au paquet pkg:maven/org.exemple/lib, par e-mail immédiat. */
    private static void creerAbonnement(Connection connexion, UUID id, String portee, UUID espace, UUID compte, String etat)
            throws SQLException {
        executer(connexion, """
                INSERT INTO subscription(id, scope, owner_workspace_id, owner_user_id, filters, channel, frequency, mandatory,
                                         state, created_at)
                VALUES (?, ?, ?, ?, '[{"type":"PACKAGE","value":"pkg:maven/org.exemple/lib"}]', 'EMAIL', 'IMMEDIATE',
                        false, ?, now())
                """, id, portee, espace, compte, etat);
    }

    private static void executer(Connection connexion, String sql, Object... parametres) throws SQLException {
        try (PreparedStatement requete = ParametresSql.preparer(connexion, sql, parametres)) {
            requete.execute();
        }
    }

    private static String valeur(Connection connexion, String sql, Object... parametres) throws SQLException {
        try (PreparedStatement requete = ParametresSql.preparer(connexion, sql, parametres);
                ResultSet resultat = requete.executeQuery()) {
            resultat.next();
            return resultat.getString(1);
        }
    }
}
