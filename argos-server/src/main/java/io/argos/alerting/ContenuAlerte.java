package io.argos.alerting;

import java.util.List;
import java.util.UUID;

import io.argos.aigateway.ReponseIa;

/**
 * Contenu d'une alerte ou d'un résumé, construit pour un seul destinataire à partir des impacts
 * qu'il est autorisé à voir (ALR-06, ACC-04). Une alerte immédiate a un seul élément ; un résumé en
 * liste plusieurs.
 *
 * @param elements éléments signalés
 * @param resumeIa résumé produit par l'IA, absent s'il n'est pas disponible
 * @param etatResumeIa {@code SANS_OBJET}, {@code DISPONIBLE}, {@code INDISPONIBLE}, {@code REFUSE} ou
 *        {@code PLAFOND_ATTEINT} (ALR-05)
 * @param mentionResumeIa « produit par l'IA » dès qu'un résumé est présent, absente sinon (IAG-05) ; le
 *        résumé s'appuie sur les sources des éléments
 */
public record ContenuAlerte(List<Element> elements, String resumeIa, String etatResumeIa, String mentionResumeIa) {

    public ContenuAlerte {
        elements = List.copyOf(elements);
        mentionResumeIa = resumeIa == null ? null : ReponseIa.MENTION;
    }

    public ContenuAlerte(List<Element> elements, String resumeIa, String etatResumeIa) {
        this(elements, resumeIa, etatResumeIa, null);
    }

    /** Élément signalé : signal, gravité, projets autorisés du destinataire, sources et lien vers l'élément. */
    public record Element(UUID signalId, String titre, String gravite, List<Projet> projets, List<Source> sources,
            String lien) {

        public Element {
            projets = List.copyOf(projets);
            sources = List.copyOf(sources);
        }
    }

    /** Projet cité et niveau de l'impact le plus fort ({@code EXACT}, {@code PROBABLE}, {@code A_VERIFIER}). */
    public record Projet(UUID id, String nom, String niveau) {
    }

    /** Source d'un élément : nom de la source, identifiant chez elle et adresse publique éventuelle. */
    public record Source(String nom, String identifiant, String url) {
    }
}
