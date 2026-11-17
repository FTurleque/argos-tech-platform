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
    AnalyseIa ia;

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
        for (DictionnaireEspace d : dictionnaires) {
            int propositions = 0;
            for (TexteOfficiel t : lus) {
                try {
                    List<Domaine> domaines = new ArrayList<>();
                    for (Qualification.Rattachement r : Qualification.qualifier(t, d)) {
                        domaines.add(new Domaine(r.domaine(), "REGLE", null, r.motif()));
                    }
                    if (domaines.isEmpty() && propositions < propositionsMax && natureEligible(t) && !existe(t.id(), d.espaceId())) {
                        propositions++;
                        Optional<AnalyseIa.Proposition> p = ia.proposerDomaine(d.espaceId(), t, d.domaines());
                        p.ifPresent(x -> domaines.add(new Domaine(x.domaine(), "IA", x.confiance(),
                                "proposé par l'IA : " + borne(x.justification(), 450))));
                    }
                    if (!domaines.isEmpty() && preparer(t, d.espaceId(), domaines, maintenant)) {
                        creees++;
                    }
                } catch (RuntimeException e) {
                    LOG.errorf(e, "préparation de la fiche du texte %s pour l'espace %s en échec", t.identifiant(), d.espaceId());
                }
            }
        }
        return creees;
    }

    private boolean natureEligible(TexteOfficiel t) {
        Set<String> natures = naturesIa.stream().map(n -> n.strip().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        return t.nature() != null && natures.contains(t.nature().toUpperCase(Locale.ROOT));
    }

    record Domaine(String code, String origine, Integer confiance, String motif) {
    }

    /** Crée la fiche du texte pour l'espace si elle n'existe pas ; vrai si elle vient d'être créée. */
    boolean preparer(TexteOfficiel t, UUID espace, List<Domaine> domaines, Instant maintenant) {
        if (existe(t.id(), espace)) {
            return false;
        }
        Optional<AnalyseIa.Fiche> produite = ia.preparer(espace, t);
        String resume = produite.map(AnalyseIa.Fiche::resume).orElse(null);
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO fiche_reglementaire (id, texte_id, espace_id, resume, resume_origine, date_effet,
                            date_effet_origine, date_effet_ia, articles_modifies, cree_le, modifie_le)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (texte_id, espace_id) DO NOTHING
                        """)) {
                    ps.setObject(1, id);
                    ps.setObject(2, t.id());
                    ps.setObject(3, espace);
                    ps.setString(4, resume);
                    ps.setString(5, resume == null ? "AUCUN" : "IA");
                    ps.setDate(6, t.dateEffet() == null ? null : Date.valueOf(t.dateEffet()));
                    ps.setString(7, t.dateEffet() == null ? null : "TEXTE");
                    ps.setDate(8, produite.map(f -> Date.valueOf(f.dateEffet())).orElse(null));
                    ps.setArray(9, c.createArrayOf("varchar", t.articlesModifies().toArray()));
                    ps.setTimestamp(10, Timestamp.from(maintenant));
                    ps.setTimestamp(11, Timestamp.from(maintenant));
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return false;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO fiche_domaine (fiche_id, espace_id, domaine, origine, confiance, motif)
                        VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """)) {
                    for (Domaine d : domaines) {
                        ps.setObject(1, id);
                        ps.setObject(2, espace);
                        ps.setString(3, d.code());
                        ps.setString(4, d.origine());
                        if (d.confiance() == null) {
                            ps.setNull(5, java.sql.Types.INTEGER);
                        } else {
                            ps.setInt(5, d.confiance());
                        }
                        ps.setString(6, borne(d.motif(), 500));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("resume", resume == null ? "à rédiger par l'expert" : "produit par l'IA");
                detail.put("dateEffet", t.dateEffet() == null ? DatesTexte.DATE_A_CONFIRMER : t.dateEffet().toString());
                detail.put("domaines", domaines.stream().map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("code", d.code());
                    m.put("origine", d.origine());
                    if (d.confiance() != null) {
                        m.put("confiance", d.confiance());
                    }
                    return m;
                }).toList());
                historiser(c, id, espace, "PREPARATION", detail, null, maintenant);
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("préparation de la fiche impossible", e);
        }
    }

    private boolean existe(UUID texte, UUID espace) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM fiche_reglementaire WHERE texte_id = ? AND espace_id = ?")) {
            ps.setObject(1, texte);
            ps.setObject(2, espace);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des fiches impossible", e);
        }
    }

    void historiser(Connection c, UUID fiche, UUID espace, String action, Map<String, Object> detail, UUID auteur,
            Instant le) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO fiche_historique (id, fiche_id, espace_id, action, detail, auteur, le)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, fiche);
            ps.setObject(3, espace);
            ps.setString(4, action);
            ps.setString(5, json.writeValueAsString(detail));
            ps.setObject(6, auteur);
            ps.setTimestamp(7, Timestamp.from(le));
            ps.executeUpdate();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String borne(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
