package io.argos.workspace.subscription;

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
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.commun.ContexteBase;

/**
 * Abonnements de veille {@code TEAM} et {@code PERSONAL} (ABO-01 à ABO-04, ABO-06) ; état actif ou
 * suspendu et suppression distincte (ABO-07, ABO-08, décision J7 du 2026-11-19).
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
        Validated validated = validate(request);
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO subscription(id, scope, owner_workspace_id, owner_user_id, filters, channel, frequency,
                                         mandatory, state, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, 'ACTIVE', now())
                """;
        try (Connection connection = dataSource.getConnection()) {
            ContexteBase.appliquer(connection, ContexteBase.Contexte.duCompte(accountId));
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                statement.setString(2, validated.scope());
                statement.setObject(3, "TEAM".equals(validated.scope()) ? request.ownerWorkspaceId() : null);
                statement.setObject(4, "PERSONAL".equals(validated.scope()) ? request.ownerUserId() : null);
                statement.setString(5, MAPPER.writeValueAsString(validated.filters()));
                statement.setString(6, validated.channel());
                statement.setString(7, validated.frequency());
                statement.setBoolean(8, request.mandatory());
                statement.executeUpdate();
            }
        } catch (SQLException error) {
            if ("23503".equals(error.getSQLState())) {
                throw new SubscriptionRejectedException("ownerWorkspaceId", "espace propriétaire inconnu");
            }
            throw new IllegalStateException("enregistrement de l'abonnement impossible", error);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("sérialisation des filtres impossible", error);
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
     * Abonnements visibles de l'utilisateur, actifs ou suspendus (ABO-07-S4) : ses abonnements
     * {@code PERSONAL} et les abonnements {@code TEAM} des espaces dont il est membre, hors abonnements
     * facultatifs qu'il a quittés. Chacun porte son état ; seuls les actifs s'appliquent ({@link #activeFor}).
     */
    @Transactional
    public List<Subscription> listFor(UUID userId, Set<UUID> memberWorkspaces) {
        String sql = "SELECT " + COLUMNS + """
                 FROM subscription s WHERE (
                    (s.scope = 'PERSONAL' AND s.owner_user_id = ?)
                    OR (s.scope = 'TEAM' AND s.owner_workspace_id = ANY (?)
                        AND (s.mandatory OR NOT EXISTS (SELECT 1 FROM subscription_opt_out o
                                                        WHERE o.subscription_id = s.id AND o.user_id = ?))))
                ORDER BY s.created_at, s.id
                """;
        return query(userId, sql, userId, memberWorkspaces, userId);
    }

    /**
     * Suspend un abonnement : critères, canal, fréquence et retraits conservés, plus aucune notification
     * (ABO-07, décision J7 du 2026-11-19). Idempotent. Vide si l'abonnement est inconnu, invisible ou si la
     * politique RLS refuse la modification (ni propriétaire du {@code PERSONAL}, ni administrateur de
     * l'espace du {@code TEAM}).
     */
    @Transactional
    public Optional<Subscription> suspend(UUID subscriptionId, UUID accountId) {
        return changeState(subscriptionId, accountId, "SUSPENDED");
    }

    /** Réactive un abonnement suspendu, sans rejouer la période de suspension (ABO-07-S2, P-4). Idempotent. */
    @Transactional
    public Optional<Subscription> reactivate(UUID subscriptionId, UUID accountId) {
        return changeState(subscriptionId, accountId, "ACTIVE");
    }

    /**
     * Supprime définitivement un abonnement, actif ou suspendu, avec ses critères et les retraits de ses
     * membres (ABO-08). Faux si l'abonnement est inconnu, invisible ou si la politique RLS refuse la suppression.
     */
    @Transactional
    public boolean delete(UUID subscriptionId, UUID accountId) {
        try (Connection connection = dataSource.getConnection()) {
            ContexteBase.appliquer(connection, ContexteBase.Contexte.duCompte(accountId));
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM subscription WHERE id = ?")) {
                statement.setObject(1, subscriptionId);
                return statement.executeUpdate() == 1;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("suppression de l'abonnement impossible", error);
        }
    }

    private Optional<Subscription> changeState(UUID subscriptionId, UUID accountId, String state) {
        int updated;
        try (Connection connection = dataSource.getConnection()) {
            ContexteBase.appliquer(connection, ContexteBase.Contexte.duCompte(accountId));
            try (PreparedStatement statement = connection.prepareStatement("UPDATE subscription SET state = ? WHERE id = ?")) {
                statement.setString(1, state);
                statement.setObject(2, subscriptionId);
                updated = statement.executeUpdate();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("changement d'état de l'abonnement impossible", error);
        }
        return updated == 1 ? find(subscriptionId, accountId) : Optional.empty();
    }

    /**
     * Retrait de l'utilisateur d'un abonnement : refusé pour un {@code TEAM} obligatoire ; retrait
     * personnel pour un {@code TEAM} facultatif actif (décision du 2026-11-16, Q-5) ; suspension pour un
     * {@code PERSONAL} qu'il possède, actif ou déjà suspendu (proposition P-8 du changement
     * {@code automatiser-chaine-veille}).
     */
    @Transactional
    public LeaveOutcome leave(UUID subscriptionId, UUID userId, Set<UUID> memberWorkspaces) {
        Optional<Subscription> found = find(subscriptionId, userId)
                .filter(candidate -> "PERSONAL".equals(candidate.scope()) || "ACTIVE".equals(candidate.state()));
        if (found.isEmpty()) {
            return LeaveOutcome.NOT_FOUND;
        }
        Subscription subscription = found.get();
        if ("PERSONAL".equals(subscription.scope())) {
            if (!subscription.ownerUserId().equals(userId)) {
                return LeaveOutcome.NOT_FOUND;
            }
            return suspend(subscriptionId, userId).isPresent() ? LeaveOutcome.LEFT : LeaveOutcome.NOT_FOUND;
        }
        try (Connection connection = dataSource.getConnection()) {
            ContexteBase.appliquer(connection, ContexteBase.Contexte.duCompte(userId));
            if (!memberWorkspaces.contains(subscription.ownerWorkspaceId())) {
                return LeaveOutcome.NOT_FOUND;
            }
            if (subscription.mandatory()) {
                return LeaveOutcome.MANDATORY;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO subscription_opt_out(subscription_id, user_id, opted_out_at) VALUES (?, ?, now())
                    ON CONFLICT DO NOTHING
                    """)) {
                statement.setObject(1, subscriptionId);
                statement.setObject(2, userId);
                statement.executeUpdate();
            }
            return LeaveOutcome.LEFT;
        } catch (SQLException error) {
            throw new IllegalStateException("retrait de l'abonnement impossible", error);
        }
    }

    private record Validated(String scope, List<SubscriptionRequest.Filter> filters, String channel, String frequency) {
    }

    /** Contrôle des attributs obligatoires, dans l'ordre de la spec (ABO-02). */
    static Validated validate(SubscriptionRequest request) {
        if (request == null) {
            throw new SubscriptionRejectedException("scope", "demande vide : portée manquante");
        }
        if (blank(request.scope())) {
            throw new SubscriptionRejectedException("scope", "attribut manquant : portée (scope)");
        }
        String scope = request.scope().strip().toUpperCase(Locale.ROOT);
        if (!scope.equals("TEAM") && !scope.equals("PERSONAL")) {
            throw new SubscriptionRejectedException("scope", "portée invalide : TEAM ou PERSONAL attendu");
        }
        if (scope.equals("TEAM") && request.ownerWorkspaceId() == null) {
            throw new SubscriptionRejectedException("ownerWorkspaceId", "attribut manquant : équipe propriétaire");
        }
        if (scope.equals("TEAM") && request.ownerUserId() != null) {
            throw new SubscriptionRejectedException("ownerUserId", "un abonnement TEAM n'a pas d'utilisateur propriétaire");
        }
        if (scope.equals("PERSONAL") && request.ownerUserId() == null) {
            throw new SubscriptionRejectedException("ownerUserId", "attribut manquant : utilisateur propriétaire");
        }
        if (scope.equals("PERSONAL") && request.ownerWorkspaceId() != null) {
            throw new SubscriptionRejectedException("ownerWorkspaceId", "un abonnement PERSONAL n'a pas d'équipe propriétaire");
        }
        if (request.filters() == null || request.filters().isEmpty()) {
            throw new SubscriptionRejectedException("filters", "attribut manquant : filtres");
        }
        List<SubscriptionRequest.Filter> filters = new ArrayList<>();
        for (SubscriptionRequest.Filter filter : request.filters()) {
            if (filter == null || blank(filter.type()) || blank(filter.value())) {
                throw new SubscriptionRejectedException("filters", "filtre incomplet : type et valeur attendus");
            }
            String type = filter.type().strip().toUpperCase(Locale.ROOT);
            if (!FILTER_TYPES.contains(type)) {
                throw new SubscriptionRejectedException("filters", "type de filtre inconnu : " + filter.type());
            }
            String value = filter.value().strip();
            if (type.equals("SEVERITY")) {
                value = value.toUpperCase(Locale.ROOT);
                if (!SEVERITIES.contains(value)) {
                    throw new SubscriptionRejectedException("filters", "gravité inconnue : " + filter.value());
                }
            }
            filters.add(new SubscriptionRequest.Filter(type, value));
        }
        if (blank(request.channel())) {
            throw new SubscriptionRejectedException("channel", "attribut manquant : canal");
        }
        if (blank(request.frequency())) {
            throw new SubscriptionRejectedException("frequency", "attribut manquant : fréquence");
        }
        if (request.mandatory() == null) {
            throw new SubscriptionRejectedException("mandatory", "attribut manquant : caractère obligatoire ou facultatif");
        }
        return new Validated(scope, List.copyOf(filters), request.channel().strip().toUpperCase(Locale.ROOT),
                request.frequency().strip().toUpperCase(Locale.ROOT));
    }

    private static final String COLUMNS = """
            s.id, s.scope, s.owner_workspace_id, s.owner_user_id, s.filters::text, s.channel, s.frequency, s.mandatory,
            s.state, s.created_at""".replace("s.", "");

    private List<Subscription> query(UUID accountId, String sql, Object... params) {
        List<Subscription> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            ContexteBase.appliquer(connection, ContexteBase.Contexte.duCompte(accountId));
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < params.length; index++) {
                    if (params[index] instanceof Set<?> set) {
                        statement.setArray(index + 1, connection.createArrayOf("uuid", set.toArray()));
                    } else {
                        statement.setObject(index + 1, params[index]);
                    }
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new Subscription(rows.getObject(1, UUID.class), rows.getString(2), rows.getObject(3, UUID.class),
                            rows.getObject(4, UUID.class), MAPPER.readValue(rows.getString(5), FILTERS), rows.getString(6),
                            rows.getString(7), rows.getBoolean(8), rows.getString(9),
                            rows.getObject(10, OffsetDateTime.class).toInstant()));
                }
            }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("lecture des abonnements impossible", error);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("filtres d'abonnement illisibles", error);
        }
        return result;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
