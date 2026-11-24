package io.argos.aigateway;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.aigateway.PromptIa.ChampSortie;
import io.argos.aigateway.ReponseIa.Issue;
import io.argos.audit.JournalAppelsIa;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

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
 *
 * <p>Chaque demande tracée est comptée dans {@code argos_ia_appels_total{tache, issue}} et chronométrée dans
 * {@code argos_ia_duree_seconds{tache}} (EXP-14) ; l'issue du dernier appel au fournisseur alimente le contrôle de
 * disponibilité {@link SantePasserelleIa}.
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

    /** Joignabilité du fournisseur d'après le dernier appel transmis. */
    public enum EtatFournisseur { INCONNU, JOIGNABLE, INJOIGNABLE }

    private final AtomicReference<EtatFournisseur> etatFournisseur = new AtomicReference<>(EtatFournisseur.INCONNU);

    @Inject
    MeterRegistry metriques;

    /** Vrai si la passerelle est activée par {@code argos.ia.active}. */
    public boolean active() {
        return active;
    }

    /** Joignabilité du fournisseur d'après le dernier appel, {@code INCONNU} avant le premier. */
    public EtatFournisseur etatFournisseur() {
        return etatFournisseur.get();
    }

    /** Vrai si l'IA est active et qu'un appel imputé à cet espace ({@code null} : instance seule) serait accepté. */
    public boolean disponible(UUID espaceId) {
        try {
            return active && quotas.disponible(espaceId);
        } catch (IllegalStateException erreur) {
            LOG.infof(erreur, "quotas IA illisibles : %s", erreur.getMessage());
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
        Trace trace = new Trace(prompt, modele, demande.espaceId(), System.nanoTime());

        if (demande.entrees().stream().anyMatch(entree -> !entree.classe().transmissible())) {
            return trace.refus(Issue.REFUS_CLASSE, 0);
        }
        EntreesFiltrees filtrage = filtrerEntrees(demande.entrees());
        if (filtrage.secret()) {
            return trace.refus(Issue.REFUS_DONNEE_SENSIBLE, 0);
        }
        trace.empreinte = empreinte(prompt, filtrage.entrees());
        QuotasIa.Reservation reservation;
        try {
            reservation = quotas.reserver(demande.espaceId(), demande.compteId());
        } catch (IllegalStateException erreur) {
            LOG.warnf(erreur, "quotas IA indisponibles : %s", erreur.getMessage());
            return ReponseIa.refus(Issue.INDISPONIBLE, modele, prompt.version());
        }
        Optional<Issue> refusQuota = refusDeQuota(reservation);
        if (refusQuota.isPresent()) {
            return trace.refus(refusQuota.get(), filtrage.masquees());
        }

        Optional<FournisseurModele.Reponse> reponseModele = appelerModele(demande, prompt, modele, filtrage.entrees());
        if (reponseModele.isEmpty()) {
            return trace.refus(Issue.INDISPONIBLE, filtrage.masquees());
        }
        FournisseurModele.Reponse brute = reponseModele.get();
        Optional<Map<String, String>> champs = ValidationSortie.valider(json, prompt, brute.texte());
        trace.enregistrer(champs.isPresent() ? Issue.PRODUIT : Issue.SORTIE_INVALIDE, brute, filtrage.masquees());
        if (champs.isEmpty()) {
            LOG.infof("sortie non conforme au schéma de la tâche %s v%d : rejetée", prompt.tache(), prompt.version());
            return ReponseIa.refus(Issue.SORTIE_INVALIDE, brute.modele(), prompt.version());
        }
        return new ReponseIa(Issue.PRODUIT, champs.get(), ReponseIa.MENTION, demande.sources(), brute.modele(),
                prompt.version());
    }

    /**
     * Entrées filtrées et bornées, avec le nombre de valeurs masquées ; {@code secret} quand une entrée contient
     * une donnée sensible, auquel cas la demande est refusée sans rien transmettre.
     */
    private record EntreesFiltrees(List<EntreeIa> entrees, int masquees, boolean secret) {
    }

    private EntreesFiltrees filtrerEntrees(List<EntreeIa> entrees) {
        List<EntreeIa> filtrees = new ArrayList<>();
        int masquees = 0;
        for (EntreeIa entree : entrees) {
            FiltreDonnees.Resultat filtrage = FiltreDonnees.filtrer(entree.valeur());
            if (filtrage.secret()) {
                return new EntreesFiltrees(List.of(), 0, true);
            }
            masquees += filtrage.masquees();
            filtrees.add(new EntreeIa(entree.nom(), borner(filtrage.texte()), entree.classe()));
        }
        return new EntreesFiltrees(filtrees, masquees, false);
    }

    /** Issue du refus correspondant à une réservation de quota refusée ; vide si la réservation est accordée. */
    private static Optional<Issue> refusDeQuota(QuotasIa.Reservation reservation) {
        return switch (reservation) {
            case PLAFOND_INSTANCE -> Optional.of(Issue.PLAFOND_INSTANCE);
            case QUOTA_ESPACE -> Optional.of(Issue.QUOTA_ESPACE);
            case QUOTA_COMPTE -> Optional.of(Issue.QUOTA_COMPTE);
            default -> Optional.empty();
        };
    }

    /**
     * Réponse brute du modèle ; vide s'il est indisponible. Le quota réservé est restitué quand la demande n'a
     * pas été transmise au modèle.
     */
    private Optional<FournisseurModele.Reponse> appelerModele(DemandeIa demande, PromptIa prompt, String modele,
            List<EntreeIa> filtrees) {
        try {
            FournisseurModele.Reponse reponse = fournisseur.executer(new FournisseurModele.Requete(modele,
                    prompt.consignes(), message(filtrees), schema(prompt)));
            etatFournisseur.set(EtatFournisseur.JOIGNABLE);
            return Optional.of(reponse);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            LOG.infof(interruption, "appel du modèle %s interrompu pour la tâche %s", modele, prompt.tache());
            return Optional.empty();
        } catch (IOException erreur) {
            etatFournisseur.set(EtatFournisseur.INJOIGNABLE);
            LOG.infof(erreur, "modèle %s indisponible pour la tâche %s : %s", modele, prompt.tache(), erreur.toString());
            rendreQuotaSiNonTransmis(demande, erreur);
            return Optional.empty();
        }
    }

    /**
     * Panne du fournisseur ({@link FournisseurModele#executer}) : le quota réservé est rendu quand la demande n'a pas
     * atteint le fournisseur ; la passerelle répond alors par un motif et l'appelant poursuit sans IA.
     */
    private void rendreQuotaSiNonTransmis(DemandeIa demande, IOException erreur) {
        if (nonTransmis(erreur)) {
            try {
                quotas.liberer(demande.espaceId(), demande.compteId());
            } catch (IllegalStateException erreurRestitution) {
                LOG.warnf(erreurRestitution, "restitution du quota IA impossible : %s", erreurRestitution.getMessage());
            }
        }
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
            long dureeNanos = System.nanoTime() - debut;
            long dureeMs = TimeUnit.NANOSECONDS.toMillis(dureeNanos);
            Counter.builder("argos.ia.appels").description("Demandes à la passerelle IA tracées, par tâche et par issue")
                    .tag("tache", prompt.tache()).tag("issue", issue.name()).register(metriques).increment();
            Timer.builder("argos.ia.duree").description("Durée des demandes à la passerelle IA, par tâche")
                    .tag("tache", prompt.tache()).register(metriques).record(dureeNanos, TimeUnit.NANOSECONDS);
            journal.enregistrer(new JournalAppelsIa.Entree(prompt.tache(), prompt.version(), fournisseur.nom(),
                    brute == null ? modele : brute.modele(), espaceId, issue.name(),
                    brute == null ? 0 : brute.jetonsEntree(), brute == null ? 0 : brute.jetonsSortie(),
                    brute == null ? BigDecimal.ZERO : brute.coutEur(), empreinte, masquees,
                    dureeMs));
        }
    }
}
