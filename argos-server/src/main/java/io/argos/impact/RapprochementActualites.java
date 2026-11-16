package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.signal.web.ActualiteRecue;
import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * Rapprochement d'une actualité web avec les projets par nom de technologie (WEB-06, ADR-0013) :
 * toujours au niveau {@code A_VERIFIER}, jamais {@code EXACT} ni {@code PROBABLE}, sans impact
 * projet ({@code project_impact}) ni alerte.
 *
 * <p>Seules les actualités citées par une source d'espace sont rapprochées, et seulement des
 * projets de cet espace : une source personnelle ne révèle rien aux autres membres
 * ([PROPOSITION] du changement {@code collecter-veille-rss-web}). Le nom d'une technologie est
 * déduit de la clé de paquet des composants inventoriés ({@link #terme(String)}) et recherché comme
 * mot entier, sans tenir compte de la casse, dans le titre et le texte de l'actualité.
 */
@ApplicationScoped
public class RapprochementActualites {

    private static final Logger LOG = Logger.getLogger(RapprochementActualites.class);

    /** Méthode enregistrée avec chaque rapprochement. */
    public static final String METHODE = "NOM_TECHNOLOGIE_V1";
    static final int TERME_MIN = 3;

    /** Rapprochement visible d'un membre de l'espace. */
    public record Rapprochement(UUID id, UUID actualiteId, UUID projetId, String projetNom, UUID espaceId,
            String technologie, String niveau, String methode, Instant creeLe) {
    }

    @Inject
    DataSource dataSource;

    void surActualiteRecue(@Observes(during = TransactionPhase.AFTER_SUCCESS) ActualiteRecue evenement) {
        if (evenement.espaceId() == null) {
            return;
        }
        try {
            QuarkusTransaction.requiringNew().run(() -> rapprocher(evenement.actualiteId(), evenement.espaceId()));
        } catch (RuntimeException e) {
            LOG.errorf(e, "rapprochement de l'actualité %s en échec", evenement.actualiteId());
        }
    }

    /** Rapproche une actualité des projets d'un espace ; renvoie le nombre de rapprochements créés. */
    public int rapprocher(UUID actualiteId, UUID espaceId) {
        try (Connection c = dataSource.getConnection()) {
            String contenu;
            try (PreparedStatement ps = c.prepareStatement("SELECT titre || ' ' || texte FROM actualite WHERE id = ?")) {
                ps.setObject(1, actualiteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return 0;
                    }
                    contenu = rs.getString(1);
                }
            }
            Map<UUID, Set<String>> termesParProjet = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT DISTINCT p.id, c.package_key FROM component c JOIN project p ON p.id = c.project_id
                    WHERE p.workspace_id = ? ORDER BY p.id, c.package_key
                    """)) {
                ps.setObject(1, espaceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID projet = rs.getObject(1, UUID.class);
                        terme(rs.getString(2)).ifPresent(t ->
                                termesParProjet.computeIfAbsent(projet, k -> new TreeSet<>()).add(t));
                    }
                }
            }
            int crees = 0;
            for (Map.Entry<UUID, Set<String>> e : termesParProjet.entrySet()) {
                for (String terme : e.getValue()) {
                    if (!mentionne(contenu, terme)) {
                        continue;
                    }
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO actualite_impact (id, actualite_id, project_id, workspace_id, technologie, niveau,
                                                          methode, cree_le)
                            VALUES (?, ?, ?, ?, ?, 'A_VERIFIER', ?, now())
                            ON CONFLICT (actualite_id, project_id, technologie) DO NOTHING
                            """)) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setObject(2, actualiteId);
                        ps.setObject(3, e.getKey());
                        ps.setObject(4, espaceId);
                        ps.setString(5, terme);
                        ps.setString(6, METHODE);
                        crees += ps.executeUpdate();
                    }
                }
            }
            return crees;
        } catch (SQLException e) {
            throw new IllegalStateException("rapprochement de l'actualité " + actualiteId + " impossible", e);
        }
    }

    /**
     * Rapprochements d'une actualité visibles du compte (RLS sous {@code argos_app} : espaces dont
     * il est membre, actualité citée par une source qu'il voit).
     */
    public List<Rapprochement> pour(UUID actualiteId, UUID compteId) {
        List<Rapprochement> resultat = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                RlsSession.account(c, compteId);
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT i.id, i.actualite_id, i.project_id, p.name, i.workspace_id, i.technologie, i.niveau, i.methode,
                               i.cree_le
                        FROM actualite_impact i JOIN project p ON p.id = i.project_id
                        WHERE i.actualite_id = ? ORDER BY p.name, i.technologie
                        """)) {
                    ps.setObject(1, actualiteId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            resultat.add(new Rapprochement(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                                    rs.getObject(3, UUID.class), rs.getString(4), rs.getObject(5, UUID.class),
                                    rs.getString(6), rs.getString(7), rs.getString(8),
                                    rs.getObject(9, OffsetDateTime.class).toInstant()));
                        }
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des rapprochements impossible", e);
        }
        return resultat;
    }

    /**
     * Nom de technologie d'une clé de paquet {@code pkg:type/[namespace/]nom} : la portée d'un paquet
     * npm à portée ({@code @angular/core} → {@code angular}), sinon le nom. Vide si le nom compte
     * moins de {@value #TERME_MIN} caractères.
     */
    static Optional<String> terme(String packageKey) {
        if (packageKey == null || !packageKey.startsWith("pkg:")) {
            return Optional.empty();
        }
        String reste = packageKey.substring(4);
        int slash = reste.indexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        String type = reste.substring(0, slash);
        String[] segments = reste.substring(slash + 1).split("/");
        String terme;
        if ("npm".equals(type) && segments.length > 1 && segments[0].startsWith("@")) {
            terme = segments[0].substring(1);
        } else {
            terme = segments[segments.length - 1];
        }
        terme = terme.strip().toLowerCase(Locale.ROOT);
        return terme.length() < TERME_MIN ? Optional.empty() : Optional.of(terme);
    }

    /** Mot entier, sans tenir compte de la casse. */
    static boolean mentionne(String contenu, String terme) {
        Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(terme) + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return contenu != null && p.matcher(contenu).find();
    }
}
