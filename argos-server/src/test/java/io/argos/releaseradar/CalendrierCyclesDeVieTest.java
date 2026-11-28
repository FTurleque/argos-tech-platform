package io.argos.releaseradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.releaseradar.CalendrierSorties.Echeance;
import io.argos.releaseradar.CalendrierSorties.Nature;
import io.argos.testsupport.Api;
import io.argos.testsupport.RadarFixtures;
import io.argos.testsupport.RadarFixtures.Cycle;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.Substitution;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Radar des sorties (RAD) : synchronisation qui isole l'échec d'un produit et conserve ses cycles en base, et
 * calendrier des échéances relié au référentiel des seuls espaces demandés.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class CalendrierCyclesDeVieTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-20T08:00:00Z");
    private static final Instant PLUS_TARD = Instant.parse("2026-11-21T08:00:00Z");
    private static final String CLE = "pkg:maven/org.exemple/biblio";
    private static final LocalDate DEBUT = LocalDate.parse("2020-01-01");
    private static final LocalDate FIN = LocalDate.parse("2030-12-31");

    @Inject
    TestDb baseDeTest;

    @Inject
    ClientEndoflife client;

    @Inject
    CyclesDeVie cyclesDeVie;

    @Inject
    CalendrierEndoflife calendrier;

    @BeforeEach
    void reinitialiser() {
        baseDeTest.reset();
    }

    // ------------------------------------------------------------------ synchronisation

    @Test
    void echecDUnProduitIsoleEtCyclesConnusConserves() {
        RadarFixtures.publier("biblio", RadarFixtures.reponse("biblio", cycle("1"), cycle("2")));
        RadarFixtures.publier("outil", RadarFixtures.reponse("outil", cycle("7")));
        assertEquals(Map.of("biblio", "A_JOUR", "outil", "A_JOUR"), cyclesDeVie.synchroniser(List.of("biblio", "outil"), MAINTENANT));

        StubServer.programmer("/endoflife/api/v1/products/biblio", Stub.status(503));
        Map<String, String> etats = cyclesDeVie.synchroniser(List.of("biblio", "outil"), PLUS_TARD);

        assertEquals(Map.of("biblio", "EN_ERREUR", "outil", "A_JOUR"), etats);
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM cycle_vie WHERE produit = 'biblio'"), "cycles connus conservés");
        CyclesDeVie.EtatProduit biblio = cyclesDeVie.etats(List.of("biblio", "outil", "absent")).get(0);
        assertEquals("EN_ERREUR", biblio.etat());
        assertEquals("réponse HTTP 503", biblio.derniereErreur());
        assertEquals(MAINTENANT, biblio.dernierSucces(), "dernier succès conservé");
        assertEquals(PLUS_TARD, biblio.derniereSynchro());
        assertEquals(2, cyclesDeVie.etats(List.of("biblio", "outil", "absent")).size(), "produit jamais synchronisé absent");
    }

    @Test
    void panneDAccesAuxDonneesPasseLeProduitEnErreurSansInterrompreLesAutres() {
        RadarFixtures.publier("outil", RadarFixtures.reponse("outil", cycle("7")));
        ClientEndoflife enPanne = clientQuiLeve(new IllegalStateException("base injoignable"), "biblio");
        try (Substitution substitution = Substitution.remplacer(cyclesDeVie, "client", enPanne)) {
            Map<String, String> etats = cyclesDeVie.synchroniser(List.of("biblio", "outil"), MAINTENANT);
            assertEquals(Map.of("biblio", "EN_ERREUR", "outil", "A_JOUR"), etats);
        }
        assertEquals("erreur interne de synchronisation",
                baseDeTest.string("SELECT derniere_erreur FROM cycle_vie_produit WHERE produit = 'biblio'"));
    }

    @Test
    void erreurImprevueRelanceeApresLesAutresProduits() {
        RadarFixtures.publier("outil", RadarFixtures.reponse("outil", cycle("7")));
        UnsupportedOperationException imprevue = new UnsupportedOperationException("imprévue");
        ClientEndoflife defaillant = clientQuiLeve(imprevue, "biblio");
        try (Substitution substitution = Substitution.remplacer(cyclesDeVie, "client", defaillant)) {
            assertSame(imprevue, assertThrows(UnsupportedOperationException.class,
                    () -> cyclesDeVie.synchroniser(List.of("biblio", "outil"), MAINTENANT)));
        }
        assertEquals("A_JOUR", baseDeTest.string("SELECT etat FROM cycle_vie_produit WHERE produit = 'outil'"));
        assertEquals("EN_ERREUR", baseDeTest.string("SELECT etat FROM cycle_vie_produit WHERE produit = 'biblio'"));
    }

    // ------------------------------------------------------------------ calendrier

    @Test
    void echeancesBorneesALaPeriodeEtAuReferentielDesEspacesDemandes() {
        UUID espace = Api.nouvelEspace("alice", "Calendrier radar");
        UUID autre = Api.nouvelEspace("alice", "Calendrier radar autre");
        RadarFixtures.standard(baseDeTest, espace, CLE, "Biblio", "ADOPT", "2.1.0", "Socle.", "biblio");
        RadarFixtures.publier("biblio", RadarFixtures.reponse("biblio",
                new Cycle("2", LocalDate.parse("2024-03-01"), LocalDate.parse("2027-03-01"), false, "2.1.4", LocalDate.parse("2026-10-01")),
                new Cycle("1", LocalDate.parse("2019-03-01"), null, false, null, null)));
        cyclesDeVie.synchroniser(List.of("biblio"), MAINTENANT);

        List<Echeance> echeances = calendrier.echeances(List.of(CLE), Set.of(espace), DEBUT, FIN);

        assertEquals(List.of(Nature.SORTIE, Nature.CORRECTIF, Nature.FIN_DE_SUPPORT),
                echeances.stream().map(Echeance::nature).toList(), "sortie du cycle 1 hors période, sans fin ni correctif");
        assertEquals("Dernière version de la branche 2", echeances.get(1).note());
        assertEquals(List.of(), calendrier.echeances(List.of(CLE), Set.of(autre), DEBUT, FIN), "référentiel d'un autre espace");
        assertEquals(List.of(), calendrier.echeances(List.of(), Set.of(espace), DEBUT, FIN));
    }

    @Test
    void finDeSupportDuCycleLePlusLongAuquelAppartientLaVersion() {
        UUID espace = Api.nouvelEspace("alice", "Fin de support radar");
        RadarFixtures.standard(baseDeTest, espace, CLE, "Biblio", "ADOPT", "14.2.0", "Socle.", "biblio");
        RadarFixtures.publier("biblio", RadarFixtures.reponse("biblio",
                new Cycle("14", null, LocalDate.parse("2027-01-01"), false, null, null),
                new Cycle("14.2", null, LocalDate.parse("2028-01-01"), false, null, null),
                new Cycle("15", null, null, false, null, null)));
        cyclesDeVie.synchroniser(List.of("biblio"), MAINTENANT);

        Optional<Echeance> fin = calendrier.finDeSupport(CLE, "v14.2.3", Set.of(espace));

        assertEquals("14.2", fin.orElseThrow().version());
        assertEquals(LocalDate.parse("2028-01-01"), fin.get().date());
        assertEquals(LocalDate.parse("2027-01-01"), calendrier.finDeSupport(CLE, "14-rc1", Set.of(espace)).orElseThrow().date());
        assertEquals(Optional.empty(), calendrier.finDeSupport(CLE, "15.0", Set.of(espace)), "cycle sans fin de support");
        assertEquals(Optional.empty(), calendrier.finDeSupport(CLE, "16", Set.of(espace)), "aucun cycle");
        assertEquals(Optional.empty(), calendrier.finDeSupport(CLE, " ", Set.of(espace)));
        assertEquals(Optional.empty(), calendrier.finDeSupport(null, "14", Set.of(espace)));
        assertEquals(Optional.empty(), calendrier.finDeSupport(CLE, null, Set.of(espace)));
    }

    private static Cycle cycle(String nom) {
        return new Cycle(nom, LocalDate.parse("2025-01-01"), null, false, nom + ".0", LocalDate.parse("2025-06-01"));
    }

    /** Client qui lève l'erreur donnée pour un produit et délègue au client réel pour les autres. */
    private ClientEndoflife clientQuiLeve(RuntimeException erreur, String produitDefaillant) {
        ClientEndoflife reel = Substitution.reel(client);
        return new ClientEndoflife() {
            @Override
            public List<Cycle> cycles(String produit) throws SourceIndisponible {
                if (produitDefaillant.equals(produit)) {
                    throw erreur;
                }
                return reel.cycles(produit);
            }
        };
    }
}
