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
 * sinon.
 */
@ApplicationScoped
public class SourcesWeb {

    /** Demande de création ; {@code espaceId} n'est renseigné que pour une source d'espace. */
    public record Demande(String portee, UUID espaceId, String type, String adresse, String libelle) {
    }

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

    @ConfigProperty(name = "argos.veille-web.sources-personnelles-max", defaultValue = "50")
    int sourcesPersonnellesMax;

    /** Valide l'adresse et son contenu, enregistre la source puis intègre sa première collecte. */
    public SourceWeb creer(Demande demande, UUID compteId) {
        Demande d = valider(demande);
        boolean personnelle = SourceWeb.PERSONNELLE.equals(d.portee());
        if (personnelle) {
            verifierLimite(compteId);
        }
        RecuperateurWeb.Contenu contenu;
        String titre;
        try {
            contenu = recuperateur.recuperer(d.adresse());
            titre = SourceWeb.FLUX.equals(d.type()) ? lecteurFlux.lire(contenu.octets()).titre()
                    : lecteurPage.lire(contenu.octets(), contenu.typeContenu(), contenu.adresseFinale().toString()).titre();
        } catch (RecuperationRefusee e) {
            throw new DonneeInvalide(e.motif().name(), e.getMessage(), "adresse");
        }
        String adresse = normaliser(d.adresse());
        String libelle = d.libelle() != null ? d.libelle()
                : !titre.isBlank() ? titre : contenu.adresseFinale().getHost();
        UUID id = UUID.randomUUID();
        UUID espace = personnelle ? null : d.espaceId();
        base.executer(new Contexte(compteId, null, espace), c -> {
            if (personnelle) {
                try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                    ps.setString(1, "source_web:" + compteId);
                    ps.execute();
                }
                if (compterPersonnelles(c) >= sourcesPersonnellesMax) {
                    throw limite();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO source_web (id, portee, espace_id, proprietaire_id, type, adresse, libelle, cree_par)
                    VALUES (?, ?, ?, ?, ?, ?, ?, argos_compte_courant())
                    """)) {
                ps.setObject(1, id);
                ps.setString(2, d.portee());
                ps.setObject(3, espace);
                ps.setObject(4, personnelle ? compteId : null);
                ps.setString(5, d.type());
                ps.setString(6, adresse);
                ps.setString(7, couper(libelle, 200));
                ps.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new RegleViolee("SOURCE_EXISTANTE", "Cette adresse est déjà suivie.");
                }
                throw e;
            }
            return null;
        });
        collecte.premiereCollecte(id, contenu);
        return trouver(id, compteId).orElseThrow();
    }

    /** Sources visibles du compte : les siennes et celles des espaces dont il est membre. */
    public List<SourceWeb> visibles(UUID compteId) {
        return base.executer(new Contexte(compteId, null, null), c -> lire(c,
                "SELECT " + COLONNES + " FROM source_web ORDER BY portee, libelle, id"));
    }

    public Optional<SourceWeb> trouver(UUID id, UUID compteId) {
        return base.executer(new Contexte(compteId, null, null),
                c -> lire(c, "SELECT " + COLONNES + " FROM source_web WHERE id = ?", id)).stream().findFirst();
    }

    /** Supprime une source visible ; renvoie faux si la politique RLS l'a refusé. */
    public boolean supprimer(SourceWeb source, UUID compteId) {
        return base.executer(new Contexte(compteId, null, source.espaceId()), c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM source_web WHERE id = ?")) {
                ps.setObject(1, source.id());
                return ps.executeUpdate() == 1;
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

    private static long compterPersonnelles(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM source_web WHERE portee = 'PERSONNELLE' AND proprietaire_id = argos_compte_courant()");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static Demande valider(Demande d) {
        if (d == null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Demande vide.", "adresse");
        }
        String portee = d.portee() == null ? "" : d.portee().strip().toUpperCase(Locale.ROOT);
        if (!portee.equals(SourceWeb.ESPACE) && !portee.equals(SourceWeb.PERSONNELLE)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Portée attendue : ESPACE ou PERSONNELLE.", "portee");
        }
        if (portee.equals(SourceWeb.ESPACE) && d.espaceId() == null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Espace obligatoire pour une source d'espace.", "espaceId");
        }
        if (portee.equals(SourceWeb.PERSONNELLE) && d.espaceId() != null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Une source personnelle n'a pas d'espace.", "espaceId");
        }
        String type = d.type() == null ? SourceWeb.FLUX : d.type().strip().toUpperCase(Locale.ROOT);
        if (!type.equals(SourceWeb.FLUX) && !type.equals(SourceWeb.PAGE)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Type attendu : FLUX ou PAGE.", "type");
        }
        String libelle = d.libelle() == null || d.libelle().isBlank() ? null : d.libelle().strip();
        return new Demande(portee, d.espaceId(), type, d.adresse(), libelle);
    }

    private static String normaliser(String adresse) {
        return java.net.URI.create(adresse.strip()).normalize().toString();
    }

    private static List<SourceWeb> lire(Connection c, String sql, Object... parametres) throws SQLException {
        List<SourceWeb> sources = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < parametres.length; i++) {
                ps.setObject(i + 1, parametres[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sources.add(new SourceWeb(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                            rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getString(8), rs.getInt(9), rs.getString(10), instant(rs, 11), instant(rs, 12),
                            instant(rs, 13), instant(rs, 14)));
                }
            }
        }
        return sources;
    }

    private static Instant instant(ResultSet rs, int colonne) throws SQLException {
        OffsetDateTime v = rs.getObject(colonne, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }

    private static String couper(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
