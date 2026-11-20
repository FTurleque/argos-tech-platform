package io.argos.projectintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.argos.scm.ActiviteDepot;
import io.argos.scm.RepositoryActivity;

/**
 * Agrégation par jour avant stockage (tâche 1.2, décision D1) et absence de toute identité de
 * personne dans les structures de l'activité (PIN-03-S1, revue du schéma outillée) ; règles de la
 * synthèse : aucun chiffre inventé (PIN-02, PIN-04).
 */
class AgregationEtIdentiteTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** Mots qui désigneraient une personne : aucun composant d'enregistrement ne doit en contenir. */
    private static final Set<String> IDENTITE = Set.of("auteur", "author", "user", "login", "assignee", "assignees",
            "reviewer", "reviewers", "relecteur", "contributeur", "contributor", "personne", "person", "email", "mail",
            "membre", "member", "compte", "account", "name", "nom", "owner", "committer", "creator", "by", "par");

    @Test
    void agregationParJourDansLaFenetre() {
        LocalDate fin = LocalDate.of(2026, 11, 16);
        LocalDate debut = fin.minusDays(29);
        ActiviteDepot activite = new ActiviteDepot(
                List.of(new ActiviteDepot.Ticket(Instant.parse("2026-11-10T09:00:00Z"), Instant.parse("2026-11-12T09:00:00Z")),
                        new ActiviteDepot.Ticket(Instant.parse("2026-09-01T09:00:00Z"), Instant.parse("2026-11-12T10:00:00Z")),
                        new ActiviteDepot.Ticket(Instant.parse("2026-11-10T23:30:00Z"), null)),
                List.of(new ActiviteDepot.Fusion(Instant.parse("2026-11-11T09:00:00Z"), Instant.parse("2026-11-13T09:00:00Z"))),
                List.of(new ActiviteDepot.Pipeline(Instant.parse("2026-11-14T09:00:00Z"), ActiviteDepot.Issue.REUSSI),
                        new ActiviteDepot.Pipeline(Instant.parse("2026-11-14T10:00:00Z"), ActiviteDepot.Issue.ECHOUE)),
                List.of(new ActiviteDepot.Version(Instant.parse("2026-11-15T09:00:00Z"))), List.of(), true);

        Map<LocalDate, Map<Agregation.Type, Integer>> jours = Agregation.compter(activite, debut, fin, PARIS);

        assertEquals(1, jours.get(LocalDate.of(2026, 11, 10)).get(Agregation.Type.TICKET_OUVERT));
        assertEquals(1, jours.get(LocalDate.of(2026, 11, 11)).get(Agregation.Type.TICKET_OUVERT),
                "23:30 UTC le 10 novembre est le 11 à Paris : le jour suit le fuseau commun");
        assertEquals(2, jours.get(LocalDate.of(2026, 11, 12)).get(Agregation.Type.TICKET_FERME),
                "un ticket créé hors fenêtre et fermé dedans compte comme fermé");
        assertFalse(jours.containsKey(LocalDate.of(2026, 9, 1)), "aucun compteur hors fenêtre");
        assertEquals(1, jours.get(LocalDate.of(2026, 11, 13)).get(Agregation.Type.FUSION_FUSIONNEE));
        assertEquals(1, jours.get(LocalDate.of(2026, 11, 14)).get(Agregation.Type.PIPELINE_ECHOUE));
        assertEquals(1, jours.get(LocalDate.of(2026, 11, 15)).get(Agregation.Type.VERSION_PUBLIEE));
    }

    @Test
    void pin03S1AucuneIdentiteDansLesStructuresDeLActivite() {
        List<Class<?>> structures = Stream.<Class<?>>of(ActiviteDepot.class, RepositoryActivity.class, VueActivite.class,
                        Avancement.class, SyntheseActivite.class)
                .flatMap(classe -> Stream.<Class<?>>concat(Stream.of(classe), Stream.of(classe.getDeclaredClasses())))
                .filter(Class::isRecord).toList();
        assertTrue(structures.size() >= 20, "structures inspectées : " + structures);
        for (Class<?> structure : structures) {
            for (RecordComponent composant : structure.getRecordComponents()) {
                String nom = composant.getName();
                List<String> mots = Stream.of(nom.split("(?=\\p{Upper})")).map(mot -> mot.toLowerCase(Locale.ROOT)).toList();
                assertTrue(mots.stream().noneMatch(IDENTITE::contains),
                        structure.getSimpleName() + "." + nom + " désignerait une personne");
            }
        }
    }

    @Test
    void syntheseRefuseUnChiffreAbsentDesIndicateurs() {
        VueActivite vue = new VueActivite(UUID.randomUUID(),
                new VueActivite.Periode(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 11, 16), 30),
                new VueActivite.Compteurs(4, 2, 3, 1, 5, 1, 0, 1),
                List.of(new VueActivite.Jalon("Version 2", "OUVERT", LocalDate.of(2026, 12, 1), 27, 30,
                        Avancement.de(27, 30).orElseThrow())),
                new VueActivite.Fraicheur("A_JOUR", Instant.parse("2026-11-16T05:00:00Z"), null, true, null, null));

        assertTrue(SyntheseActivite.fondeeSurLesIndicateurs(
                "Sur 30 jours, 4 tickets ouverts ; jalon Version 2 : 27 sur 30 (90 %), échéance le 1/12/2026.", vue));
        assertFalse(SyntheseActivite.fondeeSurLesIndicateurs("Environ 93 % du travail est terminé.", vue),
                "pourcentage inventé");
        assertTrue(SyntheseActivite.fondeeSurLesIndicateurs("3 demandes de fusion ouvertes.", vue),
                "3 figure parmi les compteurs : admis");
        assertFalse(SyntheseActivite.fondeeSurLesIndicateurs("Fin prévue dans 17 jours.", vue), "prévision inventée");
        assertEquals(List.of("compteurs.ticketsOuverts", "compteurs.ticketsFermes", "compteurs.fusionsOuvertes",
                "compteurs.fusionsFusionnees", "compteurs.pipelinesReussis", "compteurs.pipelinesEchoues",
                "compteurs.pipelinesAutres", "compteurs.versionsPubliees", "jalons"), SyntheseActivite.indicateurs(vue));
    }
}
