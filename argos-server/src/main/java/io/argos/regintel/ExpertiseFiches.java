package io.argos.regintel;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.scm.DemandesEvolution;
import io.argos.scm.ScmException;
import io.argos.workspace.AccesRefuse;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.Introuvable;
import io.argos.workspace.MotifRefus;
import io.argos.workspace.RegleViolee;

/**
 * Travail de l'expert métier sur une fiche (REG-05, REG-06, REG-08) : correction, validation ou rejet,
 * transitions du cycle de vie, demande d'évolution associée. Chaque écriture est historisée
 * ({@code fiche_historique}) dans la même transaction.
 *
 * <p>Chaque opération s'exécute sous le rôle {@code argos_app}, avec le compte de l'appelant et
 * l'espace de la fiche comme espace courant : la base n'autorise la modification qu'à un expert d'un
 * des domaines de la fiche (politique {@code fiche_reglementaire_expertise}, migration V16). Une fiche
 * invisible de l'appelant est refusée comme hors périmètre (réponse « introuvable », refus journalisé) ;
 * une fiche visible d'un non-expert est refusée pour rôle insuffisant.
 */
@ApplicationScoped
public class ExpertiseFiches {

    private static final Logger LOG = Logger.getLogger(ExpertiseFiches.class);

    /**
     * Correction d'une fiche en attente ; un champ absent ({@code null}) reste inchangé.
     *
     * @param dateEffetAConfirmer vrai pour retirer la date d'effet (« date à confirmer »)
     * @param domaines            domaines de l'espace retenus pour la fiche
     */
    public record Correction(String resume, LocalDate dateEffet, Boolean dateEffetAConfirmer, List<String> domaines) {
    }

    @Inject
    ContexteBase base;

    @Inject
    FichesEnBase fiches;

    @Inject
    PreparationFiches preparation;

    @Inject
    DemandesEvolution demandes;

    /** Corrige une fiche en attente de validation ; chaque champ modifié est historisé avec sa valeur précédente. */
    public FicheAnalyse corriger(UUID compte, UUID id, Correction correction, Instant maintenant) {
        if (correction == null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez au moins un champ à corriger.", null);
        }
        String resume = correction.resume() == null ? null : correction.resume().strip();
        if (resume != null && (resume.isEmpty() || resume.length() > 2000)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Le résumé compte de 1 à 2 000 caractères.", "resume");
        }
        if (correction.dateEffet() != null && Boolean.TRUE.equals(correction.dateEffetAConfirmer())) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez une date d'effet ou « date à confirmer », pas les deux.",
                    "dateEffet");
        }
        Set<String> domaines = correction.domaines() == null ? null : valider(correction.domaines());
        return travailler(compte, id, (connexion, fiche) -> {
            exigerEnAttente(fiche);
            Map<String, Object> detail = new LinkedHashMap<>();
            if (resume != null && !resume.equals(fiche.resume())) {
                maj(connexion, "resume = ?, resume_origine = 'EXPERT'", id, compte, maintenant, requete -> requete.setString(1, resume));
                detail.put("resume", Map.of("avant", valeur(fiche.resume()), "apres", resume));
            }
            if (correction.dateEffet() != null && !correction.dateEffet().equals(fiche.dateEffet())) {
                maj(connexion, "date_effet = ?, date_effet_origine = 'EXPERT'", id, compte, maintenant,
                        requete -> requete.setDate(1, Date.valueOf(correction.dateEffet())));
                detail.put("dateEffet", Map.of("avant", fiche.dateEffetLibelle(), "apres", correction.dateEffet().toString()));
            }
            if (Boolean.TRUE.equals(correction.dateEffetAConfirmer()) && fiche.dateEffet() != null) {
                maj(connexion, "date_effet = NULL, date_effet_origine = NULL", id, compte, maintenant, requete -> {
                });
                detail.put("dateEffet", Map.of("avant", fiche.dateEffet().toString(), "apres", DatesTexte.DATE_A_CONFIRMER));
            }
            if (domaines != null) {
                Set<String> avant = new LinkedHashSet<>(fiche.domaines().stream().map(FicheAnalyse.DomaineRattache::code).toList());
                if (!avant.equals(domaines)) {
                    remplacerDomaines(connexion, fiche, avant, domaines);
                    detail.put("domaines", Map.of("avant", List.copyOf(avant), "apres", List.copyOf(domaines)));
                }
            }
            if (detail.isEmpty()) {
                return;
            }
            if (!detail.containsKey("resume") && !detail.containsKey("dateEffet")) {
                maj(connexion, "resume = resume", id, compte, maintenant, requete -> {
                });
            }
            preparation.historiser(connexion, id, fiche.espaceId(), "CORRECTION", detail, compte, maintenant);
        });
    }

    /** Valide une fiche en attente : elle devient visible des membres de l'espace, le texte passe en analyse. */
    public FicheAnalyse valider(UUID compte, UUID id, Instant maintenant) {
        return travailler(compte, id, (connexion, fiche) -> {
            exigerEnAttente(fiche);
            if (fiche.resume() == null) {
                throw new RegleViolee("RESUME_REQUIS", "Rédigez le résumé de la fiche avant de la valider.");
            }
            if (fiche.domaines().isEmpty()) {
                throw new RegleViolee("DOMAINE_REQUIS", "Rattachez la fiche à au moins un domaine avant de la valider.");
            }
            CycleVie.Etat suivi = CycleVie.apresValidation(fiche.etatSuivi());
            maj(connexion, "etat_validation = 'VALIDEE', etat_suivi = ?, decide_par = argos_compte_courant(), decide_le = ?", id,
                    compte, maintenant, requete -> {
                        requete.setString(1, suivi.name());
                        requete.setTimestamp(2, Timestamp.from(maintenant));
                    });
            preparation.historiser(connexion, id, fiche.espaceId(), "VALIDATION",
                    Map.of("etatSuivi", Map.of("avant", fiche.etatSuivi().name(), "apres", suivi.name())), compte, maintenant);
        });
    }

    /** Rejette une fiche en attente : elle n'est jamais diffusée. */
    public FicheAnalyse rejeter(UUID compte, UUID id, String motif, Instant maintenant) {
        String motifNettoye = motif == null ? "" : motif.strip();
        if (motifNettoye.isEmpty() || motifNettoye.length() > 500) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez le motif du rejet (500 caractères au plus).", "motif");
        }
        return travailler(compte, id, (connexion, fiche) -> {
            exigerEnAttente(fiche);
            maj(connexion, "etat_validation = 'REJETEE', decide_par = argos_compte_courant(), decide_le = ?", id, compte, maintenant,
                    requete -> requete.setTimestamp(1, Timestamp.from(maintenant)));
            preparation.historiser(connexion, id, fiche.espaceId(), "REJET", Map.of("motif", motifNettoye), compte, maintenant);
        });
    }

    /** Fait passer le texte à l'état demandé, dans l'ordre du cycle de vie (REG-06). */
    public FicheAnalyse transition(UUID compte, UUID id, CycleVie.Etat vers, Instant maintenant) {
        return travailler(compte, id, (connexion, fiche) -> {
            if (!"VALIDEE".equals(fiche.etatValidation())) {
                throw new RegleViolee("TRANSITION_REFUSEE",
                        "Transition refusée : le suivi d'un texte commence à la validation de sa fiche.");
            }
            CycleVie.Etat arrivee = CycleVie.transition(fiche.etatSuivi(), vers, fiche.demande() != null);
            maj(connexion, "etat_suivi = ?", id, compte, maintenant, requete -> requete.setString(1, arrivee.name()));
            preparation.historiser(connexion, id, fiche.espaceId(), "TRANSITION",
                    Map.of("etatSuivi", Map.of("avant", fiche.etatSuivi().name(), "apres", arrivee.name())), compte, maintenant);
        });
    }

    /**
     * Associe une demande d'évolution GitHub ou GitLab à un texte « impact produit » (REG-08). L'adresse doit
     * désigner un ticket ou une demande de fusion d'une forge configurée ; son état est lu par l'API de la
     * forge (demande inexistante : refus ; forge injoignable : lien enregistré, état non vérifié).
     */
    public FicheAnalyse associerDemande(UUID compte, UUID id, String adresse, Instant maintenant) {
        DemandesEvolution.Demande demande = demandes.reconnaitre(adresse).orElseThrow(() -> new DonneeInvalide("DEMANDE_INVALIDE",
                "Indiquez l'adresse d'un ticket ou d'une demande de fusion GitHub ou GitLab.", "adresse"));
        // Contrôle préalable, sans appel à la forge : l'état de la fiche est vérifié de nouveau dans la transaction.
        fiches.detail(compte, id).filter(FicheAnalyse::peutExpertiser).ifPresent(ExpertiseFiches::exigerImpactProduit);
        String etat;
        try {
            etat = demandes.etat(demande).orElseThrow(() -> new DonneeInvalide("DEMANDE_INTROUVABLE",
                    "Cette demande d'évolution n'existe pas ou n'est pas accessible.", "adresse"));
        } catch (ScmException erreur) {
            LOG.infof(erreur, "état de la demande %s non vérifié : %s", demande.adresse(), erreur.getMessage());
            etat = null;
        }
        String etatLu = etat;
        return travailler(compte, id, (connexion, fiche) -> {
            exigerImpactProduit(fiche);
            maj(connexion, "demande_url = ?, demande_forge = ?, demande_etat = ?", id, compte, maintenant, requete -> {
                requete.setString(1, demande.adresse());
                requete.setString(2, demande.forge().name());
                requete.setString(3, etatLu);
            });
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("avant", fiche.demande() == null ? "" : fiche.demande().adresse());
            detail.put("apres", demande.adresse());
            detail.put("forge", demande.forge().name());
            preparation.historiser(connexion, id, fiche.espaceId(), "DEMANDE_EVOLUTION", detail, compte, maintenant);
        });
    }

    // ------------------------------------------------------------------ outils

    @FunctionalInterface
    private interface Travail {
        void executer(Connection connexion, FicheAnalyse fiche) throws SQLException;
    }

    @FunctionalInterface
    private interface Parametrage {
        void appliquer(PreparedStatement requete) throws SQLException;
    }

    /** Exécute le travail sur la fiche visible de l'expert, dans l'espace de la fiche, et renvoie la fiche à jour. */
    private FicheAnalyse travailler(UUID compte, UUID id, Travail travail) {
        Objects.requireNonNull(compte);
        UUID espace = fiches.espaceDe(id).orElseThrow(() -> new Introuvable("Fiche introuvable."));
        return base.executer(new Contexte(compte, null, espace), connexion -> {
            FicheAnalyse fiche = fiches.lire(connexion, id, false).orElseThrow(() -> new AccesRefuse(MotifRefus.NON_MEMBRE));
            if (!fiche.peutExpertiser()) {
                throw new AccesRefuse(MotifRefus.ROLE_INSUFFISANT);
            }
            travail.executer(connexion, fiche);
            return fiches.lire(connexion, id, true).orElseThrow(() -> new IllegalStateException("fiche " + id + " illisible"));
        });
    }

    private static void maj(Connection connexion, String affectations, UUID id, UUID compte, Instant maintenant, Parametrage parametrage)
            throws SQLException {
        int nombreParametres = (int) affectations.chars().filter(caractere -> caractere == '?').count();
        try (PreparedStatement requete = connexion.prepareStatement("UPDATE fiche_reglementaire SET " + affectations
                + ", modifie_par = argos_compte_courant(), modifie_le = ? WHERE id = ?")) {
            parametrage.appliquer(requete);
            requete.setTimestamp(nombreParametres + 1, Timestamp.from(maintenant));
            requete.setObject(nombreParametres + 2, id);
            if (requete.executeUpdate() != 1) {
                throw new AccesRefuse(MotifRefus.ROLE_INSUFFISANT);
            }
        }
    }

    /** Remplace les domaines ; l'expert doit rester expert d'au moins un domaine retenu. */
    private static void remplacerDomaines(Connection connexion, FicheAnalyse fiche, Set<String> avant, Set<String> apres)
            throws SQLException {
        boolean expertRetenu = false;
        for (String domaine : apres) {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT EXISTS (SELECT 1 FROM domaine_metier WHERE espace_id = ? AND code = ?), argos_expert_domaine(?, ?)")) {
                requete.setObject(1, fiche.espaceId());
                requete.setString(2, domaine);
                requete.setObject(3, fiche.espaceId());
                requete.setString(4, domaine);
                try (ResultSet resultat = requete.executeQuery()) {
                    resultat.next();
                    if (!resultat.getBoolean(1)) {
                        throw new DonneeInvalide("DOMAINE_INCONNU", "Le domaine « " + domaine + " » n'existe pas dans l'espace.",
                                "domaines");
                    }
                    expertRetenu |= resultat.getBoolean(2);
                }
            }
        }
        if (!expertRetenu) {
            throw new RegleViolee("EXPERT_DU_DOMAINE_REQUIS",
                    "Conservez au moins un domaine dont vous êtes expert : la fiche sortirait sinon de votre périmètre.");
        }
        // Ajouts avant retraits : l'expert reste expert de la fiche pendant tout le remplacement (politique
        // fiche_domaine_retrait réévaluée à chaque instruction) ; chaque ligne touchée est contrôlée.
        for (String domaine : apres) {
            if (!avant.contains(domaine)) {
                try (PreparedStatement requete = connexion.prepareStatement("""
                        INSERT INTO fiche_domaine (fiche_id, espace_id, domaine, origine, confiance, motif)
                        VALUES (?, ?, ?, 'EXPERT', NULL, 'choisi par l''expert métier')
                        """)) {
                    requete.setObject(1, fiche.id());
                    requete.setObject(2, fiche.espaceId());
                    requete.setString(3, domaine);
                    exigerUneLigne(requete.executeUpdate(), domaine);
                }
            }
        }
        for (String domaine : avant) {
            if (!apres.contains(domaine)) {
                try (PreparedStatement requete = connexion.prepareStatement(
                        "DELETE FROM fiche_domaine WHERE fiche_id = ? AND domaine = ?")) {
                    requete.setObject(1, fiche.id());
                    requete.setString(2, domaine);
                    exigerUneLigne(requete.executeUpdate(), domaine);
                }
            }
        }
    }

    /** Une écriture de domaine ignorée par la base annule toute la correction (historique fidèle). */
    private static void exigerUneLigne(int lignes, String domaine) {
        if (lignes != 1) {
            LOG.warnf("domaine %s de la fiche non modifié par la base : correction annulée", domaine);
            throw new AccesRefuse(MotifRefus.ROLE_INSUFFISANT);
        }
    }

    private static Set<String> valider(List<String> domaines) {
        if (domaines.isEmpty() || domaines.size() > 20) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez de 1 à 20 domaines.", "domaines");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String domaine : domaines) {
            String codeNormalise = domaine == null ? "" : domaine.strip();
            if (!codeNormalise.matches("[a-z0-9][a-z0-9-]{0,63}")) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Code de domaine invalide.", "domaines");
            }
            codes.add(codeNormalise);
        }
        return codes;
    }

    private static void exigerImpactProduit(FicheAnalyse fiche) {
        if (!"VALIDEE".equals(fiche.etatValidation()) || (fiche.etatSuivi() != CycleVie.Etat.IMPACT_PRODUIT
                && fiche.etatSuivi() != CycleVie.Etat.SPECIFIE && fiche.etatSuivi() != CycleVie.Etat.LIVRE)) {
            throw new RegleViolee("IMPACT_PRODUIT_REQUIS",
                    "Une demande d'évolution s'associe à un texte qualifié « impact produit ».");
        }
    }

    private static void exigerEnAttente(FicheAnalyse fiche) {
        if (!"EN_ATTENTE_VALIDATION".equals(fiche.etatValidation())) {
            throw new RegleViolee("FICHE_DECIDEE", "Cette fiche a déjà été validée ou rejetée.");
        }
    }

    private static String valeur(String texte) {
        return texte == null ? "" : texte;
    }
}
