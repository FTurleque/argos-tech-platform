package io.argos.api.recherche;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.profil.CatalogueProfils;
import io.argos.commun.DonneeInvalide;
import io.argos.commun.securite.Caller;
import io.argos.impact.ProjetsConcernes;
import io.argos.signal.recherche.RechercheVeille;

/**
 * Entrées non fiables de la recherche hors du conteneur (RCH-03, décision G4) : filtres inconnus ou mal formés refusés
 * par {@code CRITERE_INVALIDE} avec l'attribut en cause, pagination non entière refusée par {@code DONNEE_INVALIDE},
 * bornes de page et de taille conservées. La recherche elle-même est simulée : seule la demande qui lui parvient est
 * vérifiée.
 */
class TraductionCriteresTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-20T10:00:00Z");
    private static final int TEXTE_TROP_LONG = 501;
    private static final int TAILLE_HORS_BORNES = 101;
    private static final int TAILLE_TRES_GRANDE = 1000;
    private static final int TAILLE_DEMANDEE = 5;
    private static final int TAILLE_PAR_DEFAUT = 20;
    private static final CriteresRecherche AUCUN = new CriteresRecherche(null, null, null, null, null, null, null);

    private final Caller oscar = new Caller(UUID.randomUUID(), Set.of(UUID.randomUUID()), Set.of(), false, Set.of());
    private RechercheSimulee recherche;
    private TraductionCriteres traduction;

    @BeforeEach
    void preparer() {
        recherche = new RechercheSimulee();
        traduction = new TraductionCriteres();
        traduction.horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        traduction.profils = new CatalogueProfils(new ObjectMapper(), List.of("developpeur", "expert-metier", "artiste"),
                Optional.empty());
        traduction.recherche = recherche;
        // Page vide : les projets concernés sont complétés sans lecture en base.
        traduction.projetsConcernes = new ProjetsConcernes();
    }

    /** Recherche simulée : garde la page et la taille demandées, rend une page vide. */
    private static final class RechercheSimulee extends RechercheVeille {

        private final List<int[]> demandes = new ArrayList<>();

        @Override
        public Resultats rechercher(Criteres criteres, UUID compteId, int page, int taille) {
            demandes.add(new int[] {page, taille});
            return new Resultats(List.of(), page, taille, 0, false, null);
        }
    }

    private static CriteresRecherche criteres(String texte, List<String> types, String gravite, Integer horizon,
            List<String> profils) {
        return new CriteresRecherche(texte, null, null, types, gravite, horizon, profils);
    }

    private DonneeInvalide refus(CriteresRecherche criteres, String page, String taille) {
        return assertThrows(DonneeInvalide.class, () -> traduction.executer(criteres, oscar, true, page, taille));
    }

    private void critereRefuse(CriteresRecherche criteres, String attribut) {
        DonneeInvalide erreur = refus(criteres, null, null);
        assertEquals("CRITERE_INVALIDE", erreur.code());
        assertEquals(attribut, erreur.attribut());
    }

    /** Entrées non fiables : type, gravité, horizon, profil et texte hors bornes refusés avant toute recherche. */
    @Test
    void entreesNonFiables() {
        critereRefuse(criteres(null, List.of("INCONNU"), null, null, null), "types");
        critereRefuse(criteres(null, null, "EXTREME", null, null), "gravite");
        critereRefuse(criteres(null, null, null, 0, null), "horizonJours");
        critereRefuse(criteres(null, null, null, null, List.of("inconnu")), "profils");
        critereRefuse(criteres("x".repeat(TEXTE_TROP_LONG), null, null, null, null), "texte");
        assertEquals("CRITERE_INVALIDE", refus(AUCUN, null, String.valueOf(TAILLE_TRES_GRANDE)).code());

        assertTrue(recherche.demandes.isEmpty(), "aucune recherche pour une demande refusée");
    }

    /** Décision G4 : une page ou une taille non entière est refusée avec l'attribut en cause. */
    @Test
    void paginationNonEntiereRefusee() {
        DonneeInvalide page = refus(AUCUN, "abc", null);
        assertEquals("DONNEE_INVALIDE", page.code());
        assertEquals("page", page.attribut());
        DonneeInvalide taille = refus(AUCUN, null, "x");
        assertEquals("DONNEE_INVALIDE", taille.code());
        assertEquals("taille", taille.attribut());
        assertEquals("page", refus(AUCUN, "1e2", "dix").attribut(), "la page est lue avant la taille");
        assertTrue(recherche.demandes.isEmpty());
    }

    /** Bornes existantes conservées : taille de 1 à 100, page positive ou nulle ; défauts page 0, taille 20. */
    @Test
    void bornesDePaginationConservees() {
        assertEquals("CRITERE_INVALIDE", refus(AUCUN, null, String.valueOf(TAILLE_HORS_BORNES)).code());
        assertEquals("CRITERE_INVALIDE", refus(AUCUN, "-1", null).code());
        assertEquals("CRITERE_INVALIDE", refus(AUCUN, null, "0").code());

        RechercheVeille.Resultats demandee = traduction.executer(AUCUN, oscar, true, "1", String.valueOf(TAILLE_DEMANDEE));
        assertEquals(1, demandee.page());
        assertEquals(TAILLE_DEMANDEE, demandee.taille());
        RechercheVeille.Resultats parDefaut = traduction.executer(AUCUN, oscar, true, null, "");
        assertEquals(0, parDefaut.page());
        assertEquals(TAILLE_PAR_DEFAUT, parDefaut.taille());
    }
}
