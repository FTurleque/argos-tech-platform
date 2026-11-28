package io.argos.impact;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.commun.ContexteBase;
import io.argos.commun.Jdbc;
import io.argos.signal.recherche.RechercheVeille;
import io.argos.signal.recherche.RechercheVeille.Element;
import io.argos.signal.recherche.RechercheVeille.ProjetVise;
import io.argos.signal.recherche.RechercheVeille.Resultats;

/**
 * Projets visibles concernés par les éléments d'une recherche de veille (RCH-01, {@link RechercheVeille}) : projets
 * touchés par un impact d'avis ou par un rapprochement d'actualité, limités aux espaces de l'appelant et lus sous la
 * RLS avec son compte ({@link ContexteBase}). Le module {@code signal} trouve les éléments ; ce module, propriétaire
 * des impacts et des rapprochements, les complète.
 */
@ApplicationScoped
public class ProjetsConcernes {

    private static final String UUID_SQL = "uuid";

    /** Projets visibles touchés par les avis : identifiants des avis, espaces de l'appelant. */
    private static final String PROJETS_DES_SIGNAUX = """
            SELECT DISTINCT i.signal_id, p.id, p.name FROM project_impact i JOIN project p ON p.id = i.project_id
            WHERE i.signal_id = ANY (?) AND i.workspace_id = ANY (?) ORDER BY p.name, p.id
            """;

    /** Projets visibles touchés par les actualités : identifiants des actualités, espaces de l'appelant. */
    private static final String PROJETS_DES_ACTUALITES = """
            SELECT DISTINCT ai.actualite_id, p.id, p.name FROM actualite_impact ai JOIN project p ON p.id = ai.project_id
            WHERE ai.actualite_id = ANY (?) AND ai.workspace_id = ANY (?) ORDER BY p.name, p.id
            """;

    @Inject
    DataSource dataSource;

    /** Les résultats, chaque élément complété des projets des espaces {@code espaces} qu'il concerne. */
    @Transactional
    public Resultats completer(Resultats resultats, UUID compteId, Set<UUID> espaces) {
        if (resultats.elements().isEmpty() || espaces.isEmpty()) {
            return resultats;
        }
        List<UUID> signaux = resultats.elements().stream()
                .filter(element -> RechercheVeille.AVIS_SECURITE.equals(element.type())).map(Element::id).toList();
        List<UUID> actualites = resultats.elements().stream()
                .filter(element -> !RechercheVeille.AVIS_SECURITE.equals(element.type())).map(Element::id).toList();
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(compteId));
            Map<UUID, List<ProjetVise>> projets = new LinkedHashMap<>();
            if (!signaux.isEmpty()) {
                projets.putAll(projets(connexion, PROJETS_DES_SIGNAUX, signaux, espaces));
            }
            if (!actualites.isEmpty()) {
                projets.putAll(projets(connexion, PROJETS_DES_ACTUALITES, actualites, espaces));
            }
            List<Element> completes = resultats.elements().stream()
                    .map(element -> element.avecProjets(projets.getOrDefault(element.id(), List.of()))).toList();
            return new Resultats(completes, resultats.page(), resultats.taille(), resultats.total(),
                    resultats.totalPlafonne(), resultats.message());
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des projets concernés impossible", erreur);
        }
    }

    private static Map<UUID, List<ProjetVise>> projets(Connection connexion, String sql, List<UUID> ids, Set<UUID> espaces) {
        Map<UUID, List<ProjetVise>> projets = new LinkedHashMap<>();
        Jdbc.lister(connexion, sql, Jdbc.parametres(new Jdbc.Tableau(UUID_SQL, ids), new Jdbc.Tableau(UUID_SQL, espaces)),
                resultat -> Map.entry(resultat.getObject(1, UUID.class),
                        new ProjetVise(resultat.getObject(2, UUID.class), resultat.getString(3))))
                .forEach(ligne -> projets.computeIfAbsent(ligne.getKey(), cle -> new ArrayList<>()).add(ligne.getValue()));
        return projets;
    }
}
