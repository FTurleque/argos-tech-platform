package io.argos.workspace.vue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.workspace.AccesRefuse;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.Introuvable;
import io.argos.workspace.MotifRefus;
import io.argos.workspace.RegleViolee;

/**
 * Vues enregistrées (RCH-04) : une recherche nommée d'un compte, visible de lui seul. Les critères
 * sont un document JSON validé par la couche d'exposition ; ce module ne les interprète pas et une vue
 * n'accorde aucun droit : elle est rejouée sous les droits courants de son auteur.
 *
 * <p>Chaque opération s'exécute sous la RLS ({@link ContexteBase}) : la politique de
 * {@code workspace_vue} (migration V11) n'expose que les vues du compte courant. La vue d'un autre
 * compte est refusée et le refus journalisé ; une vue inexistante est seulement « introuvable ». Dans
 * les deux cas, la réponse est identique (404, « Ressource introuvable. ») et ne révèle pas
 * l'existence de la vue.
 */
@ApplicationScoped
public class VuesEnregistrees {

    /** Vue enregistrée ; {@code criteres} est le document JSON des mots et des filtres. */
    public record Vue(UUID id, String nom, String criteres, Instant creeLe) {
    }

    static final int NOM_MAX = 120;
    static final int CRITERES_MAX = 20_000;

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.recherche.vues-max", defaultValue = "100")
    int vuesMax;

    /** Vues du compte, par nom. */
    public List<Vue> lister(UUID compteId) {
        return base.executer(new Contexte(compteId, null, null), connexion -> {
            List<Vue> vues = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT id, nom, criteres::text, cree_le FROM workspace_vue WHERE compte_id = ? ORDER BY nom, id")) {
                requete.setObject(1, compteId);
                try (ResultSet resultat = requete.executeQuery()) {
                    while (resultat.next()) {
                        vues.add(vue(resultat));
                    }
                }
            }
            return vues;
        });
    }

    /** Enregistre une vue ; nom unique par compte, nombre de vues borné ({@code argos.recherche.vues-max}). */
    public Vue creer(UUID compteId, String nom, String criteresJson) {
        String nomPropre = nom == null ? "" : nom.strip();
        if (nomPropre.isEmpty() || nomPropre.length() > NOM_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Le nom de la vue compte de 1 à " + NOM_MAX + " caractères.", "nom");
        }
        if (criteresJson == null || criteresJson.length() > CRITERES_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Critères de la vue absents ou trop longs.", "criteres");
        }
        UUID id = UUID.randomUUID();
        return base.executer(new Contexte(compteId, null, null), connexion -> {
            // Créations concurrentes du même compte sérialisées : le plafond ne peut pas être dépassé.
            try (PreparedStatement requete = connexion.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                requete.setString(1, "workspace_vue:" + compteId);
                requete.execute();
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT count(*), count(*) FILTER (WHERE nom = ?) FROM workspace_vue")) {
                requete.setString(1, nomPropre);
                try (ResultSet resultat = requete.executeQuery()) {
                    resultat.next();
                    if (resultat.getLong(2) > 0) {
                        throw new RegleViolee("VUE_EXISTANTE", "Vous avez déjà une vue nommée ainsi.");
                    }
                    if (resultat.getLong(1) >= vuesMax) {
                        throw new RegleViolee("LIMITE_VUES", "Nombre maximal de vues enregistrées atteint (" + vuesMax + ").");
                    }
                }
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO workspace_vue (id, compte_id, nom, criteres) VALUES (?, ?, ?, ?::jsonb)
                    RETURNING id, nom, criteres::text, cree_le
                    """)) {
                requete.setObject(1, id);
                requete.setObject(2, compteId);
                requete.setString(3, nomPropre);
                requete.setString(4, criteresJson);
                try (ResultSet resultat = requete.executeQuery()) {
                    resultat.next();
                    return vue(resultat);
                }
            } catch (SQLException erreur) {
                if ("23505".equals(erreur.getSQLState())) {
                    // Création concurrente du même nom : l'unicité est garantie par la base.
                    throw new RegleViolee("VUE_EXISTANTE", "Vous avez déjà une vue nommée ainsi.");
                }
                throw erreur;
            }
        });
    }

    /** Vue du compte ; refus journalisé si elle appartient à un autre compte, « introuvable » si elle n'existe pas. */
    public Vue trouver(UUID compteId, UUID id) {
        Optional<Vue> vue = base.executer(new Contexte(compteId, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT id, nom, criteres::text, cree_le FROM workspace_vue WHERE id = ?")) {
                requete.setObject(1, id);
                try (ResultSet resultat = requete.executeQuery()) {
                    return resultat.next() ? Optional.of(vue(resultat)) : Optional.empty();
                }
            }
        });
        return vue.orElseThrow(() -> refus(id));
    }

    /** Supprime une vue du compte ; mêmes refus que {@link #trouver}. */
    public void supprimer(UUID compteId, UUID id) {
        int supprimees = base.executer(new Contexte(compteId, null, null), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM workspace_vue WHERE id = ?")) {
                requete.setObject(1, id);
                return requete.executeUpdate();
            }
        });
        if (supprimees == 0) {
            throw refus(id);
        }
    }

    /**
     * Refus d'une vue invisible sous la RLS : l'existence est lue hors rôle applicatif pour décider s'il
     * faut journaliser ; rien de la vue n'est restitué.
     */
    private RuntimeException refus(UUID id) {
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("SELECT 1 FROM workspace_vue WHERE id = ?")) {
            requete.setObject(1, id);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? new AccesRefuse(MotifRefus.NON_MEMBRE) : new Introuvable("Ressource introuvable.");
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de la vue impossible", erreur);
        }
    }

    private static Vue vue(ResultSet resultat) throws SQLException {
        OffsetDateTime cree = resultat.getObject(4, OffsetDateTime.class);
        return new Vue(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                cree == null ? null : cree.toInstant());
    }
}
