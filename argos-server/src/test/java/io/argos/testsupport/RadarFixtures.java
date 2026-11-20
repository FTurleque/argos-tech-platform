package io.argos.testsupport;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Données du radar pour les tests : réponses enregistrées de l'API endoflife.date (format v1, mêmes
 * champs que la réponse publique de {@code /api/v1/products/<produit>}), servies par {@link StubServer},
 * et entrées du référentiel insérées directement sous l'utilisateur de connexion.
 */
public final class RadarFixtures {

    private RadarFixtures() {
    }

    /** Cycle d'un produit dans une réponse endoflife.date ; les dates absentes valent {@code null}. */
    public record Cycle(String nom, LocalDate sortie, LocalDate finSupport, boolean enFinSupport, String derniere,
            LocalDate derniereLe) {
    }

    /** Corps de réponse endoflife.date v1 pour le produit et ses cycles. */
    public static String reponse(String produit, Cycle... cycles) {
        String releases = Stream.of(cycles).map(cycle -> """
                {"name":"%s","codename":null,"label":"%s","releaseDate":%s,"isLts":false,"ltsFrom":null,
                 "isEol":%s,"eolFrom":%s,"isMaintained":%s,
                 "latest":{"name":%s,"date":%s,"link":null},"custom":null}"""
                .formatted(cycle.nom(), cycle.nom(), texte(cycle.sortie()), cycle.enFinSupport(), texte(cycle.finSupport()),
                        !cycle.enFinSupport(),
                        cycle.derniere() == null ? "null" : "\"" + cycle.derniere() + "\"", texte(cycle.derniereLe())))
                .collect(Collectors.joining(","));
        return """
                {"schema_version":"1.2.1","generated_at":"2026-11-16T15:33:31+00:00",
                 "result":{"name":"%s","label":"%s","category":"framework","releases":[%s]}}"""
                .formatted(produit, produit, releases);
    }

    /** Programme la réponse endoflife.date d'un produit. */
    public static void publier(String produit, String corps) {
        StubServer.programmer("/endoflife/api/v1/products/" + produit, StubServer.Stub.succes(corps));
    }

    /** Insère une entrée du référentiel, au nom du créateur de l'espace. */
    public static void standard(TestDb baseDeTest, UUID espace, String cle, String libelle, String statut, String version,
            String justification, String produit) {
        baseDeTest.exec("""
                INSERT INTO standard_technologie (id, espace_id, cle, libelle, statut, version_recommandee, responsable,
                    justification, date_revue, produit_cycle_vie, cree_le, modifie_le, modifie_par)
                SELECT '%s', id, '%s', '%s', '%s', %s, 'Équipe socle', %s, DATE '2027-06-30', %s, now(), now(), cree_par
                FROM espace WHERE id = '%s'
                """.formatted(UUID.randomUUID(), cle, libelle, statut, litteral(version), litteral(justification),
                litteral(produit), espace));
    }

    private static String texte(LocalDate date) {
        return date == null ? "null" : "\"" + date + "\"";
    }

    private static String litteral(String valeur) {
        return valeur == null ? "NULL" : "'" + valeur.replace("'", "''") + "'";
    }
}
