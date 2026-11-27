package io.argos.signal.opportunite;

import java.net.URI;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.catalog.profil.CatalogueProfils;
import io.argos.catalog.profil.ListeObjets;
import io.argos.catalog.profil.ManifesteProfil;
import io.argos.catalog.profil.Rubrique;
import io.argos.ingestion.web.GardeAdresses;
import io.argos.ingestion.web.RecuperationRefusee;
import io.argos.commun.Conversions;
import io.argos.commun.DonneeInvalide;
import io.argos.commun.ErreurBase;
import io.argos.commun.Jdbc;
import io.argos.commun.RegleViolee;

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

    private static final String SQLSTATE_UNICITE = "23505";

    static final String COLONNES = """
            id, profil, type, adresse, libelle, rubrique, disciplines, territoires, licence, conditions_usage,
            conditions_url, conditions_verifiees_par, conditions_verifiees_le, etat, derniere_erreur,
            derniere_collecte_le, cree_le""";

    /** Longueur maximale du libellé d'une source. */
    private static final int LIBELLE_MAX = 200;

    @Inject
    Jdbc jdbc;

    @Inject
    CatalogueProfils catalogue;

    @ConfigProperty(name = "argos.profil-artiste.data-culture.hotes")
    List<String> hotesDataCulture;

    /** Déclare une source ; son état est {@code CONDITIONS_A_VERIFIER} jusqu'à l'enregistrement de ses conditions. */
    public SourceProfil declarer(Declaration declaration, UUID compteId) {
        SourceValidee source = valider(declaration);
        UUID id = UUID.randomUUID();
        try {
            jdbc.modifier("""
                    INSERT INTO source_profil (id, profil, type, adresse, libelle, rubrique, disciplines, territoires, cree_par)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, requete -> {
                        requete.setObject(1, id);
                        requete.setString(2, source.profil().identifiant());
                        requete.setString(3, source.type());
                        requete.setString(4, source.adresse().toString());
                        requete.setString(5, source.libelle());
                        requete.setString(6, source.rubrique());
                        requete.setArray(7, requete.getConnection().createArrayOf("varchar", source.disciplines().toArray()));
                        requete.setArray(8, requete.getConnection().createArrayOf("varchar", source.territoires().toArray()));
                        requete.setObject(9, compteId);
                    });
        } catch (ErreurBase erreur) {
            if (erreur.getCause() instanceof SQLException sql && SQLSTATE_UNICITE.equals(sql.getSQLState())) {
                throw new RegleViolee("SOURCE_EXISTANTE", "Cette adresse est déjà déclarée pour ce profil.");
            }
            throw erreur;
        }
        return trouver(id).orElseThrow();
    }

    /** Déclaration validée, prête à l'enregistrement. */
    private record SourceValidee(ManifesteProfil profil, String type, URI adresse, String libelle, String rubrique,
            List<String> disciplines, List<String> territoires) {
    }

    /** Profil du catalogue, type et hôte admis, libellé borné, rubrique et objets suivis des listes du profil. */
    private SourceValidee valider(Declaration declaration) {
        if (declaration == null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Déclaration vide.", "adresse");
        }
        ManifesteProfil profil = catalogue.trouver(declaration.profil())
                .orElseThrow(() -> new DonneeInvalide("PROFIL_INCONNU", "Profil absent du catalogue.", "profil"));
        String type = typeDeclare(declaration.type());
        URI adresse = analyser(declaration.adresse());
        if (SourceProfil.DATA_CULTURE.equals(type) && !hoteDataCulture(adresse)) {
            throw new DonneeInvalide("HOTE_NON_ADMIS",
                    "Une source DATA_CULTURE doit désigner l'un des hôtes admis : " + hotesDataCulture + ".", "adresse");
        }
        return new SourceValidee(profil, type, adresse, libelle(declaration.libelle(), adresse),
                rubrique(profil, declaration.rubrique()),
                dansListe(profil, "discipline", declaration.disciplines(), "disciplines"),
                dansListe(profil, "territoire", declaration.territoires(), "territoires"));
    }

    private static String typeDeclare(String type) {
        String normalise = type == null ? "" : type.strip().toUpperCase(Locale.ROOT);
        if (!SourceProfil.DATA_CULTURE.equals(normalise) && !SourceProfil.FLUX.equals(normalise)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Type attendu : DATA_CULTURE ou FLUX.", "type");
        }
        return normalise;
    }

    /** Libellé saisi, ou à défaut l'hôte de l'adresse ; au plus {@value #LIBELLE_MAX} caractères. */
    private static String libelle(String saisi, URI adresse) {
        String libelle = saisi == null || saisi.isBlank() ? adresse.getHost() : saisi.strip();
        if (libelle.length() > LIBELLE_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Libellé limité à 200 caractères.", "libelle");
        }
        return libelle;
    }

    /** Rubrique saisie, qui doit être une rubrique du profil. */
    private static String rubrique(ManifesteProfil profil, String saisie) {
        String rubrique = saisie == null ? "" : saisie.strip();
        if (profil.rubriques().stream().map(Rubrique::identifiant).noneMatch(rubrique::equals)) {
            throw new DonneeInvalide("RUBRIQUE_INCONNUE", "Rubriques du profil : "
                    + profil.rubriques().stream().map(Rubrique::identifiant).toList() + ".", "rubrique");
        }
        return rubrique;
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
        int modifiees = jdbc.modifier("""
                UPDATE source_profil SET licence = ?, conditions_usage = ?, conditions_url = ?,
                    conditions_verifiees_par = ?, conditions_verifiees_le = ?,
                    etat = CASE WHEN etat = 'CONDITIONS_A_VERIFIER' THEN 'ACTIVE' ELSE etat END
                WHERE id = ?
                """, Jdbc.parametres(licence, texte, url, compteId, Conversions.utc(maintenant), id));
        if (modifiees == 0) {
            return Optional.empty();
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
        return jdbc.lister(sql, Jdbc.parametres(parametre), resultat -> new SourceProfil(resultat.getObject(1, UUID.class),
                resultat.getString(2), resultat.getString(3), resultat.getString(4), resultat.getString(5), resultat.getString(6),
                tableau(resultat.getArray(7)), tableau(resultat.getArray(8)), resultat.getString(9), resultat.getString(10),
                resultat.getString(11), resultat.getObject(12, UUID.class), Conversions.instant(resultat, 13),
                resultat.getString(14), resultat.getString(15), Conversions.instant(resultat, 16),
                Conversions.instant(resultat, 17)));
    }

    static List<String> tableau(Array tableauSql) throws SQLException {
        return tableauSql == null ? List.of() : Arrays.asList((String[]) tableauSql.getArray());
    }
}
