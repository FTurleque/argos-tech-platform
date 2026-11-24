package io.argos.signal.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import io.argos.workspace.ContexteBase;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.RegleViolee;
import io.argos.workspace.ContexteBase.Contexte;

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
                try (PreparedStatement requete = connexion.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                    requete.setString(1, "source_web:" + compteId);
                    requete.execute();
                }
                if (compterPersonnelles(connexion) >= sourcesPersonnellesMax) {
                    throw limite();
                }
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO source_web (id, portee, espace_id, proprietaire_id, type, adresse, libelle, cree_par)
                    VALUES (?, ?, ?, ?, ?, ?, ?, argos_compte_courant())
                    """)) {
                requete.setObject(1, id);
                requete.setString(2, demande.portee());
                requete.setObject(3, espace);
                requete.setObject(4, personnelle ? compteId : null);
                requete.setString(5, demande.type());
                requete.setString(6, adresse);
                requete.setString(7, couper(libelle, 200));
                requete.executeUpdate();
            } catch (SQLException erreur) {
                if ("23505".equals(erreur.getSQLState())) {
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
        return base.executer(new Contexte(compteId, null, source.espaceId()), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM source_web WHERE id = ?")) {
                requete.setObject(1, source.id());
                return requete.executeUpdate() == 1;
            }
        });
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

    private static long compterPersonnelles(Connection connexion) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT count(*) FROM source_web WHERE portee = 'PERSONNELLE' AND proprietaire_id = argos_compte_courant()");
                ResultSet resultat = requete.executeQuery()) {
            resultat.next();
            return resultat.getLong(1);
        }
    }

    static Demande valider(Demande demande) {
        if (demande == null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Demande vide.", "adresse");
        }
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
        String type = demande.type() == null ? SourceWeb.FLUX : demande.type().strip().toUpperCase(Locale.ROOT);
        if (!type.equals(SourceWeb.FLUX) && !type.equals(SourceWeb.PAGE)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Type attendu : FLUX ou PAGE.", "type");
        }
        String libelle = demande.libelle() == null || demande.libelle().isBlank() ? null : demande.libelle().strip();
        return new Demande(portee, demande.espaceId(), type, demande.adresse(), libelle);
    }

    /**
     * Forme canonique de l'adresse saisie, après son contrôle syntaxique (schéma http(s), hôte,
     * absence d'identifiants, longueur) ; une adresse refusée lève {@code ADRESSE_INVALIDE}.
     */
    static String normaliser(String adresse) {
        return Deduplication.adresse(GardeAdresses.analyser(adresse).toString());
    }

    private static List<SourceWeb> lire(Connection connexion, String sql, Object... parametres) throws SQLException {
        List<SourceWeb> sources = new ArrayList<>();
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            for (int indice = 0; indice < parametres.length; indice++) {
                requete.setObject(indice + 1, parametres[indice]);
            }
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    sources.add(new SourceWeb(resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getObject(3, UUID.class),
                            resultat.getObject(4, UUID.class), resultat.getString(5), resultat.getString(6), resultat.getString(7),
                            resultat.getString(8), resultat.getInt(9), resultat.getString(10), instant(resultat, 11), instant(resultat, 12),
                            instant(resultat, 13), instant(resultat, 14)));
                }
            }
        }
        return sources;
    }

    private static Instant instant(ResultSet resultat, int colonne) throws SQLException {
        OffsetDateTime valeur = resultat.getObject(colonne, OffsetDateTime.class);
        return valeur == null ? null : valeur.toInstant();
    }

    private static String couper(String valeur, int max) {
        return valeur == null || valeur.length() <= max ? valeur : valeur.substring(0, max);
    }
}
