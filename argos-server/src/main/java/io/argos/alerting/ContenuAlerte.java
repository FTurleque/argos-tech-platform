package io.argos.alerting;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.argos.aigateway.ReponseIa;
import io.argos.impact.MatchRules;

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

    /**
     * Projet cité et son qualificatif : le niveau de l'impact le plus fort pour une alerte d'impact, la version
     * pour un rappel du radar (exactement un des deux). Le JSON porte le qualificatif dans {@code niveau}.
     *
     * @param niveauImpact niveau d'impact, absent pour un rappel du radar
     * @param version version du projet (« version 17 », « version inconnue »), absente pour une alerte d'impact
     */
    public record Projet(UUID id, String nom, @JsonIgnore MatchRules.Level niveauImpact, @JsonIgnore String version) {

        public static Projet impact(UUID id, String nom, MatchRules.Level niveau) {
            return new Projet(id, nom, Objects.requireNonNull(niveau), null);
        }

        public static Projet radar(UUID id, String nom, String version) {
            return new Projet(id, nom, null, Objects.requireNonNull(version));
        }

        /** Relit un projet stocké : un qualificatif qui nomme un niveau d'impact est un niveau, sinon une version. */
        @JsonCreator
        static Projet lire(@JsonProperty("id") UUID id, @JsonProperty("nom") String nom,
                @JsonProperty("niveau") String qualificatif) {
            if (qualificatif == null) {
                return new Projet(id, nom, null, null);
            }
            for (MatchRules.Level niveau : MatchRules.Level.values()) {
                if (niveau.name().equals(qualificatif)) {
                    return impact(id, nom, niveau);
                }
            }
            return radar(id, nom, qualificatif);
        }

        /** Qualificatif écrit dans le JSON et affiché : le nom du niveau d'impact, sinon la version. */
        @JsonProperty("niveau")
        public String niveau() {
            return niveauImpact != null ? niveauImpact.name() : version;
        }
    }

    /** Source d'un élément : nom de la source, identifiant chez elle et adresse publique éventuelle. */
    public record Source(String nom, String identifiant, String url) {
    }
}
