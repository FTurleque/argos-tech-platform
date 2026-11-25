package io.argos.signal.opportunite;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;

/**
 * Lecture des opportunités collectées d'un profil. Données publiques de la plateforme, lues sous
 * l'utilisateur de connexion comme les signaux ; seules les opportunités des sources dont les
 * conditions d'usage sont enregistrées sont lues (ART-05).
 */
@ApplicationScoped
public class Opportunites {

    private static final String SELECT = """
            SELECT o.id, o.source_id, s.libelle, o.rubrique, o.titre, o.organisme, o.criteres, o.lien, o.disciplines,
                   o.territoires, o.date_limite, o.recu_le
            FROM opportunite o JOIN source_profil s ON s.id = o.source_id
            WHERE s.conditions_verifiees_le IS NOT NULL AND s.profil = ?
            """;

    @Inject
    Jdbc jdbc;

    /**
     * Opportunités du profil dont une discipline figure parmi celles données et dont la date limite
     * est absente ou postérieure ou égale à {@code aPartirDu} ; présélection large, la décision revient
     * à la stratégie de rapprochement.
     */
    public List<Opportunite> candidates(String profil, List<String> disciplines, LocalDate aPartirDu) {
        return lire(SELECT + " AND o.disciplines && ? AND (o.date_limite IS NULL OR o.date_limite >= ?)"
                + " ORDER BY o.date_limite NULLS LAST, o.titre, o.id", profil, disciplines.toArray(new String[0]), aPartirDu);
    }

    public Optional<Opportunite> trouver(String profil, UUID id) {
        return lire(SELECT + " AND o.id = ?", profil, null, id).stream().findFirst();
    }

    private List<Opportunite> lire(String sql, String profil, String[] disciplines, Object dernier) {
        return jdbc.lister(sql, requete -> {
            int position = 1;
            requete.setString(position++, profil);
            if (disciplines != null) {
                requete.setArray(position++, requete.getConnection().createArrayOf("varchar", disciplines));
            }
            requete.setObject(position, dernier);
        }, lignes -> new Opportunite(lignes.getObject(1, UUID.class), lignes.getObject(2, UUID.class),
                lignes.getString(3), lignes.getString(4), lignes.getString(5), lignes.getString(6), lignes.getString(7),
                lignes.getString(8), SourcesProfil.tableau(lignes.getArray(9)), SourcesProfil.tableau(lignes.getArray(10)),
                lignes.getObject(11, LocalDate.class), Conversions.instant(lignes, 12)));
    }
}
