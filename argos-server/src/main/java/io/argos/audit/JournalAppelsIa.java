package io.argos.audit;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Journal des appels et des refus de la passerelle IA (IAG-01, changement {@code encadrer-appels-ia}).
 *
 * <p>Chaque entrée enregistre la tâche, la version du prompt, le fournisseur et le modèle, l'espace
 * imputé, l'issue, les jetons, le coût, l'empreinte des entrées, le nombre de données personnelles
 * masquées et la durée. Ni les entrées ni la sortie du modèle ne sont recopiées (décision D2).
 * L'écriture se fait dans sa propre transaction, sous l'utilisateur de connexion : le rôle applicatif
 * {@code argos_app} n'a aucun droit sur la table (migration V13).
 */
@ApplicationScoped
public class JournalAppelsIa {

    /** Appel ou refus à journaliser. {@code issue} : valeur admise par la contrainte de la table. */
    public record Entree(String tache, int versionPrompt, String fournisseur, String modele, UUID espaceId,
            String issue, int jetonsEntree, int jetonsSortie, BigDecimal coutEur, String empreinteEntrees,
            int donneesMasquees, long dureeMs) {
    }

    private static final Logger LOG = Logger.getLogger(JournalAppelsIa.class);
    private static final int LONGUEUR_MAX = 100;
    private static final int LONGUEUR_MAX_MODELE = 200;

    @Inject
    DataSource dataSource;

    public void enregistrer(Entree e) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO ia_appel (tache, version_prompt, fournisseur, modele, espace_id, issue, jetons_entree,
                                              jetons_sortie, cout_eur, empreinte_entrees, donnees_masquees, duree_ms)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            ps.setString(1, borne(e.tache(), LONGUEUR_MAX));
            ps.setInt(2, e.versionPrompt());
            ps.setString(3, borne(e.fournisseur(), 32));
            ps.setString(4, borne(e.modele(), LONGUEUR_MAX_MODELE));
            ps.setObject(5, e.espaceId());
            ps.setString(6, e.issue());
            ps.setInt(7, Math.max(e.jetonsEntree(), 0));
            ps.setInt(8, Math.max(e.jetonsSortie(), 0));
            ps.setBigDecimal(9, e.coutEur() == null ? BigDecimal.ZERO : e.coutEur());
            ps.setString(10, e.empreinteEntrees());
            ps.setInt(11, Math.max(e.donneesMasquees(), 0));
            ps.setInt(12, (int) Math.min(Math.max(e.dureeMs(), 0), Integer.MAX_VALUE));
            ps.executeUpdate();
        } catch (SQLException ex) {
            // La demande a déjà été traitée ; l'échec de sa trace reste visible dans les journaux.
            LOG.errorf(ex, "Appel IA non journalisé : %s v%d %s", e.tache(), e.versionPrompt(), e.issue());
        }
    }

    private static String borne(String valeur, int max) {
        return valeur == null ? "" : valeur.length() > max ? valeur.substring(0, max) : valeur;
    }
}
