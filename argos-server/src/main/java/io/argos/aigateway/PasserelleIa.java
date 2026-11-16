package io.argos.aigateway;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.aigateway.PromptIa.ChampSortie;
import io.argos.aigateway.ReponseIa.Issue;
import io.argos.audit.JournalAppelsIa;

/**
 * Passerelle IA : seul point d'appel aux modèles (IAG-01 à IAG-05, DC-1, ADR-0004).
 *
 * <p>Pour chaque demande, dans cet ordre : refus de toute entrée CONFIDENTIAL ou RESTRICTED (IAG-02),
 * refus des secrets et masquage des données personnelles (Q-4), réservation sur le plafond de
 * l'instance et le quota de l'espace (IAG-03), appel du modèle de la tâche avec son prompt versionné,
 * validation de la sortie par le schéma de la tâche (IAG-04), puis réponse marquée « produit par l'IA »
 * avec ses sources (IAG-05). Chaque appel et chaque refus est tracé dans {@code ia_appel} par le
 * module {@code audit}, sans copie des entrées ni de la sortie (décision D2).
 *
 * <p>La passerelle ne lève jamais d'exception vers l'appelant pour un refus ou une panne du
 * fournisseur : elle répond par un motif, et l'appelant poursuit sans IA. Sans Ollama joignable, tout
 * Argos fonctionne en mode déterministe.
 */
@ApplicationScoped
public class PasserelleIa {

    private static final Logger LOG = Logger.getLogger(PasserelleIa.class);
    private static final String MARQUE_TRONQUE = " [tronqué]";

    @Inject
    RegistrePrompts registre;

    @Inject
    QuotasIa quotas;

    @Inject
    FournisseurModele fournisseur;

    @Inject
    JournalAppelsIa journal;

    @Inject
    ObjectMapper json;

    @Inject
    Config config;

    @ConfigProperty(name = "argos.ia.active")
    boolean active;

    @ConfigProperty(name = "argos.ia.modele")
    String modeleParDefaut;

    @ConfigProperty(name = "argos.ia.taille-max-entree")
    int tailleMaxEntree;

    /** Vrai si l'IA est active et qu'un appel imputé à cet espace ({@code null} : instance seule) serait accepté. */
    public boolean disponible(UUID espaceId) {
        try {
            return active && quotas.disponible(espaceId);
        } catch (RuntimeException e) {
            LOG.infof("quotas IA illisibles : %s", e.getMessage());
            return false;
        }
    }

    /** Modèle de la tâche : {@code argos.ia.modeles.<tâche>} s'il est défini, sinon {@code argos.ia.modele}. */
    public String modele(String tache) {
        return config.getOptionalValue("argos.ia.modeles." + tache, String.class).filter(m -> !m.isBlank())
                .orElse(modeleParDefaut);
    }

    public ReponseIa demander(DemandeIa demande) {
        PromptIa prompt = registre.prompt(demande.tache())
                .orElseThrow(() -> new IllegalArgumentException("tâche IA inconnue : " + demande.tache()));
        String modele = modele(prompt.tache());
        if (!active) {
            return ReponseIa.refus(Issue.DESACTIVEE, modele, prompt.version());
        }
        long debut = System.nanoTime();
        String empreinte = empreinte(prompt, demande);
        Trace trace = new Trace(prompt, modele, demande.espaceId(), empreinte, debut);

        if (demande.entrees().stream().anyMatch(e -> !e.classe().transmissible())) {
            return trace.refus(Issue.REFUS_CLASSE, 0);
        }
        List<EntreeIa> filtrees = new ArrayList<>();
        int masquees = 0;
        for (EntreeIa e : demande.entrees()) {
            FiltreDonnees.Resultat r = FiltreDonnees.filtrer(e.valeur());
            if (r.secret()) {
                return trace.refus(Issue.REFUS_DONNEE_SENSIBLE, 0);
            }
            masquees += r.masquees();
            filtrees.add(new EntreeIa(e.nom(), borner(r.texte()), e.classe()));
        }
        QuotasIa.Reservation reservation;
        try {
            reservation = quotas.reserver(demande.espaceId());
        } catch (RuntimeException e) {
            LOG.warnf("quotas IA indisponibles : %s", e.getMessage());
            return ReponseIa.refus(Issue.INDISPONIBLE, modele, prompt.version());
        }
        if (reservation == QuotasIa.Reservation.PLAFOND_INSTANCE) {
            return trace.refus(Issue.PLAFOND_INSTANCE, masquees);
        }
        if (reservation == QuotasIa.Reservation.QUOTA_ESPACE) {
            return trace.refus(Issue.QUOTA_ESPACE, masquees);
        }

        FournisseurModele.Reponse brute;
        try {
            brute = fournisseur.executer(new FournisseurModele.Requete(modele, prompt.consignes(), message(filtrees),
                    schema(prompt)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return trace.refus(Issue.INDISPONIBLE, masquees);
        } catch (Exception e) {
            LOG.infof("modèle %s indisponible pour la tâche %s : %s", modele, prompt.tache(), e.toString());
            return trace.refus(Issue.INDISPONIBLE, masquees);
        }
        Optional<Map<String, String>> champs = ValidationSortie.valider(json, prompt, brute.texte());
        trace.enregistrer(champs.isPresent() ? Issue.PRODUIT : Issue.SORTIE_INVALIDE, brute, masquees);
        if (champs.isEmpty()) {
            LOG.infof("sortie non conforme au schéma de la tâche %s v%d : rejetée", prompt.tache(), prompt.version());
            return ReponseIa.refus(Issue.SORTIE_INVALIDE, brute.modele(), prompt.version());
        }
        return new ReponseIa(Issue.PRODUIT, champs.get(), ReponseIa.MENTION, demande.sources(), brute.modele(),
                prompt.version());
    }

    /** Valeur bornée à {@code argos.ia.taille-max-entree} caractères. */
    private String borner(String valeur) {
        return valeur.length() <= tailleMaxEntree ? valeur
                : valeur.substring(0, Math.max(tailleMaxEntree - MARQUE_TRONQUE.length(), 0)) + MARQUE_TRONQUE;
    }

    /**
     * Message utilisateur : les entrées entre balises de données, chacune avec son nom et sa classe. Les
     * chevrons des valeurs sont neutralisés : une donnée ne peut ni fermer sa balise ni en ouvrir une.
     */
    static String message(List<EntreeIa> entrees) {
        StringBuilder b = new StringBuilder("<donnees>\n");
        for (EntreeIa e : entrees) {
            b.append("<donnee nom=\"").append(e.nom()).append("\" classe=\"").append(e.classe()).append("\">")
                    .append(e.valeur().replace('<', '‹').replace('>', '›'))
                    .append("</donnee>\n");
        }
        return b.append("</donnees>").toString();
    }

    /** Schéma JSON de la sortie, transmis au fournisseur pour contraindre le format. */
    static Map<String, Object> schema(PromptIa prompt) {
        Map<String, Object> proprietes = new LinkedHashMap<>();
        List<String> obligatoires = new ArrayList<>();
        for (ChampSortie c : prompt.champs()) {
            proprietes.put(c.nom(), switch (c.type()) {
                case TEXTE -> Map.of("type", "string");
                case DATE -> Map.of("type", "string", "format", "date");
                case ENTIERS -> Map.of("type", "array", "items", Map.of("type", "integer"));
            });
            if (c.obligatoire()) {
                obligatoires.add(c.nom());
            }
        }
        return Map.of("type", "object", "properties", proprietes, "required", obligatoires);
    }

    /** Empreinte SHA-256 de la tâche, de la version et des entrées telles que reçues : jamais leur copie. */
    private static String empreinte(PromptIa prompt, DemandeIa demande) {
        StringBuilder b = new StringBuilder(prompt.tache()).append('|').append(prompt.version());
        demande.entrees().forEach(e -> b.append('|').append(e.nom()).append('=').append(e.classe()).append(':')
                .append(e.valeur().length()).append(':').append(e.valeur()));
        return PromptIa.sha256(b.toString());
    }

    /** Écriture des traces d'une demande. */
    private final class Trace {

        private final PromptIa prompt;
        private final String modele;
        private final UUID espaceId;
        private final String empreinte;
        private final long debut;

        Trace(PromptIa prompt, String modele, UUID espaceId, String empreinte, long debut) {
            this.prompt = prompt;
            this.modele = modele;
            this.espaceId = espaceId;
            this.empreinte = empreinte;
            this.debut = debut;
        }

        ReponseIa refus(Issue issue, int masquees) {
            enregistrer(issue, null, masquees);
            if (issue == Issue.REFUS_CLASSE || issue == Issue.REFUS_DONNEE_SENSIBLE) {
                LOG.warnf("demande IA %s refusée : %s", prompt.tache(), issue);
            }
            return ReponseIa.refus(issue, modele, prompt.version());
        }

        void enregistrer(Issue issue, FournisseurModele.Reponse brute, int masquees) {
            long dureeMs = (System.nanoTime() - debut) / 1_000_000;
            journal.enregistrer(new JournalAppelsIa.Entree(prompt.tache(), prompt.version(), fournisseur.nom(),
                    brute == null ? modele : brute.modele(), espaceId, issue.name(),
                    brute == null ? 0 : brute.jetonsEntree(), brute == null ? 0 : brute.jetonsSortie(),
                    brute == null ? BigDecimal.ZERO : BigDecimal.valueOf(brute.coutEur()), empreinte, masquees,
                    dureeMs));
        }
    }
}
