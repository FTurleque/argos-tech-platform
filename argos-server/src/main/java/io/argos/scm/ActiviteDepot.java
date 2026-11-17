package io.argos.scm;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Activité d'un dépôt depuis une date, sous une forme neutre, commune à GitLab et à GitHub
 * (arc42 §5, « scm-connector »). Aucun élément ne porte d'identité de personne (PIN-03) : ni auteur,
 * ni assigné, ni relecteur ; seules des dates, des états et les titres des jalons sortent du module.
 *
 * @param tickets tickets créés ou modifiés depuis la date de début
 * @param fusions demandes de fusion (pull requests) créées ou modifiées depuis la date de début
 * @param pipelines pipelines (GitLab CI, GitHub Actions) créés depuis la date de début
 * @param versions versions publiées depuis la date de début
 * @param jalons jalons ouverts, ou fermés depuis la date de début
 * @param complet faux si une liste a été tronquée au nombre maximal de pages lues
 */
public record ActiviteDepot(List<Ticket> tickets, List<Fusion> fusions, List<Pipeline> pipelines,
        List<Version> versions, List<Jalon> jalons, boolean complet) {

    public ActiviteDepot {
        tickets = List.copyOf(tickets);
        fusions = List.copyOf(fusions);
        pipelines = List.copyOf(pipelines);
        versions = List.copyOf(versions);
        jalons = List.copyOf(jalons);
    }

    /** Ticket : date de création et, s'il est fermé, date de fermeture. */
    public record Ticket(Instant creeLe, Instant fermeLe) {
    }

    /** Demande de fusion : date de création et, si elle est fusionnée, date de fusion. */
    public record Fusion(Instant creeLe, Instant fusionneeLe) {
    }

    /** Issue d'un pipeline. */
    public enum Issue { REUSSI, ECHOUE, AUTRE }

    /** Pipeline : date de création et issue. */
    public record Pipeline(Instant creeLe, Issue issue) {
    }

    /** Version publiée. */
    public record Version(Instant publieeLe) {
    }

    /**
     * Jalon et ses tickets. {@code titre} est un texte externe, nettoyé des caractères de contrôle et
     * tronqué à 200 caractères ; il reste une donnée, jamais une instruction.
     *
     * @param reference identifiant du jalon dans la forge (numéro GitHub, identifiant GitLab)
     * @param ouvert vrai si le jalon est ouvert
     */
    public record Jalon(String reference, String titre, boolean ouvert, LocalDate echeance, int ticketsFermes,
            int ticketsTotal) {
    }
}
