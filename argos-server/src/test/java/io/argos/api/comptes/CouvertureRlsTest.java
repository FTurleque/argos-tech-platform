package io.argos.api.comptes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;
import io.quarkus.test.junit.QuarkusTest;

/**
 * ACC-04-S2 sur l'ensemble du schéma : toute table accessible au rôle applicatif {@code argos_app}
 * est soumise à la RLS, sauf les données de référence globales listées ci-dessous, qui ne portent
 * aucun identifiant d'espace ni de compte. Sans contexte, aucune table soumise à la RLS ne renvoie de
 * ligne ; le contexte transmis à la base est local à la transaction (ADR-0016, design D1).
 *
 * <p>Une nouvelle table accessible à {@code argos_app} sans RLS fait échouer ce test tant qu'elle
 * n'est pas classée ici comme donnée de référence globale.
 */
@QuarkusTest
class CouvertureRlsTest {

    /**
     * Données de référence partagées par tous les espaces, lisibles par tout compte : avis publics,
     * calendrier des cycles de vie, textes officiels publiés, opportunités publiques. Aucune ne porte
     * d'espace ni de compte.
     */
    static final Set<String> REFERENCE_GLOBALE = Set.of(
            "signal", "cycle_vie_produit", "cycle_vie", "texte_officiel", "opportunite");

    /**
     * Colonnes qui rattachent une ligne à un espace ou à un compte ; toute clé étrangère vers
     * {@link #TABLES_DE_PERIMETRE} compte aussi, quel que soit le nom de la colonne.
     */
    private static final String COLONNES_DE_PERIMETRE = "'espace_id', 'workspace_id', 'owner_workspace_id',"
            + " 'compte_id', 'proprietaire_id', 'account_id', 'owner_account_id'";

    /** Tables dont une référence rattache une ligne à un espace ou à un compte. */
    private static final String TABLES_DE_PERIMETRE = "'compte', 'espace', 'project'";

    @Inject
    DataSource dataSource;

    @Inject
    ContexteBase base;

    /** État RLS d'une table accessible à argos_app. */
    record Table(String nom, boolean rls, boolean politique, boolean perimetre, boolean lecture) {
    }

    @Test
    void touteTableAccessibleAuRoleApplicatifEstSousRls() throws SQLException {
        List<Table> tables = tablesAccessibles();
        assertFalse(tables.isEmpty(), "aucune table accessible à argos_app : catalogue mal interrogé");

        List<String> sansRls = new ArrayList<>();
        List<String> referenceAvecPerimetre = new ArrayList<>();
        Set<String> referencesConstatees = new TreeSet<>();
        for (Table table : tables) {
            boolean protegee = table.rls() && table.politique();
            if (!protegee && !REFERENCE_GLOBALE.contains(table.nom())) {
                sansRls.add(table.nom());
            }
            if (!protegee && REFERENCE_GLOBALE.contains(table.nom())) {
                referencesConstatees.add(table.nom());
            }
            if (REFERENCE_GLOBALE.contains(table.nom()) && table.perimetre()) {
                referenceAvecPerimetre.add(table.nom());
            }
        }
        assertEquals(List.of(), sansRls, "tables accessibles à argos_app sans RLS ni politique");
        assertEquals(List.of(), referenceAvecPerimetre,
                "une donnée de référence globale ne porte ni espace ni compte");
        assertEquals(new TreeSet<>(REFERENCE_GLOBALE), referencesConstatees,
                "liste des données de référence globales à jour (accessibles à argos_app, hors RLS)");
    }

    @Test
    void roleApplicatifSansPrivilegeDeContournement() throws SQLException {
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("""
                        SELECT r.rolsuper, r.rolbypassrls, r.rolcanlogin,
                               (SELECT count(*) FROM pg_class k WHERE k.relowner = r.oid) AS possede
                        FROM pg_roles r WHERE r.rolname = 'argos_app'
                        """);
                ResultSet resultat = requete.executeQuery()) {
            assertTrue(resultat.next(), "rôle argos_app absent");
            assertFalse(resultat.getBoolean("rolsuper"), "argos_app super-utilisateur");
            assertFalse(resultat.getBoolean("rolbypassrls"), "argos_app contourne la RLS");
            assertFalse(resultat.getBoolean("rolcanlogin"), "argos_app se connecte directement");
            assertEquals(0, resultat.getLong("possede"), "argos_app propriétaire d'un objet : RLS non appliquée");
        }
    }

    /** ACC-04-S2 : sans espace ni compte courant, aucune table soumise à la RLS ne renvoie de ligne. */
    @Test
    void aucuneLigneSansContexteSurToutesLesTablesSousRls() throws SQLException {
        String espace = Api.creerEspace("alice", "Espace couverture RLS");
        Api.inviter("alice", espace, "couverture-rls@argos.test", "LECTEUR");

        Map<String, Long> visiblesSansContexte = new TreeMap<>();
        int peuplees = 0;
        for (Table table : tablesAccessibles()) {
            if (!table.rls() || !table.lecture()) {
                continue;
            }
            String sql = "SELECT count(*) FROM " + identifiant(table.nom());
            if (compterEnProprietaire(sql) > 0) {
                peuplees++;
            }
            long lignes = base.executer(new Contexte(null, null, null, null), connexion -> compter(connexion, sql));
            if (lignes != 0) {
                visiblesSansContexte.put(table.nom(), lignes);
            }
        }
        assertEquals(Map.of(), visiblesSansContexte, "lignes visibles sous argos_app sans contexte");
        assertTrue(peuplees >= 3, "au moins espace, appartenance et invitation contiennent des lignes");
    }

    /** Le rôle et l'espace courant transmis à la base ne survivent pas à la transaction. */
    @Test
    void contexteLocalALaTransaction() throws SQLException {
        String espace = Api.creerEspace("alice", "Espace contexte local");
        UUID alice = UUID.fromString(Api.idCompte("alice"));
        UUID id = UUID.fromString(espace);

        String vuDansLaTransaction = base.executer(new Contexte(alice, null, id), connexion -> lire(connexion,
                "SELECT current_user || '|' || current_setting('argos.espace_courant', true)"));
        assertEquals("argos_app|" + espace, vuDansLaTransaction);

        for (int essai = 0; essai < 5; essai++) {
            try (Connection connexion = dataSource.getConnection()) {
                String apres = lire(connexion, "SELECT current_user || '|' || coalesce(current_setting('argos.espace_courant', true), '')");
                assertFalse(apres.startsWith("argos_app|"), "rôle argos_app conservé hors transaction : " + apres);
                assertFalse(apres.endsWith(espace), "espace courant conservé hors transaction : " + apres);
            }
        }
    }

    private List<Table> tablesAccessibles() throws SQLException {
        String sql = """
                SELECT c.relname,
                       c.relrowsecurity,
                       EXISTS (SELECT 1 FROM pg_policies p
                               WHERE p.schemaname = n.nspname AND p.tablename = c.relname
                                 AND ('argos_app' = ANY (p.roles) OR 'public' = ANY (p.roles))) AS politique,
                       EXISTS (SELECT 1 FROM information_schema.columns col
                               WHERE col.table_schema = n.nspname AND col.table_name = c.relname
                                 AND col.column_name IN (%s))
                       OR EXISTS (SELECT 1 FROM pg_constraint k
                                  JOIN pg_class f ON f.oid = k.confrelid
                                  WHERE k.conrelid = c.oid AND k.contype = 'f'
                                    AND f.relname IN (%s)) AS perimetre,
                       has_table_privilege('argos_app', c.oid, 'SELECT') AS lecture
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                  AND c.relname <> 'flyway_schema_history'
                  AND (has_any_column_privilege('argos_app', c.oid, 'SELECT, INSERT, UPDATE')
                       OR has_table_privilege('argos_app', c.oid, 'DELETE, TRUNCATE'))
                ORDER BY c.relname
                """.formatted(COLONNES_DE_PERIMETRE, TABLES_DE_PERIMETRE);
        List<Table> tables = new ArrayList<>();
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement(sql);
                ResultSet resultat = requete.executeQuery()) {
            while (resultat.next()) {
                tables.add(new Table(resultat.getString(1), resultat.getBoolean(2), resultat.getBoolean(3), resultat.getBoolean(4),
                        resultat.getBoolean(5)));
            }
        }
        return tables;
    }

    private static String identifiant(String nom) {
        if (!nom.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("nom de table inattendu : " + nom);
        }
        return nom;
    }

    private long compterEnProprietaire(String sql) throws SQLException {
        try (Connection connexion = dataSource.getConnection()) {
            return compter(connexion, sql);
        }
    }

    private static long compter(Connection connexion, String sql) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(sql); ResultSet resultat = requete.executeQuery()) {
            resultat.next();
            return resultat.getLong(1);
        }
    }

    private static String lire(Connection connexion, String sql) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(sql); ResultSet resultat = requete.executeQuery()) {
            resultat.next();
            return resultat.getString(1);
        }
    }
}
