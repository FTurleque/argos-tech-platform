package io.argos.impact;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.aigateway.DemandeIa;
import io.argos.aigateway.EntreeIa;
import io.argos.aigateway.PasserelleIa;
import io.argos.aigateway.RegistrePrompts;
import io.argos.aigateway.ReponseIa;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.lookup.LookupIfProperty;

/**
 * Analyse complémentaire de la corrélation par la passerelle IA (tâche {@code analyse-correlation}).
 * Désactivée par défaut : elle n'est sollicitée que si l'exploitant pose
 * {@code argos.ia.analyse-correlation.active=true}, car elle transmet des noms de projets (classe
 * INTERNAL, Q-4).
 *
 * <p>Pour chaque espace qui a au moins un impact déterministe du signal, un appel imputé à cet espace
 * transmet le titre du signal et ses paquets (PUBLIC) et au plus {@value #CANDIDATS_MAX} projets du même
 * espace sans impact (noms, INTERNAL) ; aucun projet d'un autre espace n'est mêlé à l'appel. Les
 * suggestions ne citent que ces candidats, ne créent aucun impact et ne déclenchent aucune alerte
 * (COR-04). Un espace au quota épuisé est passé ; si le fournisseur est indisponible pour tous les
 * espaces, l'analyse est indisponible (COR-05).
 */
@ApplicationScoped
@DefaultBean
@LookupIfProperty(name = "argos.ia.analyse-correlation.active", stringValue = "true")
public class AnalyseComplementaireIa implements ComplementaryAnalysis {

    static final int CANDIDATS_MAX = 20;

    @Inject
    PasserelleIa passerelle;

    @Inject
    DataSource dataSource;

    @Override
    public boolean budgetAvailable() {
        return passerelle.disponible(null);
    }

    @Override
    public List<Suggestion> analyze(UUID signalId, List<UUID> deterministicImpactIds) {
        if (deterministicImpactIds.isEmpty()) {
            return List.of();
        }
        Contexte ctx = lire(signalId, deterministicImpactIds);
        List<Suggestion> suggestions = new ArrayList<>();
        int appels = 0;
        int indisponibles = 0;
        for (Map.Entry<UUID, List<Candidat>> espace : ctx.candidats().entrySet()) {
            List<Candidat> candidats = espace.getValue();
            if (candidats.isEmpty()) {
                continue;
            }
            StringBuilder liste = new StringBuilder();
            for (int i = 0; i < candidats.size(); i++) {
                liste.append(i + 1).append(". ").append(candidats.get(i).nom()).append('\n');
            }
            appels++;
            ReponseIa r = passerelle.demander(new DemandeIa(RegistrePrompts.ANALYSE_CORRELATION, espace.getKey(),
                    List.of(EntreeIa.publique("titre", ctx.titre()),
                            EntreeIa.publique("paquets", String.join(", ", ctx.paquets())),
                            EntreeIa.interne("projets", liste.toString())),
                    List.of()));
            if (r.issue() == ReponseIa.Issue.INDISPONIBLE) {
                indisponibles++;
                continue;
            }
            if (!r.produit() || r.champ("projets").isEmpty()) {
                continue;
            }
            for (String n : r.champ("projets").split(",")) {
                int index = Integer.parseInt(n) - 1;
                if (index < candidats.size()) {
                    suggestions.add(new Suggestion(candidats.get(index).id(), r.champ("justification")));
                }
            }
        }
        if (appels > 0 && indisponibles == appels) {
            throw new IllegalStateException("passerelle IA indisponible");
        }
        return List.copyOf(suggestions);
    }

    private record Candidat(UUID id, String nom) {
    }

    private record Contexte(String titre, List<String> paquets, Map<UUID, List<Candidat>> candidats) {
    }

    /** Signal, paquets et candidats par espace, lus sous l'utilisateur de connexion (traitement système). */
    private Contexte lire(UUID signalId, List<UUID> impactIds) {
        try (Connection c = dataSource.getConnection()) {
            String titre = "";
            try (PreparedStatement ps = c.prepareStatement("SELECT coalesce(title, external_id) FROM signal WHERE id = ?")) {
                ps.setObject(1, signalId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        titre = rs.getString(1);
                    }
                }
            }
            List<String> paquets = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT package_key FROM signal_package WHERE signal_id = ? ORDER BY package_key LIMIT 20")) {
                ps.setObject(1, signalId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        paquets.add(rs.getString(1));
                    }
                }
            }
            Set<UUID> espaces = new LinkedHashSet<>();
            Set<UUID> touches = new LinkedHashSet<>();
            Array ids = c.createArrayOf("uuid", impactIds.toArray());
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT workspace_id, project_id FROM project_impact WHERE id = ANY (?) ORDER BY workspace_id")) {
                ps.setArray(1, ids);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        espaces.add(rs.getObject(1, UUID.class));
                        touches.add(rs.getObject(2, UUID.class));
                    }
                }
            }
            Map<UUID, List<Candidat>> candidats = new LinkedHashMap<>();
            for (UUID espace : espaces) {
                List<Candidat> liste = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id, name FROM project WHERE workspace_id = ? AND NOT (id = ANY (?)) ORDER BY name, id LIMIT ?")) {
                    ps.setObject(1, espace);
                    ps.setArray(2, c.createArrayOf("uuid", touches.toArray()));
                    ps.setInt(3, CANDIDATS_MAX);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            liste.add(new Candidat(rs.getObject(1, UUID.class), rs.getString(2)));
                        }
                    }
                }
                candidats.put(espace, liste);
            }
            return new Contexte(titre, paquets, candidats);
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du contexte d'analyse impossible", e);
        }
    }
}
