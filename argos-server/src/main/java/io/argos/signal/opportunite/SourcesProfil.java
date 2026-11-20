package io.argos.signal.opportunite;

import java.net.URI;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ListeObjets;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.catalog.profil.Rubrique;
import io.argos.ingestion.web.GardeAdresses;
import io.argos.ingestion.web.RecuperationRefusee;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.RegleViolee;

/**
 * Registre des sources d'un profil de veille (ART-05, Q-1) : déclaration par l'administrateur
 * plateforme, puis enregistrement des conditions d'usage vérifiées. Ce service ne décide d'aucune
 * autorisation : la couche d'exposition réserve ces opérations à l'administrateur plateforme.
 *
 * <p>Une source de type {@code DATA_CULTURE} doit désigner un hôte admis
 * ({@code argos.profil-artiste.data-culture.hotes}, data.culture.gouv.fr en exploitation). Toute adresse
 * est contrôlée syntaxiquement ici (http ou https, sans identifiants) puis, à chaque collecte, par le
 * garde des adresses de la veille web (réseaux privés refusés, redirections vérifiées). Aucune requête
 * n'est émise à la déclaration.
 *
 * <p>Les sources sont des données de la plateforme, sans espace : elles sont lues et écrites sous
 * l'utilisateur de connexion, comme les signaux.
 */
@ApplicationScoped
public class SourcesProfil {

    /** Déclaration d'une source. */
    public record Declaration(String profil, String type, String adresse, String libelle, String rubrique,
            List<String> disciplines, List<String> territoires) {
    }

    /** Conditions d'usage vérifiées par l'administrateur plateforme. */
    public record Conditions(String licence, String conditionsUsage, String conditionsUrl) {
    }

    static final String COLONNES = """
            id, profil, type, adresse, libelle, rubrique, disciplines, territoires, licence, conditions_usage,
            conditions_url, conditions_verifiees_par, conditions_verifiees_le, etat, derniere_erreur,
            derniere_collecte_le, cree_le""";

    @Inject
    DataSource dataSource;

    @Inject
    CatalogueProfils catalogue;

    @ConfigProperty(name = "argos.profil-artiste.data-culture.hotes")
    List<String> hotesDataCulture;

    /** Déclare une source ; son état est {@code CONDITIONS_A_VERIFIER} jusqu'à l'enregistrement de ses conditions. */
    public SourceProfil declarer(Declaration declaration, UUID compteId) {
        if (declaration == null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Déclaration vide.", "adresse");
        }
        ManifesteProfil profil = catalogue.trouver(declaration.profil())
                .orElseThrow(() -> new DonneeInvalide("PROFIL_INCONNU", "Profil absent du catalogue.", "profil"));
        String type = declaration.type() == null ? "" : declaration.type().strip().toUpperCase(Locale.ROOT);
        if (!SourceProfil.DATA_CULTURE.equals(type) && !SourceProfil.FLUX.equals(type)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Type attendu : DATA_CULTURE ou FLUX.", "type");
        }
        URI adresse = analyser(declaration.adresse());
        if (SourceProfil.DATA_CULTURE.equals(type) && !hoteDataCulture(adresse)) {
            throw new DonneeInvalide("HOTE_NON_ADMIS",
                    "Une source DATA_CULTURE doit désigner l'un des hôtes admis : " + hotesDataCulture + ".", "adresse");
        }
        String libelle = declaration.libelle() == null || declaration.libelle().isBlank() ? adresse.getHost()
                : declaration.libelle().strip();
        if (libelle.length() > 200) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Libellé limité à 200 caractères.", "libelle");
        }
        String rubrique = declaration.rubrique() == null ? "" : declaration.rubrique().strip();
        if (profil.rubriques().stream().map(Rubrique::identifiant).noneMatch(rubrique::equals)) {
            throw new DonneeInvalide("RUBRIQUE_INCONNUE", "Rubriques du profil : "
                    + profil.rubriques().stream().map(Rubrique::identifiant).toList() + ".", "rubrique");
        }
        List<String> disciplines = dansListe(profil, "discipline", declaration.disciplines(), "disciplines");
        List<String> territoires = dansListe(profil, "territoire", declaration.territoires(), "territoires");
        UUID id = UUID.randomUUID();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO source_profil (id, profil, type, adresse, libelle, rubrique, disciplines, territoires, cree_par)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            requete.setObject(1, id);
            requete.setString(2, profil.identifiant());
            requete.setString(3, type);
            requete.setString(4, adresse.toString());
            requete.setString(5, libelle);
            requete.setString(6, rubrique);
            requete.setArray(7, connexion.createArrayOf("varchar", disciplines.toArray()));
            requete.setArray(8, connexion.createArrayOf("varchar", territoires.toArray()));
            requete.setObject(9, compteId);
            requete.executeUpdate();
        } catch (SQLException erreur) {
            if ("23505".equals(erreur.getSQLState())) {
                throw new RegleViolee("SOURCE_EXISTANTE", "Cette adresse est déjà déclarée pour ce profil.");
            }
            throw new IllegalStateException("déclaration de la source impossible", erreur);
        }
        return trouver(id).orElseThrow();
    }

    /**
     * Enregistre les conditions d'usage vérifiées : la source devient collectable
     * ({@code ACTIVE}) à la prochaine collecte.
     */
    public Optional<SourceProfil> enregistrerConditions(UUID id, Conditions conditions, UUID compteId, Instant maintenant) {
        if (conditions == null) {
            throw new DonneeInvalide("CONDITIONS_INCOMPLETES", "Conditions d'usage absentes.", "licence");
        }
        String licence = texte(conditions.licence(), 200, "licence", true);
        String texte = texte(conditions.conditionsUsage(), 2000, "conditionsUsage", true);
        String url = conditions.conditionsUrl() == null || conditions.conditionsUrl().isBlank() ? null
                : analyser(conditions.conditionsUrl()).toString();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                UPDATE source_profil SET licence = ?, conditions_usage = ?, conditions_url = ?,
                    conditions_verifiees_par = ?, conditions_verifiees_le = ?,
                    etat = CASE WHEN etat = 'CONDITIONS_A_VERIFIER' THEN 'ACTIVE' ELSE etat END
                WHERE id = ?
                """)) {
            requete.setString(1, licence);
            requete.setString(2, texte);
            requete.setString(3, url);
            requete.setObject(4, compteId);
            requete.setObject(5, maintenant.atOffset(ZoneOffset.UTC));
            requete.setObject(6, id);
            if (requete.executeUpdate() == 0) {
                return Optional.empty();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("enregistrement des conditions d'usage impossible", erreur);
        }
        return trouver(id);
    }

    public List<SourceProfil> lister(String profil) {
        return lire("SELECT " + COLONNES + " FROM source_profil WHERE profil = ? ORDER BY libelle, id", profil);
    }

    public Optional<SourceProfil> trouver(UUID id) {
        return lire("SELECT " + COLONNES + " FROM source_profil WHERE id = ?", id).stream().findFirst();
    }

    boolean hoteDataCulture(URI adresse) {
        String hote = adresse.getHost() == null ? "" : adresse.getHost().toLowerCase(Locale.ROOT);
        return hotesDataCulture.stream().map(hoteAdmis -> hoteAdmis.strip().toLowerCase(Locale.ROOT)).anyMatch(hote::equals);
    }

    private static URI analyser(String adresse) {
        try {
            return GardeAdresses.analyser(adresse);
        } catch (RecuperationRefusee refus) {
            throw new DonneeInvalide(refus.motif().name(), refus.message(), "adresse");
        }
    }

    private static List<String> dansListe(ManifesteProfil profil, String type, List<String> valeurs, String attribut) {
        if (valeurs == null || valeurs.isEmpty()) {
            return List.of();
        }
        List<String> admises = profil.liste(type).map(ListeObjets::identifiants).orElse(List.of());
        Set<String> distinctes = new LinkedHashSet<>();
        for (String valeur : valeurs) {
            if (valeur == null || !admises.contains(valeur.strip())) {
                throw new DonneeInvalide("VALEUR_HORS_LISTE", "Valeurs admises : " + admises + ".", attribut);
            }
            distinctes.add(valeur.strip());
        }
        return List.copyOf(distinctes);
    }

    private static String texte(String valeur, int max, String attribut, boolean obligatoire) {
        if (valeur == null || valeur.isBlank()) {
            if (obligatoire) {
                throw new DonneeInvalide("CONDITIONS_INCOMPLETES", "Champ obligatoire : " + attribut + ".", attribut);
            }
            return null;
        }
        String valeurNettoyee = valeur.strip();
        if (valeurNettoyee.length() > max) {
            throw new DonneeInvalide("DONNEE_INVALIDE", attribut + " limité à " + max + " caractères.", attribut);
        }
        return valeurNettoyee;
    }

    private List<SourceProfil> lire(String sql, Object parametre) {
        List<SourceProfil> sources = new ArrayList<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setObject(1, parametre);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    sources.add(new SourceProfil(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                            resultat.getString(4), resultat.getString(5), resultat.getString(6), tableau(resultat.getArray(7)),
                            tableau(resultat.getArray(8)), resultat.getString(9), resultat.getString(10), resultat.getString(11),
                            resultat.getObject(12, UUID.class), instant(resultat, 13), resultat.getString(14), resultat.getString(15),
                            instant(resultat, 16), instant(resultat, 17)));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des sources de profil impossible", erreur);
        }
        return sources;
    }

    static List<String> tableau(Array tableauSql) throws SQLException {
        return tableauSql == null ? List.of() : Arrays.asList((String[]) tableauSql.getArray());
    }

    static Instant instant(ResultSet resultat, int colonne) throws SQLException {
        OffsetDateTime valeur = resultat.getObject(colonne, OffsetDateTime.class);
        return valeur == null ? null : valeur.toInstant();
    }
}
