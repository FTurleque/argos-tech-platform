package io.argos.regintel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.aigateway.DemandeIa;
import io.argos.aigateway.EntreeIa;
import io.argos.aigateway.PasserelleIa;
import io.argos.aigateway.RegistrePrompts;
import io.argos.aigateway.ReponseIa;
import io.argos.catalog.dictionnaire.DictionnaireMetier.Domaine;

/**
 * Appels de la veille réglementaire à la passerelle IA (ADR-0029) : proposition d'un domaine pour un
 * texte qu'aucune règle ne rattache (REG-02-S2) et résumé de la fiche (REG-04). Le texte officiel est
 * une donnée publique transmise entre balises de données, jamais parmi les consignes (R-11). La
 * passerelle est facultative : sans IA, chaque méthode renvoie un résultat vide et la veille poursuit
 * en mode déterministe.
 */
@ApplicationScoped
public class AnalyseIa {

    /** Longueur de l'extrait du texte transmis au modèle. */
    static final int EXTRAIT_MAX = 6000;

    /** Domaine proposé, avec la confiance déclarée par le modèle (1 à 100) et sa justification. */
    public record Proposition(String domaine, int confiance, String justification) {
    }

    /** Résumé produit et date d'effet proposée. */
    public record Fiche(String resume, LocalDate dateEffet) {
    }

    @Inject
    PasserelleIa passerelle;

    /** Vrai si un appel imputé à l'espace serait accepté. */
    public boolean disponible(UUID espace) {
        return passerelle.disponible(espace);
    }

    /** Domaine proposé parmi ceux de l'espace ; vide sans IA, sans proposition ou hors liste. */
    public Optional<Proposition> proposerDomaine(UUID espace, TexteOfficiel t, List<Domaine> domaines) {
        if (domaines.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder liste = new StringBuilder();
        for (int i = 0; i < domaines.size(); i++) {
            liste.append(i + 1).append(". ").append(domaines.get(i).libelle()).append('\n');
        }
        List<EntreeIa> entrees = new ArrayList<>(entrees(t));
        entrees.add(EntreeIa.interne("domaines", liste.toString()));
        ReponseIa r = passerelle.demander(new DemandeIa(RegistrePrompts.DOMAINE_REGLEMENTAIRE, espace, entrees, sources(t)));
        if (!r.produit() || r.champ("domaine").isBlank() || r.champ("confiance").isBlank()) {
            return Optional.empty();
        }
        int rang = Integer.parseInt(r.champ("domaine").split(",")[0]);
        int confiance = Integer.parseInt(r.champ("confiance").split(",")[0]);
        if (rang < 1 || rang > domaines.size() || confiance < 1 || confiance > 100) {
            return Optional.empty();
        }
        return Optional.of(new Proposition(domaines.get(rang - 1).code(), confiance, r.champ("justification")));
    }

    /** Résumé et date d'effet proposés pour la fiche ; vide sans IA ou pour une sortie non conforme. */
    public Optional<Fiche> preparer(UUID espace, TexteOfficiel t) {
        ReponseIa r = passerelle.demander(new DemandeIa(RegistrePrompts.FICHE_REGLEMENTAIRE, espace, entrees(t), sources(t)));
        if (!r.produit()) {
            return Optional.empty();
        }
        return Optional.of(new Fiche(r.champ("resume"), LocalDate.parse(r.champ("dateEffet"))));
    }

    private static List<EntreeIa> entrees(TexteOfficiel t) {
        String extrait = t.extrait() == null ? "" : t.extrait();
        return List.of(EntreeIa.publique("titre", t.titre()),
                EntreeIa.publique("nature", t.nature() == null ? "" : t.nature()),
                EntreeIa.publique("ministere", t.ministere() == null ? "" : t.ministere()),
                EntreeIa.publique("publication", t.datePublication().toString()),
                EntreeIa.publique("texte", extrait.length() > EXTRAIT_MAX ? extrait.substring(0, EXTRAIT_MAX) : extrait));
    }

    private static List<String> sources(TexteOfficiel t) {
        return List.of(t.identifiant());
    }
}
