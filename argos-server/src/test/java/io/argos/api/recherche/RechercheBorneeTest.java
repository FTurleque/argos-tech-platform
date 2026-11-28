package io.argos.api.recherche;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.signal.recherche.RechercheVeille;
import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Recherche bornée (RCH-07) : la pertinence n'est calculée que pour les {@value RechercheVeille#PLAFOND} éléments les
 * plus récents qui correspondent, et le total restitué est plafonné, avec l'indicateur {@code totalPlafonne}.
 *
 * <p>Les avis sont écrits hors RLS, comme la collecte : leur vecteur de recherche est une colonne générée.
 */
@QuarkusTest
class RechercheBorneeTest {

    private static final int TAILLE_PAGE = 20;
    private static final int AU_DELA_DU_PLAFOND = RechercheVeille.PLAFOND + 100;
    private static final int SOUS_LE_PLAFOND = 37;

    @Inject
    TestDb baseDeTest;

    @Inject
    RechercheVeille recherche;

    private UUID compte;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        Api.nouvelEspace("alice", "Recherche bornée");
        compte = Api.compte("alice");
    }

    /** Avis numérotés de 1 à {@code nombre}, du plus ancien au plus récent, dont le titre contient le mot donné. */
    private void avis(String prefixe, int nombre, String titre) {
        // Avis et première version en une instruction : les deux références sont vérifiées à la fin de l'instruction.
        baseDeTest.exec("""
                WITH avis AS (
                    INSERT INTO signal (id, source, external_id, title, summary, severity, published_at, modified_at,
                                        collected_at, updated_at, content_hash, affected, current_version_id)
                    SELECT gen_random_uuid(), 'OSV', ?::text || n, ?::text || ' ' || n, NULL, 'HIGH',
                           now() - make_interval(hours => ?::int - n), now() - make_interval(hours => ?::int - n), now(),
                           now(), md5(?::text || n) || md5(?::text || n || 'b'), '[]'::jsonb, gen_random_uuid()
                    FROM generate_series(1, ?::int) n
                    RETURNING id, current_version_id, content_hash, modified_at)
                INSERT INTO signal_version (id, signal_id, version_no, content_hash, source_modified_at, collected_at,
                                            raw_content)
                SELECT current_version_id, id, 1, content_hash, modified_at, now(), '{}' FROM avis
                """, prefixe, titre, nombre, nombre, prefixe, prefixe, nombre);
    }

    private RechercheVeille.Resultats chercher(String mots) {
        return recherche.rechercher(new RechercheVeille.Criteres(mots, null, null, null, null, null, null, null), compte, 0,
                TAILLE_PAGE);
    }

    /** RCH-07-S1 : plus de 1 000 correspondances, total plafonné, pertinence calculée sur les 1 000 plus récents. */
    @Test
    void rch07S1SelectionPlusGrandeQueLePlafond() {
        // Avis le plus ancien, le plus pertinent : hors des 1 000 plus récents, il n'est pas classé.
        avis("ANCIEN-", 1, "plafond plafond plafond plafond plafond");
        baseDeTest.exec("UPDATE signal SET published_at = now() - interval '1 year', modified_at = now() - interval '1 year'"
                + " WHERE external_id = 'ANCIEN-1'");
        avis("BORNE-", AU_DELA_DU_PLAFOND, "Faille plafond");

        RechercheVeille.Resultats resultats = chercher("plafond");

        assertEquals(TAILLE_PAGE, resultats.elements().size());
        assertEquals(RechercheVeille.PLAFOND, resultats.total());
        assertTrue(resultats.totalPlafonne(), "indicateur de plafond");
        assertFalse(resultats.elements().stream().anyMatch(element -> element.identifiant().startsWith("ANCIEN-")),
                "l'avis plus ancien que les 1 000 plus récents n'est pas classé");
    }

    /** RCH-07-S2 : sous le plafond, total exact et ordre de pertinence, puis date. */
    @Test
    void rch07S2SelectionSousLePlafond() {
        avis("PETIT-", SOUS_LE_PLAFOND, "Faille bornee");
        avis("PERTINENT-", 1, "bornee bornee bornee");

        RechercheVeille.Resultats resultats = chercher("bornee");

        assertEquals(SOUS_LE_PLAFOND + 1, resultats.total());
        assertFalse(resultats.totalPlafonne());
        assertEquals("PERTINENT-1", resultats.elements().get(0).identifiant(), "le plus pertinent d'abord");
        assertEquals("PETIT-" + SOUS_LE_PLAFOND, resultats.elements().get(1).identifiant(), "puis le plus récent");
    }
}
