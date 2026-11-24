package io.argos.api.comptes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.quarkus.test.junit.QuarkusTest;

/**
 * EXP-05 : comptes de base distincts et non privilégiés (ADR-0034). La base de test est initialisée par le script de
 * l'instance de production ({@code deploiement/postgres/initialiser-bases.sh}) : l'application se connecte avec le
 * rôle de connexion et Flyway applique les migrations sous le propriétaire.
 */
@QuarkusTest
class RolesBaseTest {

    /** Droits insuffisants (PostgreSQL). */
    private static final String DROITS_INSUFFISANTS = "42501";

    private static final String BASE_KEYCLOAK = "keycloak";

    @Inject
    DataSource dataSource;

    @Inject
    ContexteBase base;

    @Inject
    TestDb baseDeTest;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String urlBase;

    @ConfigProperty(name = "quarkus.datasource.username")
    String roleConnexion;

    @ConfigProperty(name = "quarkus.datasource.password")
    String motDePasseConnexion;

    @ConfigProperty(name = "quarkus.flyway.username")
    String proprietaire;

    @ConfigProperty(name = "quarkus.datasource.devservices.container-env.ARGOS_KEYCLOAK_DB_PASSWORD")
    String motDePasseKeycloak;

    /** EXP-05-S1 : ni le compte de Keycloak ni celui d'Argos n'ouvrent de session sur la base de l'autre. */
    @Test
    void connexionsCroiseesRefusees() {
        SQLException keycloakVersArgos = assertThrows(SQLException.class,
                () -> DriverManager.getConnection(urlBase, BASE_KEYCLOAK, motDePasseKeycloak).close());
        assertEquals(DROITS_INSUFFISANTS, keycloakVersArgos.getSQLState(), keycloakVersArgos.getMessage());

        SQLException argosVersKeycloak = assertThrows(SQLException.class,
                () -> DriverManager.getConnection(urlVers(BASE_KEYCLOAK), roleConnexion, motDePasseConnexion).close());
        assertEquals(DROITS_INSUFFISANTS, argosVersKeycloak.getSQLState(), argosVersKeycloak.getMessage());
    }

    /** EXP-05-S2 : aucun compte applicatif n'est superutilisateur ni ne contourne la RLS. */
    @Test
    void aucunSuperutilisateurApplicatif() throws SQLException {
        for (String role : List.of(roleConnexion, proprietaire, BASE_KEYCLOAK, "argos_technique", "argos_app")) {
            String attributs = lireEnConnexion("SELECT rolsuper || '|' || rolbypassrls FROM pg_roles WHERE rolname = '"
                    + identifiant(role) + "'");
            assertEquals("false|false", attributs, "attributs du rôle " + role);
        }
        assertEquals("0", lireEnConnexion("SELECT count(*) FROM pg_class WHERE relowner = 'argos_connexion'::regrole"),
                "le rôle de connexion ne possède aucun objet");
    }

    /** EXP-05-S3 : revenu à son rôle initial, une session d'utilisateur ne lit rien d'un espace dont il n'est pas membre. */
    @Test
    void abandonDuRoleRestreintSansEffet() {
        UUID espaceEtranger = Api.nouvelEspace("bob", "Espace de Bob, hors de portée");
        UUID espaceAlice = UUID.fromString(Api.creerEspace("alice", "Espace d'Alice"));
        UUID alice = Api.compte("alice");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM espace WHERE id = '" + espaceEtranger + "'"));

        String apresRetour = base.executer(new Contexte(alice, null, espaceAlice), connexion -> {
            executer(connexion, "RESET ROLE");
            return lire(connexion, "SELECT current_user || '|' || (SELECT count(*) FROM espace WHERE id = '"
                    + espaceEtranger + "') || '|' || (SELECT count(*) FROM appartenance WHERE espace_id = '"
                    + espaceEtranger + "')");
        });
        assertEquals(roleConnexion + "|0|0", apresRetour);
    }

    /** EXP-05-S4 : le rôle de connexion ne crée, ne modifie ni ne supprime aucune table. */
    @Test
    void schemaHorsDePorteeDuRoleDeConnexion() throws SQLException {
        for (String ddl : List.of("CREATE TABLE essai_ddl (id integer)", "ALTER TABLE espace ADD COLUMN essai_ddl integer",
                "DROP TABLE invitation", "CREATE INDEX essai_ddl ON espace (nom)")) {
            SQLException refus = assertThrows(SQLException.class, () -> executerEnConnexion(ddl), ddl);
            assertEquals(DROITS_INSUFFISANTS, refus.getSQLState(), ddl + " : " + refus.getMessage());
        }
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM pg_class WHERE relname = 'essai_ddl'"));
        assertEquals(0, baseDeTest.count(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'espace' AND column_name = 'essai_ddl'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM pg_class WHERE relname = 'invitation'"));
    }

    /** EXP-05-S5 : migrations appliquées sous le propriétaire, requêtes sous le rôle de connexion. */
    @Test
    void migrationsSousLeProprietaireRequetesSousLaConnexion() throws SQLException {
        assertEquals(0, baseDeTest.count(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND installed_by <> '" + identifiant(proprietaire) + "'"),
                "migration appliquée sous un autre compte que le propriétaire");
        assertTrue(baseDeTest.count("SELECT count(*) FROM flyway_schema_history WHERE version = '22'") == 1);
        assertEquals(proprietaire, lireEnConnexion("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = current_database()"));
        assertEquals(roleConnexion + "|" + roleConnexion, lireEnConnexion("SELECT session_user || '|' || current_user"));
    }

    /** Toute table sous RLS ouvre ses lignes à argos_technique hors contexte utilisateur, et à lui seul en plus d'argos_app. */
    @Test
    void touteTableSousRlsAUnePolitiqueTechnique() throws SQLException {
        List<String> sansPolitique = new ArrayList<>();
        try (Connection connexion = dataSource.getConnection(); Statement instruction = connexion.createStatement();
                ResultSet resultat = instruction.executeQuery("""
                        SELECT c.relname FROM pg_class c
                        WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p') AND c.relrowsecurity
                          AND NOT EXISTS (SELECT 1 FROM pg_policies p
                                          WHERE p.schemaname = 'public' AND p.tablename = c.relname
                                            AND 'argos_technique' = ANY (p.roles)
                                            AND p.qual = '%1$s' AND p.with_check = '%1$s')
                        ORDER BY c.relname
                        """.formatted("argos_hors_contexte_utilisateur()"))) {
            while (resultat.next()) {
                sansPolitique.add(resultat.getString(1));
            }
        }
        assertEquals(List.of(), sansPolitique, "tables sous RLS sans politique argos_technique hors contexte utilisateur");
        assertFalse(lireEnConnexion("SELECT has_table_privilege('flyway_schema_history', 'SELECT')").equals("true"),
                "l'historique des migrations n'est pas lisible par le rôle de connexion");
    }

    private String urlVers(String nomBase) {
        int finChemin = urlBase.indexOf('?');
        String sansParametres = finChemin < 0 ? urlBase : urlBase.substring(0, finChemin);
        String parametres = finChemin < 0 ? "" : urlBase.substring(finChemin);
        return sansParametres.substring(0, sansParametres.lastIndexOf('/') + 1) + nomBase + parametres;
    }

    private String lireEnConnexion(String sql) throws SQLException {
        try (Connection connexion = dataSource.getConnection()) {
            return lire(connexion, sql);
        }
    }

    private void executerEnConnexion(String sql) throws SQLException {
        try (Connection connexion = dataSource.getConnection()) {
            executer(connexion, sql);
        }
    }

    private static void executer(Connection connexion, String sql) throws SQLException {
        try (Statement instruction = connexion.createStatement()) {
            instruction.execute(sql);
        }
    }

    private static String lire(Connection connexion, String sql) throws SQLException {
        try (Statement instruction = connexion.createStatement(); ResultSet resultat = instruction.executeQuery(sql)) {
            assertTrue(resultat.next(), "aucune ligne : " + sql);
            return resultat.getString(1);
        }
    }

    private static String identifiant(String nom) {
        if (!nom.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("nom de rôle inattendu : " + nom);
        }
        return nom;
    }
}
