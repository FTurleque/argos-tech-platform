package io.argos.conservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Règles de conservation des données journalisées (EXP-17, ADR-0041) : pour chaque règle, une ligne échue est
 * supprimée et une ligne récente conservée ; les lignes encore utiles ne sont jamais supprimées.
 */
@QuarkusTest
class ConservationTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-26T03:45:00Z");
    private static final String SOURCE = "OSV";
    private static final int TRES_ANCIEN_JOURS = 400;
    private static final int RECENT_JOURS = 2;

    @Inject
    TestDb baseDeTest;

    @Inject
    PurgesConservation purges;

    private UUID espace;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("alice", "Espace conservation");
    }

    private static OffsetDateTime ilYA(int jours) {
        return MAINTENANT.minus(Duration.ofDays(jours)).atOffset(ZoneOffset.UTC);
    }

    private Map<String, PurgesConservation.Bilan> purger() {
        return purges.executer(MAINTENANT).stream()
                .collect(Collectors.toMap(PurgesConservation.Bilan::regle, bilan -> bilan));
    }

    @Test
    void chaqueRegleDeclareeEstAppliqueeAvecSucces() {
        Map<String, PurgesConservation.Bilan> bilans = purger();
        assertEquals(List.of("actualites", "appels-ia", "appels-mcp", "avis-mis-a-l-ecart", "echecs-import",
                "evenements-exploitation", "opportunites", "quotas-ia", "refus-acces", "synchronisations"),
                bilans.keySet().stream().sorted().toList());
        assertTrue(bilans.values().stream().allMatch(PurgesConservation.Bilan::reussie));
        assertEquals(MAINTENANT.minus(Duration.ofDays(180)), bilans.get("refus-acces").limite());
    }

    @Test
    void journauxDeLAudit() {
        for (int jours : new int[] {TRES_ANCIEN_JOURS, RECENT_JOURS}) {
            baseDeTest.exec("INSERT INTO refus_acces (sujet_oidc, ressource, motif, horodatage) VALUES ('s', 'GET /x', 'M', ?)",
                    ilYA(jours));
            baseDeTest.exec("INSERT INTO mcp_appel (outil, parametres, duree_ms, issue, horodatage)"
                    + " VALUES ('outil', '{}'::jsonb, 1, 'SUCCES', ?)", ilYA(jours));
            baseDeTest.exec("INSERT INTO ia_appel (horodatage, tache, version_prompt, fournisseur, modele, issue,"
                    + " empreinte_entrees) VALUES (?, 'tache', 1, 'ollama', 'modele', 'PRODUIT', 'e')", ilYA(jours));
        }

        Map<String, PurgesConservation.Bilan> bilans = purger();

        assertEquals(1, bilans.get("refus-acces").supprimees());
        assertEquals(1, bilans.get("appels-mcp").supprimees());
        assertEquals(1, bilans.get("appels-ia").supprimees());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM mcp_appel"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ia_appel"));
    }

    @Test
    void compteursDeQuotasIaParJour() {
        LocalDate aujourdhui = LocalDate.ofInstant(MAINTENANT, ZoneOffset.UTC);
        baseDeTest.exec("INSERT INTO ia_quota_jour (jour, portee, appels) VALUES (?, 'INSTANCE', 3)", aujourdhui.minusDays(120));
        baseDeTest.exec("INSERT INTO ia_quota_jour (jour, portee, appels) VALUES (?, 'INSTANCE', 3)", aujourdhui);

        assertEquals(1, purger().get("quotas-ia").supprimees());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ia_quota_jour WHERE jour = ?", aujourdhui));
    }

    /** EXP-17-S3 : une synchronisation en cours, à reprendre ou citée par un avis mis à l'écart est conservée. */
    @Test
    void synchronisationsTermineesSeulement() {
        UUID terminee = synchronisation("SUCCEEDED", ilYA(TRES_ANCIEN_JOURS));
        baseDeTest.exec("INSERT INTO sync_attempt (id, run_id, attempt_no, occurred_at, error_kind)"
                + " VALUES (?, ?, 1, ?, 'TRANSIENT')", UUID.randomUUID(), terminee, ilYA(TRES_ANCIEN_JOURS));
        UUID recente = synchronisation("FAILED", ilYA(RECENT_JOURS));
        UUID aReprendre = synchronisation("RETRYABLE", null);
        UUID enCours = synchronisation("RUNNING", null);
        UUID citee = synchronisation("FAILED", ilYA(TRES_ANCIEN_JOURS));
        baseDeTest.exec("INSERT INTO ingestion_failure (id, source_id, reason, raw_content, received_at, run_id)"
                + " VALUES (?, ?, 'motif', '{}', ?, ?)", UUID.randomUUID(), SOURCE, ilYA(RECENT_JOURS), citee);

        assertEquals(2, purger().get("synchronisations").supprimees(), "une synchronisation et sa tentative");

        assertEquals(0, baseDeTest.count("SELECT count(*) FROM sync_run WHERE id = ?", terminee));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM sync_attempt WHERE run_id = ?", terminee));
        for (UUID conservee : List.of(recente, aReprendre, enCours, citee)) {
            assertEquals(1, baseDeTest.count("SELECT count(*) FROM sync_run WHERE id = ?", conservee));
        }
    }

    private UUID synchronisation(String etat, OffsetDateTime finLe) {
        UUID id = UUID.randomUUID();
        OffsetDateTime debut = finLe == null ? ilYA(TRES_ANCIEN_JOURS) : finLe;
        baseDeTest.exec("INSERT INTO sync_run (id, source_id, state, attempts, started_at, heartbeat_at, finished_at)"
                + " VALUES (?, ?, ?, 1, ?, ?, ?)", id, SOURCE, etat, debut, debut, finLe);
        return id;
    }

    @Test
    void avisMisALEcartEtEvenements() {
        for (int jours : new int[] {TRES_ANCIEN_JOURS, RECENT_JOURS}) {
            baseDeTest.exec("INSERT INTO ingestion_failure (id, source_id, reason, raw_content, received_at)"
                    + " VALUES (?, ?, 'motif', '{}', ?)", UUID.randomUUID(), SOURCE, ilYA(jours));
            baseDeTest.exec("INSERT INTO ops_event (id, kind, source_id, detail, occurred_at) VALUES (?, 'K', ?, 'd', ?)",
                    UUID.randomUUID(), SOURCE, ilYA(jours));
        }

        Map<String, PurgesConservation.Bilan> bilans = purger();

        assertEquals(1, bilans.get("avis-mis-a-l-ecart").supprimees());
        assertEquals(1, bilans.get("evenements-exploitation").supprimees());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ingestion_failure"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ops_event"));
    }

    @Test
    void echecsDImport() {
        UUID projet = UUID.randomUUID();
        baseDeTest.exec("INSERT INTO project (id, workspace_id, name) VALUES (?, ?, 'Projet conservation')", projet, espace);
        for (int jours : new int[] {TRES_ANCIEN_JOURS, RECENT_JOURS}) {
            baseDeTest.exec("INSERT INTO inventory_import_failure (id, project_id, reason, received_at) VALUES (?, ?, 'motif', ?)",
                    UUID.randomUUID(), projet, ilYA(jours));
        }

        assertEquals(1, purger().get("echecs-import").supprimees());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM inventory_import_failure"));
    }

    @Test
    void actualitesSelonLeurPremiereReception() {
        UUID ancienne = actualite(ilYA(TRES_ANCIEN_JOURS), ilYA(TRES_ANCIEN_JOURS));
        UUID recuRecemment = actualite(ilYA(TRES_ANCIEN_JOURS), ilYA(RECENT_JOURS));

        assertEquals(1, purger().get("actualites").supprimees());
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM actualite WHERE id = ?", ancienne));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM actualite WHERE id = ?", recuRecemment),
                "une page publiée il y a longtemps mais reçue récemment est conservée");
    }

    private UUID actualite(OffsetDateTime publieLe, OffsetDateTime recuLe) {
        UUID id = UUID.randomUUID();
        baseDeTest.exec("INSERT INTO actualite (id, espace_id, empreinte, type, titre, publie_le, recu_le)"
                + " VALUES (?, ?, ?, 'ARTICLE', 'titre', ?, ?)", id, espace, id.toString(), publieLe, recuLe);
        return id;
    }

    /** EXP-17-S5 : opportunités échues supprimées, opportunités ouvertes conservées. */
    @Test
    void opportunitesEchues() {
        UUID source = UUID.randomUUID();
        baseDeTest.exec("INSERT INTO source_profil (id, profil, type, adresse, libelle, rubrique)"
                + " VALUES (?, 'artiste', 'FLUX', 'https://exemple.org/flux', 'Flux', 'appels')", source);
        LocalDate aujourdhui = LocalDate.ofInstant(MAINTENANT, ZoneOffset.UTC);
        UUID dateDepassee = opportunite(source, aujourdhui.minusDays(200), ilYA(TRES_ANCIEN_JOURS));
        UUID aVenir = opportunite(source, aujourdhui.plusDays(10), ilYA(TRES_ANCIEN_JOURS));
        UUID fluxAncien = opportunite(source, null, ilYA(TRES_ANCIEN_JOURS));
        UUID fluxRecent = opportunite(source, null, ilYA(RECENT_JOURS));

        assertEquals(2, purger().get("opportunites").supprimees());
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM opportunite WHERE id IN (?, ?)", dateDepassee, fluxAncien));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM opportunite WHERE id IN (?, ?)", aVenir, fluxRecent));
    }

    private UUID opportunite(UUID source, LocalDate dateLimite, OffsetDateTime recuLe) {
        UUID id = UUID.randomUUID();
        baseDeTest.exec("INSERT INTO opportunite (id, source_id, empreinte, rubrique, titre, date_limite, recu_le, modifie_le)"
                + " VALUES (?, ?, ?, 'appels', 'titre', ?, ?, ?)", id, source, id.toString(), dateLimite, recuLe, recuLe);
        return id;
    }
}
