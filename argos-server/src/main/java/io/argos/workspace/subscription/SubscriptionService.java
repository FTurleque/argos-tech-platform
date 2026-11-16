package io.argos.workspace.subscription;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Abonnements de veille {@code TEAM} et {@code PERSONAL} (ABO-01 à ABO-04, ABO-06).
 *
 * <p>Ce service ne lit ni n'écrit aucune donnée d'autorisation (INV-D7) : les espaces dont
 * l'utilisateur est membre lui sont fournis par l'appelant ({@code API & Security}), qui vérifie
 * aussi qu'un abonnement {@code TEAM} est créé par un administrateur de l'espace. Un abonnement
 * n'élargit jamais le périmètre autorisé (ABO-05).
 *
 * <p>Chaque transaction s'exécute sous le rôle {@code argos_app} avec le compte de l'appelant
 * (protocole de la migration V2) : la politique RLS de {@code subscription} sert de garde-fou.
 */
@ApplicationScoped
public class SubscriptionService {

    /**
     * Types de critères acceptés. {@code SEVERITY} porte la gravité minimale d'une règle d'alerte
     * immédiate (ALR-01, changement {@code diffuser-alertes-et-digests}).
     */
    public static final Set<String> FILTER_TYPES = Set.of("TECHNOLOGY", "PACKAGE", "VENDOR", "THEME", "DOMAIN",
            "SOURCE", "PROJECT", "SEVERITY");

    /** Valeurs acceptées pour un critère {@code SEVERITY}. */
    public static final Set<String> SEVERITIES = Set.of("LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<SubscriptionRequest.Filter>> FILTERS = new TypeReference<>() {
    };

    /** Abonnement enregistré. */
    public record Subscription(UUID id, String scope, UUID ownerWorkspaceId, UUID ownerUserId,
            List<SubscriptionRequest.Filter> filters, String channel, String frequency, boolean mandatory, String state,
            Instant createdAt) {
    }

    /** Issue d'une demande de retrait (ABO-06). */
    public enum LeaveOutcome { LEFT, MANDATORY, NOT_FOUND }

    @Inject
    DataSource dataSource;

    /** Valide puis enregistre la demande ; lève {@link SubscriptionRejectedException} si un attribut manque. */
    @Transactional
    public Subscription create(SubscriptionRequest request, UUID accountId) {
        Validated v = validate(request);
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO subscription(id, scope, owner_workspace_id, owner_user_id, filters, channel, frequency,
                                         mandatory, state, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, 'ACTIVE', now())
                """;
        try (Connection c = dataSource.getConnection()) {
            session(c, accountId);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setString(2, v.scope());
                ps.setObject(3, "TEAM".equals(v.scope()) ? request.ownerWorkspaceId() : null);
                ps.setObject(4, "PERSONAL".equals(v.scope()) ? request.ownerUserId() : null);
                ps.setString(5, MAPPER.writeValueAsString(v.filters()));
                ps.setString(6, v.channel());
                ps.setString(7, v.frequency());
                ps.setBoolean(8, request.mandatory());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                throw new SubscriptionRejectedException("ownerWorkspaceId", "espace propriétaire inconnu");
            }
            throw new IllegalStateException("enregistrement de l'abonnement impossible", e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("sérialisation des filtres impossible", e);
        }
        return find(id, accountId).orElseThrow();
    }

    @Transactional
    public Optional<Subscription> find(UUID id, UUID accountId) {
        List<Subscription> found = query(accountId, "SELECT " + COLUMNS + " FROM subscription WHERE id = ?", id);
        return found.stream().findFirst();
    }

    /**
     * Abonnements actifs qui s'appliquent à l'utilisateur : socle {@code TEAM} des espaces dont il est
     * membre, hors abonnements facultatifs qu'il a quittés (ABO-03, ABO-06), et ses abonnements
     * {@code PERSONAL} (ABO-04).
     */
    @Transactional
    public List<Subscription> activeFor(UUID userId, Set<UUID> memberWorkspaces) {
        String sql = "SELECT " + COLUMNS + """
                 FROM subscription s WHERE s.state = 'ACTIVE' AND (
                    (s.scope = 'PERSONAL' AND s.owner_user_id = ?)
                    OR (s.scope = 'TEAM' AND s.owner_workspace_id = ANY (?)
                        AND (s.mandatory OR NOT EXISTS (SELECT 1 FROM subscription_opt_out o
                                                        WHERE o.subscription_id = s.id AND o.user_id = ?))))
                ORDER BY s.created_at, s.id
                """;
        return query(userId, sql, userId, memberWorkspaces, userId);
    }

    /**
     * Retrait de l'utilisateur d'un abonnement : refusé pour un {@code TEAM} obligatoire ; retrait
     * personnel pour un {@code TEAM} facultatif ; désactivation pour un {@code PERSONAL} qu'il possède
     * (décision du 2026-11-16, Q-5).
     */
    @Transactional
    public LeaveOutcome leave(UUID subscriptionId, UUID userId, Set<UUID> memberWorkspaces) {
        Optional<Subscription> found = find(subscriptionId, userId).filter(s -> "ACTIVE".equals(s.state()));
        if (found.isEmpty()) {
            return LeaveOutcome.NOT_FOUND;
        }
        Subscription s = found.get();
        try (Connection c = dataSource.getConnection()) {
            session(c, userId);
            if ("PERSONAL".equals(s.scope())) {
                if (!s.ownerUserId().equals(userId)) {
                    return LeaveOutcome.NOT_FOUND;
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE subscription SET state = 'INACTIVE' WHERE id = ?")) {
                    ps.setObject(1, subscriptionId);
                    ps.executeUpdate();
                }
                return LeaveOutcome.LEFT;
            }
            if (!memberWorkspaces.contains(s.ownerWorkspaceId())) {
                return LeaveOutcome.NOT_FOUND;
            }
            if (s.mandatory()) {
                return LeaveOutcome.MANDATORY;
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO subscription_opt_out(subscription_id, user_id, opted_out_at) VALUES (?, ?, now())
                    ON CONFLICT DO NOTHING
                    """)) {
                ps.setObject(1, subscriptionId);
                ps.setObject(2, userId);
                ps.executeUpdate();
            }
            return LeaveOutcome.LEFT;
        } catch (SQLException e) {
            throw new IllegalStateException("retrait de l'abonnement impossible", e);
        }
    }

    private record Validated(String scope, List<SubscriptionRequest.Filter> filters, String channel, String frequency) {
    }

    /** Contrôle des attributs obligatoires, dans l'ordre de la spec (ABO-02). */
    static Validated validate(SubscriptionRequest r) {
        if (r == null) {
            throw new SubscriptionRejectedException("scope", "demande vide : portée manquante");
        }
        if (blank(r.scope())) {
            throw new SubscriptionRejectedException("scope", "attribut manquant : portée (scope)");
        }
        String scope = r.scope().strip().toUpperCase(Locale.ROOT);
        if (!scope.equals("TEAM") && !scope.equals("PERSONAL")) {
            throw new SubscriptionRejectedException("scope", "portée invalide : TEAM ou PERSONAL attendu");
        }
        if (scope.equals("TEAM") && r.ownerWorkspaceId() == null) {
            throw new SubscriptionRejectedException("ownerWorkspaceId", "attribut manquant : équipe propriétaire");
        }
        if (scope.equals("TEAM") && r.ownerUserId() != null) {
            throw new SubscriptionRejectedException("ownerUserId", "un abonnement TEAM n'a pas d'utilisateur propriétaire");
        }
        if (scope.equals("PERSONAL") && r.ownerUserId() == null) {
            throw new SubscriptionRejectedException("ownerUserId", "attribut manquant : utilisateur propriétaire");
        }
        if (scope.equals("PERSONAL") && r.ownerWorkspaceId() != null) {
            throw new SubscriptionRejectedException("ownerWorkspaceId", "un abonnement PERSONAL n'a pas d'équipe propriétaire");
        }
        if (r.filters() == null || r.filters().isEmpty()) {
            throw new SubscriptionRejectedException("filters", "attribut manquant : filtres");
        }
        List<SubscriptionRequest.Filter> filters = new ArrayList<>();
        for (SubscriptionRequest.Filter f : r.filters()) {
            if (f == null || blank(f.type()) || blank(f.value())) {
                throw new SubscriptionRejectedException("filters", "filtre incomplet : type et valeur attendus");
            }
            String type = f.type().strip().toUpperCase(Locale.ROOT);
            if (!FILTER_TYPES.contains(type)) {
                throw new SubscriptionRejectedException("filters", "type de filtre inconnu : " + f.type());
            }
            String value = f.value().strip();
            if (type.equals("SEVERITY")) {
                value = value.toUpperCase(Locale.ROOT);
                if (!SEVERITIES.contains(value)) {
                    throw new SubscriptionRejectedException("filters", "gravité inconnue : " + f.value());
                }
            }
            filters.add(new SubscriptionRequest.Filter(type, value));
        }
        if (blank(r.channel())) {
            throw new SubscriptionRejectedException("channel", "attribut manquant : canal");
        }
        if (blank(r.frequency())) {
            throw new SubscriptionRejectedException("frequency", "attribut manquant : fréquence");
        }
        if (r.mandatory() == null) {
            throw new SubscriptionRejectedException("mandatory", "attribut manquant : caractère obligatoire ou facultatif");
        }
        return new Validated(scope, List.copyOf(filters), r.channel().strip().toUpperCase(Locale.ROOT),
                r.frequency().strip().toUpperCase(Locale.ROOT));
    }

    private static final String COLUMNS = """
            s.id, s.scope, s.owner_workspace_id, s.owner_user_id, s.filters::text, s.channel, s.frequency, s.mandatory,
            s.state, s.created_at""".replace("s.", "");

    private List<Subscription> query(UUID accountId, String sql, Object... params) {
        List<Subscription> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            session(c, accountId);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    if (params[i] instanceof Set<?> set) {
                        ps.setArray(i + 1, c.createArrayOf("uuid", set.toArray()));
                    } else {
                        ps.setObject(i + 1, params[i]);
                    }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Subscription(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                            rs.getObject(4, UUID.class), MAPPER.readValue(rs.getString(5), FILTERS), rs.getString(6),
                            rs.getString(7), rs.getBoolean(8), rs.getString(9),
                            rs.getObject(10, OffsetDateTime.class).toInstant()));
                }
            }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des abonnements impossible", e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("filtres d'abonnement illisibles", e);
        }
        return result;
    }

    /**
     * Rôle {@code argos_app} et compte courant pour la transaction en cours (protocole de la migration V2) ;
     * aucune donnée d'autorisation n'est lue ici.
     */
    private static void session(Connection c, UUID accountId) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("SET LOCAL ROLE argos_app");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT set_config('argos.compte_courant', ?, true), set_config('argos.espace_courant', '', true)")) {
            ps.setString(1, accountId == null ? "" : accountId.toString());
            ps.execute();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
