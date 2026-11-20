package io.argos.regintel;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.catalog.dictionnaire.DictionnaireMetier;
import io.argos.catalog.dictionnaire.DictionnaireMetier.DictionnaireEspace;

/**
 * Qualification des textes nouvellement collectés et préparation de leurs fiches, espace par espace
 * (REG-02, REG-03, REG-04 ; DC-1). Traitement de la plateforme, sous l'utilisateur de connexion.
 *
 * <p>Pour chaque espace doté d'un dictionnaire : rattachement déterministe par les règles
 * ({@link Qualification}) ; à défaut, et seulement pour les natures de texte configurées, proposition
 * d'un domaine par l'IA, marquée comme telle avec sa confiance (REG-02-S2), dans la limite de
 * {@code argos.reglementaire.ia.propositions-max} propositions par espace et par collecte. Un texte
 * rattaché à au moins un domaine reçoit une fiche en attente de validation : articles modifiés et date
 * d'effet extraits du texte, liens officiels, résumé demandé à la passerelle IA et marqué « produit par
 * l'IA » (REG-04-S1). Sans IA, la fiche est préparée sans résumé, que l'expert rédige.
 */
@ApplicationScoped
public class PreparationFiches {

    private static final Logger LOG = Logger.getLogger(PreparationFiches.class);

    @Inject
    TextesOfficiels textes;

    @Inject
    DictionnaireMetier dictionnaire;

    @Inject
    AnalyseIa analyseIa;

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "argos.reglementaire.ia.propositions-max")
    int propositionsMax;

    @ConfigProperty(name = "argos.reglementaire.ia.natures")
    Set<String> naturesIa;

    /** Prépare les fiches des textes donnés ; renvoie le nombre de fiches créées. */
    public int preparer(Collection<UUID> idsTextes, Instant maintenant) {
        if (idsTextes.isEmpty()) {
            return 0;
        }
        List<TexteOfficiel> lus = textes.lire(idsTextes);
        List<DictionnaireEspace> dictionnaires = dictionnaire.dictionnaires();
        int creees = 0;
        for (DictionnaireEspace dictionnaire : dictionnaires) {
            int propositions = 0;
            for (TexteOfficiel texte : lus) {
                try {
                    List<Domaine> domaines = new ArrayList<>();
                    for (Qualification.Rattachement rattachement : Qualification.qualifier(texte, dictionnaire)) {
                        domaines.add(new Domaine(rattachement.domaine(), "REGLE", null, rattachement.motif()));
                    }
                    if (domaines.isEmpty() && propositions < propositionsMax && natureEligible(texte)
                            && !existe(texte.id(), dictionnaire.espaceId())) {
                        propositions++;
                        Optional<AnalyseIa.Proposition> propositionIa = analyseIa.proposerDomaine(dictionnaire.espaceId(), texte,
                                dictionnaire.domaines());
                        propositionIa.ifPresent(proposition -> domaines.add(new Domaine(proposition.domaine(), "IA",
                                proposition.confiance(), "proposé par l'IA : " + borne(proposition.justification(), 450))));
                    }
                    if (!domaines.isEmpty() && preparer(texte, dictionnaire.espaceId(), domaines, maintenant)) {
                        creees++;
                    }
                } catch (RuntimeException erreur) {
                    LOG.errorf(erreur, "préparation de la fiche du texte %s pour l'espace %s en échec", texte.identifiant(),
                            dictionnaire.espaceId());
                }
            }
        }
        return creees;
    }

    private boolean natureEligible(TexteOfficiel texte) {
        Set<String> natures = naturesIa.stream().map(nature -> nature.strip().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        return texte.nature() != null && natures.contains(texte.nature().toUpperCase(Locale.ROOT));
    }

    record Domaine(String code, String origine, Integer confiance, String motif) {
    }

    /** Crée la fiche du texte pour l'espace si elle n'existe pas ; vrai si elle vient d'être créée. */
    boolean preparer(TexteOfficiel texte, UUID espace, List<Domaine> domaines, Instant maintenant) {
        if (existe(texte.id(), espace)) {
            return false;
        }
        Optional<AnalyseIa.Fiche> produite = analyseIa.preparer(espace, texte);
        String resume = produite.map(AnalyseIa.Fiche::resume).orElse(null);
        UUID id = UUID.randomUUID();
        try (Connection connexion = dataSource.getConnection()) {
            connexion.setAutoCommit(false);
            try {
                try (PreparedStatement requete = connexion.prepareStatement("""
                        INSERT INTO fiche_reglementaire (id, texte_id, espace_id, resume, resume_origine, date_effet,
                            date_effet_origine, date_effet_ia, articles_modifies, cree_le, modifie_le)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (texte_id, espace_id) DO NOTHING
                        """)) {
                    requete.setObject(1, id);
                    requete.setObject(2, texte.id());
                    requete.setObject(3, espace);
                    requete.setString(4, resume);
                    requete.setString(5, resume == null ? "AUCUN" : "IA");
                    requete.setDate(6, texte.dateEffet() == null ? null : Date.valueOf(texte.dateEffet()));
                    requete.setString(7, texte.dateEffet() == null ? null : "TEXTE");
                    requete.setDate(8, produite.map(ficheIa -> Date.valueOf(ficheIa.dateEffet())).orElse(null));
                    requete.setArray(9, connexion.createArrayOf("varchar", texte.articlesModifies().toArray()));
                    requete.setTimestamp(10, Timestamp.from(maintenant));
                    requete.setTimestamp(11, Timestamp.from(maintenant));
                    if (requete.executeUpdate() == 0) {
                        connexion.rollback();
                        return false;
                    }
                }
                try (PreparedStatement requete = connexion.prepareStatement("""
                        INSERT INTO fiche_domaine (fiche_id, espace_id, domaine, origine, confiance, motif)
                        VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """)) {
                    for (Domaine domaine : domaines) {
                        requete.setObject(1, id);
                        requete.setObject(2, espace);
                        requete.setString(3, domaine.code());
                        requete.setString(4, domaine.origine());
                        if (domaine.confiance() == null) {
                            requete.setNull(5, java.sql.Types.INTEGER);
                        } else {
                            requete.setInt(5, domaine.confiance());
                        }
                        requete.setString(6, borne(domaine.motif(), 500));
                        requete.addBatch();
                    }
                    requete.executeBatch();
                }
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("resume", resume == null ? "à rédiger par l'expert" : "produit par l'IA");
                detail.put("dateEffet", texte.dateEffet() == null ? DatesTexte.DATE_A_CONFIRMER : texte.dateEffet().toString());
                detail.put("domaines", domaines.stream().map(domaine -> {
                    Map<String, Object> description = new HashMap<>();
                    description.put("code", domaine.code());
                    description.put("origine", domaine.origine());
                    if (domaine.confiance() != null) {
                        description.put("confiance", domaine.confiance());
                    }
                    return description;
                }).toList());
                historiser(connexion, id, espace, "PREPARATION", detail, null, maintenant);
                connexion.commit();
                return true;
            } catch (SQLException | RuntimeException erreur) {
                connexion.rollback();
                throw erreur;
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("préparation de la fiche impossible", erreur);
        }
    }

    private boolean existe(UUID texte, UUID espace) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT 1 FROM fiche_reglementaire WHERE texte_id = ? AND espace_id = ?")) {
            requete.setObject(1, texte);
            requete.setObject(2, espace);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des fiches impossible", erreur);
        }
    }

    void historiser(Connection connexion, UUID fiche, UUID espace, String action, Map<String, Object> detail, UUID auteur,
            Instant moment) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO fiche_historique (id, fiche_id, espace_id, action, detail, auteur, le)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                """)) {
            requete.setObject(1, UUID.randomUUID());
            requete.setObject(2, fiche);
            requete.setObject(3, espace);
            requete.setString(4, action);
            requete.setString(5, json.writeValueAsString(detail));
            requete.setObject(6, auteur);
            requete.setTimestamp(7, Timestamp.from(moment));
            requete.executeUpdate();
        } catch (JsonProcessingException serialisation) {
            throw new IllegalStateException(serialisation);
        }
    }

    private static String borne(String texte, int max) {
        if (texte == null) {
            return "";
        }
        return texte.length() <= max ? texte : texte.substring(0, max);
    }
}
