package io.argos.projectintel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import io.argos.scm.ActiviteDepot;

/**
 * Agrège l'activité lue d'un dépôt en compteurs par jour et par type d'événement, avant tout
 * stockage (décision D1) : seuls des nombres sortent de cette étape, aucune identité (PIN-03).
 */
final class Agregation {

    /** Types d'événements comptés, tels qu'enregistrés dans {@code activite_compteur.type}. */
    enum Type { TICKET_OUVERT, TICKET_FERME, FUSION_OUVERTE, FUSION_FUSIONNEE, PIPELINE_REUSSI, PIPELINE_ECHOUE,
        PIPELINE_AUTRE, VERSION_PUBLIEE }

    private Agregation() {
    }

    /** Compteurs par jour (fuseau {@code zone}) des événements datés du jour {@code debut} au jour {@code fin} inclus. */
    static Map<LocalDate, Map<Type, Integer>> compter(ActiviteDepot activite, LocalDate debut, LocalDate fin, ZoneId zone) {
        Map<LocalDate, Map<Type, Integer>> jours = new TreeMap<>();
        for (ActiviteDepot.Ticket t : activite.tickets()) {
            ajouter(jours, t.creeLe(), Type.TICKET_OUVERT, debut, fin, zone);
            ajouter(jours, t.fermeLe(), Type.TICKET_FERME, debut, fin, zone);
        }
        for (ActiviteDepot.Fusion f : activite.fusions()) {
            ajouter(jours, f.creeLe(), Type.FUSION_OUVERTE, debut, fin, zone);
            ajouter(jours, f.fusionneeLe(), Type.FUSION_FUSIONNEE, debut, fin, zone);
        }
        for (ActiviteDepot.Pipeline p : activite.pipelines()) {
            Type type = switch (p.issue()) {
                case REUSSI -> Type.PIPELINE_REUSSI;
                case ECHOUE -> Type.PIPELINE_ECHOUE;
                case AUTRE -> Type.PIPELINE_AUTRE;
            };
            ajouter(jours, p.creeLe(), type, debut, fin, zone);
        }
        for (ActiviteDepot.Version v : activite.versions()) {
            ajouter(jours, v.publieeLe(), Type.VERSION_PUBLIEE, debut, fin, zone);
        }
        return jours;
    }

    private static void ajouter(Map<LocalDate, Map<Type, Integer>> jours, Instant date, Type type, LocalDate debut,
            LocalDate fin, ZoneId zone) {
        if (date == null) {
            return;
        }
        LocalDate jour = LocalDate.ofInstant(date, zone);
        if (jour.isBefore(debut) || jour.isAfter(fin)) {
            return;
        }
        jours.computeIfAbsent(jour, j -> new EnumMap<>(Type.class)).merge(type, 1, Integer::sum);
    }
}
