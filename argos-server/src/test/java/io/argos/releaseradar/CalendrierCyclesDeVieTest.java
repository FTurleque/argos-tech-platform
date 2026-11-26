package io.argos.releaseradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.releaseradar.CalendrierSorties.Echeance;
import io.argos.releaseradar.CalendrierSorties.Nature;
import io.argos.testsupport.Api;
import io.argos.testsupport.DataSourceEnPanne;
import io.argos.testsupport.RadarFixtures;
import io.argos.testsupport.RadarFixtures.Cycle;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.Substitution;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Radar des sorties (RAD) : connecteur endoflife.date face aux réponses limites (identifiant refusé, 404,
 * erreur HTTP, réponse trop volumineuse ou illisible, cycles mal formés), synchronisation qui isole l'échec
 * d'un produit et conserve ses cycles, et calendrier des échéances relié au référentiel des seuls espaces
 * demandés.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class CalendrierCyclesDeVieTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-20T08:00:00Z");
    private static final Instant PLUS_TARD = Instant.parse("2026-11-21T08:00:00Z");
    private static final int CYCLES_MAX = 500;
    private static final int TAILLE_REDUITE = 10;
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

    // ------------------------------------------------------------------ connecteur

    @Test
    void identifiantDeProduitInvalideRefuseSansAppel() {
        for (String produit : new String[] {null, "", "Majuscules", "../etc", "-tiret", "a".repeat(101)}) {
            assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles(produit), String.valueOf(produit));
        }
        assertEquals(List.of(), StubServer.received("/endoflife"));
    }

    @Test
    void produitInconnuEtErreurHttpDistingues() {
        assertEquals("produit inconnu d'endoflife.date",
                assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles("inconnu")).getMessage());
        StubServer.programmer("/endoflife/api/v1/products/panne", Stub.status(500));
        assertEquals("réponse HTTP 500",
                assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles("panne")).getMessage());
    }

    @Test
    void reponseTropVolumineuseRefusee() {
        RadarFixtures.publier("volumineux", RadarFixtures.reponse("volumineux", cycle("1")));
        try (Substitution taille = Substitution.remplacer(client, "tailleMax", TAILLE_REDUITE)) {
            assertEquals("réponse trop volumineuse",
                    assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles("volumineux")).getMessage());
        }
    }

    @Test
    void sourceInjoignableSignalee() {
        try (Substitution base = Substitution.remplacer(client, "base", "http://localhost:1/")) {
            assertTrue(assertThrows(ClientEndoflife.SourceIndisponible.class, () -> client.cycles("quarkus")).getMessage()
                    .startsWith("source injoignable"));
        }
    }

    @Test
    void reponseIllisibleOuSansListeDeCyclesRefusee() {
        assertThrows(ClientEndoflife.SourceIndisponible.class, () -> lire("{illisible"));
        assertThrows(ClientEndoflife.SourceIndisponible.class, () -> lire(""));
        assertThrows(ClientEndoflife.SourceIndisponible.class, () -> lire("{\"result\": {\"releases\": {}}}"));
        assertThrows(ClientEndoflife.SourceIndisponible.class, () -> lire("{\"result\": {}}"));
    }

    @Test
    void cyclesMalFormesIgnoresEtTextesNettoyes() throws ClientEndoflife.SourceIndisponible {
        List<ClientEndoflife.Cycle> cycles = lire("""
                {"result": {"releases": [
                  {"name": "  3.2\\u0007 ", "releaseDate": "2025-01-10", "eolFrom": "date-invalide", "isEol": true,
                   "latest": {"name": "3.2.5", "date": "2026-02-01"}},
                  {"name": 12},
                  {"name": "   "},
                  {"name": "%s"},
                  {"releaseDate": "2024-01-01"}
                ]}}
                """.formatted("x".repeat(101)));

        assertEquals(1, cycles.size());
        ClientEndoflife.Cycle cycle = cycles.get(0);
        assertEquals("3.2", cycle.cycle());
        assertEquals(LocalDate.parse("2025-01-10"), cycle.sortie());
        assertNull(cycle.finSupport(), "date illisible ignorée");
        assertTrue(cycle.enFinSupport());
        assertEquals("3.2.5", cycle.derniereVersion());
        assertEquals(LocalDate.parse("2026-02-01"), cycle.derniereVersionLe());
    }

    @Test
    void auPlusCinqCentsCyclesRetenus() throws ClientEndoflife.SourceIndisponible {
        String releases = IntStream.range(0, CYCLES_MAX + 3).mapToObj(rang -> "{\"name\": \"" + rang + "\"}")
                .collect(Collectors.joining(","));
        assertEquals(CYCLES_MAX, lire("{\"result\": {\"releases\": [" + releases + "]}}").size());
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
    void produitSansFraicheurDemandeeNeLitRien() {
        assertEquals(List.of(), cyclesDeVie.etats(List.of()));
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

    @Test
    void lectureDeLaFraicheurEnEchecSurBaseInjoignable() {
        CyclesDeVie horsConteneur = new CyclesDeVie();
        horsConteneur.dataSource = DataSourceEnPanne.creer();
        assertThrows(IllegalStateException.class, () -> horsConteneur.etats(List.of("biblio")));

        CalendrierEndoflife calendrierHorsConteneur = new CalendrierEndoflife();
        calendrierHorsConteneur.dataSource = DataSourceEnPanne.creer();
        assertEquals(List.of(), calendrierHorsConteneur.echeances(List.of(CLE), Set.of(), DEBUT, FIN), "aucun espace : aucune lecture");
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

    @Test
    void appartenanceDUneVersionAUnCycle() {
        assertTrue(CalendrierEndoflife.appartient(" V3.2.1 ", "3.2"));
        assertTrue(CalendrierEndoflife.appartient("3.2", "3.2"));
        assertFalse(CalendrierEndoflife.appartient("3.20", "3.2"));
        assertFalse(CalendrierEndoflife.appartient("13.2", "3.2"));
    }

    private static List<ClientEndoflife.Cycle> lire(String corps) throws ClientEndoflife.SourceIndisponible {
        return ClientEndoflife.lire(corps.getBytes(StandardCharsets.UTF_8));
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
