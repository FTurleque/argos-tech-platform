package io.argos.mcp;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Limite le nombre d'appels d'outils MCP par compte sur une fenêtre glissante (MCP-06). Valeur par
 * défaut : 60 requêtes par minute et par utilisateur (décision du 2026-11-16, Q-1), paramétrable par
 * {@code argos.mcp.debit.requetes-par-minute}.
 *
 * <p>Le compteur est tenu en mémoire de l'instance : Argos est déployé en une seule instance
 * (ADR-0017, ADR-0026). Aucune donnée n'est conservée au-delà de la fenêtre.
 */
@ApplicationScoped
public class LimiteurDebit {

    private static final Duration FENETRE = Duration.ofMinutes(1);

    private final Map<UUID, Deque<Long>> appels = new ConcurrentHashMap<>();

    @ConfigProperty(name = "argos.mcp.debit.requetes-par-minute", defaultValue = "60")
    int requetesParMinute;

    /** Horloge en millisecondes, remplaçable par les tests unitaires. */
    LongSupplier horloge = System::currentTimeMillis;

    /** Vrai si l'appel est admis, et il est alors compté ; faux si la limite est atteinte. */
    public boolean admettre(UUID compte) {
        long maintenant = horloge.getAsLong();
        long debutFenetre = maintenant - FENETRE.toMillis();
        Deque<Long> fenetre = appels.computeIfAbsent(compte, c -> new ArrayDeque<>());
        synchronized (fenetre) {
            while (!fenetre.isEmpty() && fenetre.peekFirst() <= debutFenetre) {
                fenetre.pollFirst();
            }
            if (fenetre.size() >= requetesParMinute) {
                return false;
            }
            fenetre.addLast(maintenant);
            return true;
        }
    }
}
