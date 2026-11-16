package io.argos.testsupport;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.api.security.Caller;
import io.argos.api.security.CallerResolver;
import io.vertx.core.http.HttpServerRequest;

/**
 * Appelant de test, lu dans des en-têtes : {@code X-Test-User}, {@code X-Test-Workspaces},
 * {@code X-Test-Admin} (listes d'UUID séparés par des virgules) et {@code X-Test-Operator}.
 * N'existe que dans les sources de test : l'application livrée ne lit jamais ces en-têtes.
 */
@ApplicationScoped
public class HeaderCallerResolver implements CallerResolver {

    public static final String USER = "X-Test-User";
    public static final String WORKSPACES = "X-Test-Workspaces";
    public static final String ADMIN = "X-Test-Admin";
    public static final String OPERATOR = "X-Test-Operator";

    @Inject
    HttpServerRequest request;

    @Override
    public Optional<Caller> current() {
        String user = request.getHeader(USER);
        if (user == null || user.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Caller(user, ids(request.getHeader(WORKSPACES)), ids(request.getHeader(ADMIN)),
                "true".equals(request.getHeader(OPERATOR))));
    }

    private static Set<UUID> ids(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(",")).map(String::strip).map(UUID::fromString).collect(Collectors.toSet());
    }
}
