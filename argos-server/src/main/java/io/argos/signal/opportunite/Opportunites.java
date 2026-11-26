package io.argos.signal.opportunite;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;

/**
 * Lecture des opportunités collectées d'un profil. Données publiques de la plateforme, lues sous
 * l'utilisateur de connexion comme les signaux ; seules les opportunités des sources dont les
 * conditions d'usage sont enregistrées sont lues (ART-05). Une opportunité est ouverte jusqu'à son échéance : sa date
 * limite, ou, sans date limite, sa première collecte augmentée de l'âge maximal
 * {@code argos.profil-artiste.opportunites.age-max} (ART-08).
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

    @ConfigProperty(name = "argos.profil-artiste.opportunites.age-max")
    Duration ageMax;

    @ConfigProperty(name = "argos.alerting.fuseau")
    ZoneId fuseau;

    /**
     * Opportunités ouvertes du profil dont une discipline figure parmi celles données : date limite postérieure ou
     * égale à {@code aPartirDu}, ou, sans date limite, première collecte depuis moins de l'âge maximal au début de ce
     * jour (ART-08) ; présélection large, la décision revient à la stratégie de rapprochement.
     */
    public List<Opportunite> candidates(String profil, List<String> disciplines, LocalDate aPartirDu) {
        Instant collecteeDepuis = aPartirDu.atStartOfDay(fuseau).toInstant().minus(ageMax);
        return lire(SELECT + " AND o.disciplines && ? AND (o.date_limite >= ? OR (o.date_limite IS NULL AND o.recu_le >= ?))"
                + " ORDER BY o.date_limite NULLS LAST, o.titre, o.id", profil, disciplines.toArray(new String[0]), aPartirDu,
                Conversions.utc(collecteeDepuis));
    }

    public Optional<Opportunite> trouver(String profil, UUID id) {
        return lire(SELECT + " AND o.id = ?", profil, null, id).stream().findFirst();
    }

    /** Âge maximal de restitution d'une opportunité sans date limite (ART-08). */
    public Duration ageMax() {
        return ageMax;
    }

    private List<Opportunite> lire(String sql, String profil, String[] disciplines, Object... suivants) {
        return jdbc.lister(sql, requete -> {
            int position = 1;
            requete.setString(position++, profil);
            if (disciplines != null) {
                requete.setArray(position++, requete.getConnection().createArrayOf("varchar", disciplines));
            }
            for (Object valeur : suivants) {
                requete.setObject(position++, valeur);
            }
        }, lignes -> new Opportunite(lignes.getObject(1, UUID.class), lignes.getObject(2, UUID.class),
                lignes.getString(3), lignes.getString(4), lignes.getString(5), lignes.getString(6), lignes.getString(7),
                lignes.getString(8), SourcesProfil.tableau(lignes.getArray(9)), SourcesProfil.tableau(lignes.getArray(10)),
                lignes.getObject(11, LocalDate.class), Conversions.instant(lignes, 12)));
    }
}
