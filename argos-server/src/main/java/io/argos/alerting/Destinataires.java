package io.argos.alerting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.Jdbc;

/**
 * Destinataires possibles d'une alerte : membres des espaces des impacts, quel que soit leur rôle.
 * Lecture technique, hors rôle applicatif ; elle sert seulement à savoir pour qui évaluer les règles.
 * Le contenu de chaque alerte est ensuite relu sous le contexte du destinataire (RLS, ACC-04).
 */
@ApplicationScoped
public class Destinataires {

    /** Compte membre d'au moins un espace concerné, et son e-mail éventuel. */
    public record Destinataire(UUID compteId, String email) {
    }

    @Inject
    Jdbc jdbc;

    /** Membres d'au moins un des espaces, une fois chacun. */
    public List<Destinataire> membres(Set<UUID> espaces) {
        if (espaces.isEmpty()) {
            return new ArrayList<>();
        }
        return lire("""
                SELECT DISTINCT c.id, c.email FROM appartenance a JOIN compte c ON c.id = a.compte_id
                WHERE a.espace_id = ANY (?) ORDER BY c.id
                """, requete -> requete.setArray(1, requete.getConnection().createArrayOf("uuid", espaces.toArray())));
    }

    /**
     * Membres de l'espace dont les préférences enregistrées activent le profil donné, avec leur e-mail.
     * Les préférences n'accordent aucun droit (PRF-05) : elles désignent seulement, parmi les membres, ceux
     * qui suivent ce profil.
     */
    public List<Destinataire> membresAvecProfil(UUID espace, String profil) {
        return lire("""
                SELECT c.id, c.email FROM appartenance a JOIN compte c ON c.id = a.compte_id
                JOIN workspace_preference_utilisateur p ON p.compte_id = c.id
                WHERE a.espace_id = ? AND ? = ANY (p.profils_actifs) ORDER BY c.id
                """, Jdbc.parametres(espace, profil));
    }

    /** Destinataires (compte, e-mail) lus par la requête donnée. */
    private List<Destinataire> lire(String sql, Jdbc.Liaison liaison) {
        return jdbc.lister(sql, liaison,
                resultat -> new Destinataire(resultat.getObject(1, UUID.class), resultat.getString(2)));
    }

    /** Espaces dont le compte est membre, relus à chaque évaluation : une révocation s'applique aussitôt. */
    public Set<UUID> espacesDe(UUID compteId) {
        return new HashSet<>(jdbc.lister("SELECT espace_id FROM appartenance WHERE compte_id = ?",
                Jdbc.parametres(compteId), resultat -> resultat.getObject(1, UUID.class)));
    }
}
