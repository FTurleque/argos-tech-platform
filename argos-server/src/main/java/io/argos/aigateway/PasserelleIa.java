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
 * l'instance et les quotas de l'espace et du compte demandeur (IAG-03), rendue si le fournisseur n'a
 * pas pu être joint, appel du modèle de la tâche avec son prompt versionné,
 * validation de la sortie par le schéma de la tâche (IAG-04), puis réponse marquée « produit par l'IA »
 * avec ses sources (IAG-05). Chaque appel et chaque refus est tracé dans {@code ia_appel} par le
 * module {@code audit}, sans copie des entrées ni de la sortie (décision D2).
 *
 * <p>La passerelle ne lève jamais d'exception vers l'appelant pour un refus ou une panne du
 * fournisseur : elle répond par un motif, et l'appelant poursuit sans IA. L'IA est désactivée par
 * défaut ({@code argos.ia.active=false}) ; sans Ollama joignable, Argos fonctionne en mode
 * déterministe et la réservation de chaque demande est rendue.
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
        } catch (RuntimeException erreur) {
            LOG.infof("quotas IA illisibles : %s", erreur.getMessage());
            return false;
        }
    }

    /** Modèle de la tâche : {@code argos.ia.modeles.<tâche>} s'il est défini, sinon {@code argos.ia.modele}. */
    public String modele(String tache) {
        return config.getOptionalValue("argos.ia.modeles." + tache, String.class).filter(modele -> !modele.isBlank())
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
        Trace trace = new Trace(prompt, modele, demande.espaceId(), debut);

        if (demande.entrees().stream().anyMatch(entree -> !entree.classe().transmissible())) {
            return trace.refus(Issue.REFUS_CLASSE, 0);
        }
        List<EntreeIa> filtrees = new ArrayList<>();
        int masquees = 0;
        for (EntreeIa entree : demande.entrees()) {
            FiltreDonnees.Resultat filtrage = FiltreDonnees.filtrer(entree.valeur());
            if (filtrage.secret()) {
                return trace.refus(Issue.REFUS_DONNEE_SENSIBLE, 0);
            }
            masquees += filtrage.masquees();
            filtrees.add(new EntreeIa(entree.nom(), borner(filtrage.texte()), entree.classe()));
        }
        trace.empreinte = empreinte(prompt, filtrees);
        QuotasIa.Reservation reservation;
        try {
            reservation = quotas.reserver(demande.espaceId(), demande.compteId());
        } catch (RuntimeException erreur) {
            LOG.warnf("quotas IA indisponibles : %s", erreur.getMessage());
            return ReponseIa.refus(Issue.INDISPONIBLE, modele, prompt.version());
        }
        if (reservation == QuotasIa.Reservation.PLAFOND_INSTANCE) {
            return trace.refus(Issue.PLAFOND_INSTANCE, masquees);
        }
        if (reservation == QuotasIa.Reservation.QUOTA_ESPACE) {
            return trace.refus(Issue.QUOTA_ESPACE, masquees);
        }
        if (reservation == QuotasIa.Reservation.QUOTA_COMPTE) {
            return trace.refus(Issue.QUOTA_COMPTE, masquees);
        }

        FournisseurModele.Reponse brute;
        try {
            brute = fournisseur.executer(new FournisseurModele.Requete(modele, prompt.consignes(), message(filtrees),
                    schema(prompt)));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            return trace.refus(Issue.INDISPONIBLE, masquees);
        } catch (Exception erreur) {
            LOG.infof("modèle %s indisponible pour la tâche %s : %s", modele, prompt.tache(), erreur.toString());
            if (nonTransmis(erreur)) {
                try {
                    quotas.liberer(demande.espaceId(), demande.compteId());
                } catch (RuntimeException erreurRestitution) {
                    LOG.warnf("restitution du quota IA impossible : %s", erreurRestitution.getMessage());
                }
            }
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
        StringBuilder donnees = new StringBuilder("<donnees>\n");
        for (EntreeIa entree : entrees) {
            donnees.append("<donnee nom=\"").append(entree.nom()).append("\" classe=\"").append(entree.classe()).append("\">")
                    .append(entree.valeur().replace('<', '‹').replace('>', '›'))
                    .append("</donnee>\n");
        }
        return donnees.append("</donnees>").toString();
    }

    /** Schéma JSON de la sortie, transmis au fournisseur pour contraindre le format. */
    static Map<String, Object> schema(PromptIa prompt) {
        Map<String, Object> proprietes = new LinkedHashMap<>();
        List<String> obligatoires = new ArrayList<>();
        for (ChampSortie champ : prompt.champs()) {
            proprietes.put(champ.nom(), switch (champ.type()) {
                case TEXTE -> Map.of("type", "string");
                case DATE -> Map.of("type", "string", "format", "date");
                case ENTIERS -> Map.of("type", "array", "items", Map.of("type", "integer"));
            });
            if (champ.obligatoire()) {
                obligatoires.add(champ.nom());
            }
        }
        return Map.of("type", "object", "properties", proprietes, "required", obligatoires);
    }

    /**
     * Vrai si la demande n'a pas atteint le fournisseur : connexion refusée ou impossible, adresse
     * introuvable. La réservation est alors rendue ; une réponse en erreur ou trop lente reste comptée.
     */
    static boolean nonTransmis(Throwable erreur) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpConnectTimeoutException
                    || cause instanceof java.net.UnknownHostException
                    || cause instanceof java.nio.channels.UnresolvedAddressException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Empreinte SHA-256 de la tâche, de la version et des entrées après filtrage (secrets refusés,
     * données personnelles masquées, valeurs bornées) : jamais leur copie, ni l'empreinte d'une donnée
     * personnelle ou d'un secret. Une demande refusée avant filtrage n'a que l'empreinte de sa tâche.
     */
    private static String empreinte(PromptIa prompt, List<EntreeIa> entrees) {
        StringBuilder contenu = new StringBuilder(prompt.tache()).append('|').append(prompt.version());
        entrees.forEach(entree -> contenu.append('|').append(entree.nom()).append('=').append(entree.classe()).append(':')
                .append(entree.valeur().length()).append(':').append(entree.valeur()));
        return PromptIa.sha256(contenu.toString());
    }

    /** Écriture des traces d'une demande. */
    private final class Trace {

        private final PromptIa prompt;
        private final String modele;
        private final UUID espaceId;
        private final long debut;
        private String empreinte;

        Trace(PromptIa prompt, String modele, UUID espaceId, long debut) {
            this.prompt = prompt;
            this.modele = modele;
            this.espaceId = espaceId;
            this.debut = debut;
            this.empreinte = PromptIa.sha256(prompt.tache() + "|" + prompt.version());
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
