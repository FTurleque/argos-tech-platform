package io.argos.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Nettoyage de la base entre deux tests (AUD-TST-08) : chaque table du schéma {@code public} est soit
 * vidée par {@link TestDb#reset()}, soit déclarée conservée ou remise à zéro ; aucune table conservée
 * n'est vidée par cascade ; après la remise à zéro, toute table vidée est vide.
 */
@QuarkusTest
class NettoyageBaseTest {

    private static final String SOURCE_DE_TEST = "NETTOYAGE";
    private static final String SEPARATEUR_CLE = ">";

    @Inject
    TestDb baseDeTest;

    @Test
    void chaqueTableEstVideeOuDeclareeConservee() {
        Set<String> schema = new TreeSet<>(baseDeTest.tablesDuSchema());
        Set<String> videes = new TreeSet<>(baseDeTest.tablesVidees());
        assertFalse(videes.isEmpty(), "aucune table vidée : catalogue mal interrogé");

        Set<String> declarees = new TreeSet<>(videes);
        declarees.addAll(TestDb.TABLES_CONSERVEES);
        declarees.addAll(TestDb.TABLES_REMISES_A_ZERO);
        assertEquals(schema, declarees, "tables ni vidées, ni conservées, ni remises à zéro (ou déclarées sans exister)");

        Set<String> communes = new HashSet<>(TestDb.TABLES_CONSERVEES);
        communes.retainAll(TestDb.TABLES_REMISES_A_ZERO);
        assertEquals(Set.of(), communes, "une table est conservée ou remise à zéro, pas les deux");
        assertTrue(videes.stream().noneMatch(TestDb.TABLES_CONSERVEES::contains), "table conservée vidée");
    }

    /** {@code TRUNCATE … CASCADE} viderait une table conservée qui référence une table vidée. */
    @Test
    void aucuneTableConserveeNeReferenceUneTableVidee() {
        Set<String> gardees = new TreeSet<>(TestDb.TABLES_CONSERVEES);
        gardees.addAll(TestDb.TABLES_REMISES_A_ZERO);
        Set<String> videes = new HashSet<>(baseDeTest.tablesVidees());
        List<String> references = new ArrayList<>();
        for (String cle : clesEtrangeres()) {
            String[] extremites = cle.split(SEPARATEUR_CLE);
            if (gardees.contains(extremites[0]) && videes.contains(extremites[1])) {
                references.add(extremites[0] + " → " + extremites[1]);
            }
        }
        assertEquals(List.of(), references, "clés étrangères d'une table conservée vers une table vidée");
    }

    @Test
    void apresRemiseAZeroLesTablesVideesSontVidesEtLesTablesConserveesIntactes() {
        String espace = Api.creerEspace("alice", "Espace nettoyage");
        peuplerDesTablesVidees();
        assertTrue(baseDeTest.count("SELECT count(*) FROM cycle_vie") > 0, "jeu d'essai absent");

        baseDeTest.reset();

        Map<String, Long> nonVides = new TreeMap<>();
        for (String table : baseDeTest.tablesVidees()) {
            long lignes = baseDeTest.count("SELECT count(*) FROM " + table);
            if (lignes > 0) {
                nonVides.put(table, lignes);
            }
        }
        assertEquals(Map.of(), nonVides, "tables vidées encore peuplées après reset");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM espace WHERE id = ?::uuid", espace), "espace conservé");
        assertTrue(baseDeTest.count("SELECT count(*) FROM flyway_schema_history") > 0, "historique des migrations conservé");
        assertTrue(baseDeTest.count("SELECT count(*) FROM ia_prompt") > 0, "prompts IA conservés");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM source WHERE id = ?", SOURCE_DE_TEST), "source de test retirée");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM source WHERE id = 'OSV'"), "source OSV conservée");
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM source WHERE state <> 'NOMINAL'"), "sources remises à l'état nominal");
    }

    private void peuplerDesTablesVidees() {
        baseDeTest.exec("INSERT INTO source (id, label) VALUES (?, 'Source du test de nettoyage')", SOURCE_DE_TEST);
        baseDeTest.exec("UPDATE source SET state = 'DEGRADED', consecutive_failures = 3 WHERE id = 'OSV'");
        baseDeTest.exec("INSERT INTO ops_event (id, kind, source_id, detail, occurred_at)"
                + " VALUES (gen_random_uuid(), 'TEST', ?, 'nettoyage', now())", SOURCE_DE_TEST);
        baseDeTest.exec("INSERT INTO refus_acces (sujet_oidc, ressource, motif) VALUES ('nettoyage', 'GET /x', 'TEST')");
        baseDeTest.exec("INSERT INTO cycle_vie_produit (produit, etat, derniere_synchro_le) VALUES ('nettoyage', 'A_JOUR', now())");
        baseDeTest.exec("INSERT INTO cycle_vie (produit, cycle, en_fin_support) VALUES ('nettoyage', '1', false)");
    }

    /** Clés étrangères du schéma {@code public}, sous la forme « table>table référencée ». */
    private List<String> clesEtrangeres() {
        String cles = baseDeTest.string("""
                SELECT string_agg(origine.relname || '>' || referencee.relname, ',')
                FROM pg_constraint contrainte
                JOIN pg_class origine ON origine.oid = contrainte.conrelid
                JOIN pg_class referencee ON referencee.oid = contrainte.confrelid
                JOIN pg_namespace espaceDeNoms ON espaceDeNoms.oid = origine.relnamespace
                WHERE contrainte.contype = 'f' AND espaceDeNoms.nspname = 'public'
                """);
        return cles == null ? List.of() : List.of(cles.split(","));
    }
}
