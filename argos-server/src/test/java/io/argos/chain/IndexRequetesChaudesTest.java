package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.argos.commun.Jdbc;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Index des requêtes chaudes (migration V27, AUD-PER-09, AUD-PER-26, AUD-PER-29) : index des clés étrangères
 * {@code component_id} et des listes d'exploitation présents, index redondants de {@code project_impact} absents,
 * index des impacts récents restreint aux impacts actifs et mené par l'espace.
 */
@QuarkusTest
class IndexRequetesChaudesTest {

    private static final Set<String> ATTENDUS = Set.of("project_impact_composant", "dette_version_composant",
            "ingestion_failure_source", "ops_event_source", "sync_attempt_run", "inventory_import_failure_projet",
            "inventory_import_failure_depot", "actualite_recue", "ingestion_failure_run",
            "project_impact_actifs_recents", "signal_package_ecosysteme",
            "signal_package_type", "signal_package_nom", "signal_modifie");

    private static final Set<String> SUPPRIMES = Set.of("project_impact_signal", "project_impact_project",
            "project_impact_recents");

    @Inject
    Jdbc jdbc;

    private List<String> index(Set<String> noms) {
        return jdbc.lister("SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname = ANY (?) ORDER BY 1",
                requete -> requete.setArray(1, requete.getConnection().createArrayOf("varchar", noms.toArray())),
                resultat -> resultat.getString(1));
    }

    @Test
    void indexCreesPresentsEtRedondantsAbsents() {
        assertEquals(ATTENDUS.stream().sorted().toList(), index(ATTENDUS));
        assertEquals(List.of(), index(SUPPRIMES), "AUD-PER-29 : aucun index redondant sur project_impact");
    }

    @Test
    void indexDesImpactsRecentsPartielEtMeneParLEspace() {
        String definition = jdbc.unique("SELECT indexdef FROM pg_indexes WHERE indexname = 'project_impact_actifs_recents'",
                Jdbc.sansParametre(), resultat -> resultat.getString(1)).orElseThrow();
        assertEquals("CREATE INDEX project_impact_actifs_recents ON public.project_impact USING btree (workspace_id, "
                + "updated_at DESC, id) WHERE ((state)::text = ANY ((ARRAY['OUVERT'::character varying, "
                + "'CONFIRME'::character varying])::text[]))", definition);
    }
}
