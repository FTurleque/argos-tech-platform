package io.argos.impact;

import java.sql.Connection;
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
import io.argos.commun.Jdbc;
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
            for (int position = 0; position < candidats.size(); position++) {
                liste.append(position + 1).append(". ").append(candidats.get(position).nom()).append('\n');
            }
            appels++;
            ReponseIa reponse = passerelle.demander(new DemandeIa(RegistrePrompts.ANALYSE_CORRELATION, espace.getKey(),
                    List.of(EntreeIa.publique("titre", ctx.titre()),
                            EntreeIa.publique("paquets", String.join(", ", ctx.paquets())),
                            EntreeIa.interne("projets", liste.toString())),
                    List.of()));
            if (reponse.issue() == ReponseIa.Issue.INDISPONIBLE) {
                indisponibles++;
                continue;
            }
            if (!reponse.produit() || reponse.champ("projets").isEmpty()) {
                continue;
            }
            for (String nom : reponse.champ("projets").split(",")) {
                int index = Integer.parseInt(nom) - 1;
                if (index < candidats.size()) {
                    suggestions.add(new Suggestion(candidats.get(index).id(), reponse.champ("justification")));
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

    private record EspaceProjet(UUID espace, UUID projet) {
    }

    private record Contexte(String titre, List<String> paquets, Map<UUID, List<Candidat>> candidats) {
    }

    /** Signal, paquets et candidats par espace, lus sous l'utilisateur de connexion (traitement système). */
    private Contexte lire(UUID signalId, List<UUID> impactIds) {
        try (Connection connexion = dataSource.getConnection()) {
            List<String> titres = Jdbc.lister(connexion, "SELECT coalesce(title, external_id) FROM signal WHERE id = ?",
                    Jdbc.parametres(signalId), resultat -> resultat.getString(1));
            String titre = titres.isEmpty() ? "" : titres.get(0);
            List<String> paquets = Jdbc.lister(connexion,
                    "SELECT package_key FROM signal_package WHERE signal_id = ? ORDER BY package_key LIMIT 20",
                    Jdbc.parametres(signalId), resultat -> resultat.getString(1));
            Set<UUID> espaces = new LinkedHashSet<>();
            Set<UUID> touches = new LinkedHashSet<>();
            Jdbc.lister(connexion, "SELECT workspace_id, project_id FROM project_impact WHERE id = ANY (?) ORDER BY workspace_id",
                    requete -> requete.setArray(1, connexion.createArrayOf("uuid", impactIds.toArray())),
                    resultat -> new EspaceProjet(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class)))
                    .forEach(ligne -> {
                        espaces.add(ligne.espace());
                        touches.add(ligne.projet());
                    });
            Map<UUID, List<Candidat>> candidats = new LinkedHashMap<>();
            for (UUID espace : espaces) {
                candidats.put(espace, Jdbc.lister(connexion,
                        "SELECT id, name FROM project WHERE workspace_id = ? AND NOT (id = ANY (?)) ORDER BY name, id LIMIT ?",
                        requete -> {
                            requete.setObject(1, espace);
                            requete.setArray(2, connexion.createArrayOf("uuid", touches.toArray()));
                            requete.setInt(3, CANDIDATS_MAX);
                        }, resultat -> new Candidat(resultat.getObject(1, UUID.class), resultat.getString(2))));
            }
            return new Contexte(titre, paquets, candidats);
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture du contexte d'analyse impossible", erreur);
        }
    }
}
