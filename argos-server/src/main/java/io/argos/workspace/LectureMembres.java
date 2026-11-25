package io.argos.workspace;

import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.Jdbc;

/**
 * Lectures des appartenances publiées par {@code workspace}, propriétaire des tables {@code appartenance} et
 * {@code compte} : les autres modules demandent ici le rôle d'un compte dans un espace au lieu de lire ces tables.
 */
@ApplicationScoped
public class LectureMembres {

    /** Compte contributeur ou administrateur d'un espace, et son e-mail éventuel. */
    public record Contributeur(UUID compteId, String email) {
    }

    @Inject
    Jdbc jdbc;

    /** Vrai si le compte est {@code CONTRIBUTEUR} ou {@code ADMINISTRATEUR} de l'espace, lu sur la connexion donnée. */
    public boolean contributeurOuAdministrateur(Connection connexion, UUID espaceId, UUID compteId) {
        return Jdbc.unique(connexion, """
                SELECT 1 FROM appartenance
                WHERE espace_id = ? AND compte_id = ? AND role IN ('CONTRIBUTEUR', 'ADMINISTRATEUR')
                """, Jdbc.parametres(espaceId, compteId), resultat -> resultat.getInt(1)).isPresent();
    }

    /** Parmi les comptes donnés, ceux qui sont encore contributeurs ou administrateurs de l'espace, par compte. */
    public List<Contributeur> contributeurs(UUID espaceId, Collection<UUID> comptes) {
        if (comptes.isEmpty()) {
            return List.of();
        }
        return jdbc.lister("""
                SELECT co.id, co.email FROM appartenance a JOIN compte co ON co.id = a.compte_id
                WHERE a.espace_id = ? AND a.compte_id = ANY (?) AND a.role IN ('CONTRIBUTEUR', 'ADMINISTRATEUR')
                ORDER BY co.id
                """, requete -> {
                    requete.setObject(1, espaceId);
                    requete.setArray(2, requete.getConnection().createArrayOf("uuid", comptes.toArray()));
                }, resultat -> new Contributeur(resultat.getObject(1, UUID.class), resultat.getString(2)));
    }
}
