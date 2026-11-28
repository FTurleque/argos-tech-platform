package io.argos.signal.web;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.ingestion.web.GardeAdresses;
import io.argos.ingestion.web.LecteurFlux;
import io.argos.ingestion.web.LecteurPage;
import io.argos.ingestion.web.RecuperateurWeb;
import io.argos.ingestion.web.RecuperationRefusee;
import io.argos.commun.ContexteBase;
import io.argos.commun.Conversions;
import io.argos.commun.DonneeInvalide;
import io.argos.commun.ErreurBase;
import io.argos.commun.Jdbc;
import io.argos.commun.RegleViolee;
import io.argos.commun.ContexteBase.Contexte;

/**
 * Sources de veille web d'un espace ou personnelles (WEB-01, WEB-02, WEB-03, Q-3).
 *
 * <p>Ce service ne décide d'aucune autorisation : la couche d'exposition vérifie qu'une source
 * d'espace est créée ou supprimée par un administrateur de l'espace. Chaque transaction s'exécute
 * sous le rôle {@code argos_app} avec le compte de l'appelant ({@link ContexteBase}) : les politiques
 * de la migration V10 refont le contrôle (source personnelle visible de son seul propriétaire,
 * source d'espace visible de ses membres et créée par ses administrateurs).
 *
 * <p>Une adresse n'est enregistrée qu'après sa récupération réussie : adresse publique, redirections
 * vérifiées, contenu qui est bien un flux (WEB-01-S2) ou une page HTML. Aucune source n'est créée
 * sinon. L'adresse est enregistrée sous sa forme canonique (schéma et hôte en minuscules, port par
 * défaut et fragment retirés) : deux écritures d'une même adresse ne contournent pas l'unicité.
 *
 * <p>La source est insérée puis sa première collecte intégrée sous son verrou de collecte, pris
 * avant l'insertion : le planificateur ne la collecte pas en même temps. Si l'intégration échoue,
 * la source reste enregistrée à l'état {@code EN_ERREUR} avec son motif et le planificateur la
 * reprend après l'attente d'un échec.
 */
@ApplicationScoped
public class SourcesWeb {

    /** Demande de création ; {@code espaceId} n'est renseigné que pour une source d'espace. */
    public record Demande(String portee, UUID espaceId, String type, String adresse, String libelle) {
    }

    private static final Logger LOG = Logger.getLogger(SourcesWeb.class);

    private static final int LIBELLE_MAX = 200;
    private static final String SQLSTATE_UNICITE = "23505";

    private static final String COLONNES = """
            id, portee, espace_id, proprietaire_id, type, adresse, libelle, etat, echecs_consecutifs, derniere_erreur,
            derniere_collecte_le, dernier_succes_le, prochaine_collecte_le, cree_le""";

    @Inject
    ContexteBase base;

    @Inject
    RecuperateurWeb recuperateur;

    @Inject
    LecteurFlux lecteurFlux;

    @Inject
    LecteurPage lecteurPage;

    @Inject
    CollecteWeb collecte;

    @Inject
    StockActualites stock;

    @ConfigProperty(name = "argos.veille-web.sources-personnelles-max", defaultValue = "50")
    int sourcesPersonnellesMax;

    /** Valide l'adresse et son contenu, enregistre la source puis intègre sa première collecte. */
    public SourceWeb creer(Demande demande, UUID compteId) {
        Demande demandeValidee = valider(demande);
        boolean personnelle = SourceWeb.PERSONNELLE.equals(demandeValidee.portee());
        if (personnelle) {
            verifierLimite(compteId);
        }
        RecuperateurWeb.Contenu contenu;
        String titre;
        String adresse;
        try {
            adresse = normaliser(demandeValidee.adresse());
            contenu = recuperateur.recuperer(adresse);
            titre = SourceWeb.FLUX.equals(demandeValidee.type()) ? lecteurFlux.lire(contenu.octets()).titre()
                    : lecteurPage.lire(contenu.octets(), contenu.typeContenu(), contenu.adresseFinale().toString()).titre();
        } catch (RecuperationRefusee refus) {
            LOG.infof(refus, "ajout de source web refusé pour le compte %s : %s (%s)", compteId, refus.motif(), refus.getMessage());
            throw new DonneeInvalide(refus.motif().name(), refus.message(), "adresse");
        }
        String libelle = demandeValidee.libelle() != null ? demandeValidee.libelle()
                : !titre.isBlank() ? titre : contenu.adresseFinale().getHost();
        UUID id = UUID.randomUUID();
        UUID espace = personnelle ? null : demandeValidee.espaceId();
        try (StockActualites.Verrou verrou = stock.verrouiller(id).orElseThrow(
                () -> new IllegalStateException("verrou de la source " + id + " indisponible"))) {
            inserer(demandeValidee, compteId, personnelle, adresse, libelle, id, espace);
            collecte.premiereCollecte(id, contenu);
        }
        return trouver(id, compteId).orElseThrow();
    }

    private void inserer(Demande demande, UUID compteId, boolean personnelle, String adresse, String libelle, UUID id,
            UUID espace) {
        base.executer(new Contexte(compteId, null, espace), connexion -> {
            if (personnelle) {
                Jdbc.unique(connexion, "SELECT pg_advisory_xact_lock(hashtext(?))", Jdbc.parametres("source_web:" + compteId),
                        resultat -> null);
                if (compterPersonnelles(connexion) >= sourcesPersonnellesMax) {
                    throw limite();
                }
            }
            try {
                Jdbc.modifier(connexion, """
                        INSERT INTO source_web (id, portee, espace_id, proprietaire_id, type, adresse, libelle, cree_par)
                        VALUES (?, ?, ?, ?, ?, ?, ?, argos_compte_courant())
                        """, Jdbc.parametres(id, demande.portee(), espace, personnelle ? compteId : null, demande.type(),
                        adresse, Conversions.couper(libelle, LIBELLE_MAX)));
            } catch (ErreurBase erreur) {
                if (erreur.getCause() instanceof SQLException sql && SQLSTATE_UNICITE.equals(sql.getSQLState())) {
                    throw new RegleViolee("SOURCE_EXISTANTE", "Cette adresse est déjà suivie.");
                }
                throw erreur;
            }
            return null;
        });
    }

    /** Sources visibles du compte : les siennes et celles des espaces dont il est membre. */
    public List<SourceWeb> visibles(UUID compteId) {
        return base.executer(new Contexte(compteId, null, null), connexion -> lire(connexion,
                "SELECT " + COLONNES + " FROM source_web ORDER BY portee, libelle, id"));
    }

    public Optional<SourceWeb> trouver(UUID id, UUID compteId) {
        return base.executer(new Contexte(compteId, null, null),
                connexion -> lire(connexion, "SELECT " + COLONNES + " FROM source_web WHERE id = ?", id)).stream().findFirst();
    }

    /** Supprime une source visible ; renvoie faux si la politique RLS l'a refusé. */
    public boolean supprimer(SourceWeb source, UUID compteId) {
        return base.executer(new Contexte(compteId, null, source.espaceId()),
                connexion -> Jdbc.modifier(connexion, "DELETE FROM source_web WHERE id = ?", Jdbc.parametres(source.id())) == 1);
    }

    private void verifierLimite(UUID compteId) {
        long nombre = base.executer(new Contexte(compteId, null, null), SourcesWeb::compterPersonnelles);
        if (nombre >= sourcesPersonnellesMax) {
            throw limite();
        }
    }

    private RegleViolee limite() {
        return new RegleViolee("LIMITE_SOURCES",
                "Nombre maximal de sources personnelles atteint (" + sourcesPersonnellesMax + ").");
    }

    private static long compterPersonnelles(Connection connexion) {
        return Jdbc.unique(connexion,
                "SELECT count(*) FROM source_web WHERE portee = 'PERSONNELLE' AND proprietaire_id = argos_compte_courant()",
                Jdbc.sansParametre(), resultat -> resultat.getLong(1)).orElse(0L);
    }

    /** Demande validée : portée et espace cohérents, type connu (flux par défaut), libellé nettoyé. */
    static Demande valider(Demande demande) {
        if (demande == null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Demande vide.", "adresse");
        }
        String portee = portee(demande);
        String type = demande.type() == null ? SourceWeb.FLUX : demande.type().strip().toUpperCase(Locale.ROOT);
        if (!type.equals(SourceWeb.FLUX) && !type.equals(SourceWeb.PAGE)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Type attendu : FLUX ou PAGE.", "type");
        }
        String libelle = demande.libelle() == null || demande.libelle().isBlank() ? null : demande.libelle().strip();
        return new Demande(portee, demande.espaceId(), type, demande.adresse(), libelle);
    }

    /** Portée normalisée : une source d'espace désigne son espace, une source personnelle n'en a pas. */
    private static String portee(Demande demande) {
        String portee = demande.portee() == null ? "" : demande.portee().strip().toUpperCase(Locale.ROOT);
        if (!portee.equals(SourceWeb.ESPACE) && !portee.equals(SourceWeb.PERSONNELLE)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Portée attendue : ESPACE ou PERSONNELLE.", "portee");
        }
        if (portee.equals(SourceWeb.ESPACE) && demande.espaceId() == null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Espace obligatoire pour une source d'espace.", "espaceId");
        }
        if (portee.equals(SourceWeb.PERSONNELLE) && demande.espaceId() != null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Une source personnelle n'a pas d'espace.", "espaceId");
        }
        return portee;
    }

    /**
     * Forme canonique de l'adresse saisie, après son contrôle syntaxique (schéma http(s), hôte,
     * absence d'identifiants, longueur) ; une adresse refusée lève {@code ADRESSE_INVALIDE}.
     */
    static String normaliser(String adresse) {
        return Deduplication.adresse(GardeAdresses.analyser(adresse).toString());
    }

    private static List<SourceWeb> lire(Connection connexion, String sql, Object... parametres) {
        return Jdbc.lister(connexion, sql, Jdbc.parametres(parametres), resultat -> new SourceWeb(resultat.getObject(1, UUID.class),
                resultat.getString(2), resultat.getObject(3, UUID.class), resultat.getObject(4, UUID.class), resultat.getString(5),
                resultat.getString(6), resultat.getString(7), resultat.getString(8), resultat.getInt(9), resultat.getString(10),
                Conversions.instant(resultat, 11), Conversions.instant(resultat, 12), Conversions.instant(resultat, 13),
                Conversions.instant(resultat, 14)));
    }
}
