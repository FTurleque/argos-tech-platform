package io.argos.commun.securite;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Limite le nombre de requêtes par compte sur une fenêtre glissante d'une minute. Mécanisme commun
 * au serveur MCP (MCP-06, {@code argos.mcp.debit.requetes-par-minute}) et aux requêtes de la veille
 * web qui déclenchent une récupération externe ({@code argos.veille-web.debit.requetes-par-minute}) ;
 * chaque usage tient sa propre instance, donc ses propres compteurs.
 *
 * <p>Le compteur est tenu en mémoire de l'instance : Argos est déployé en une seule instance
 * (ADR-0017, ADR-0026). Aucune donnée n'est conservée au-delà de la fenêtre.
 */
public final class LimiteurDebit {

    private static final Duration FENETRE = Duration.ofMinutes(1);

    /** Fréquence de retrait des fenêtres échues, en nombre d'appels. */
    private static final int NETTOYAGE_TOUS_LES = 256;

    private final Map<UUID, Deque<Long>> appels = new ConcurrentHashMap<>();
    private final AtomicLong compteur = new AtomicLong();
    private final int requetesParMinute;
    private final LongSupplier horloge;

    /**
     * Limiteur de {@code requetesParMinute} requêtes par minute et par compte. La fenêtre est mesurée sur l'horloge
     * monotone de la JVM ({@code System.nanoTime()}) : une durée, pas l'heure murale (arc42 §8.16).
     */
    public LimiteurDebit(int requetesParMinute) {
        this(requetesParMinute, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    /** Limiteur avec une horloge en millisecondes, remplaçable par les tests unitaires. */
    public LimiteurDebit(int requetesParMinute, LongSupplier horloge) {
        if (requetesParMinute < 1) {
            throw new IllegalArgumentException("limite de débit invalide : " + requetesParMinute);
        }
        this.requetesParMinute = requetesParMinute;
        this.horloge = horloge;
    }

    /** Vrai si l'appel est admis, et il est alors compté ; faux si la limite est atteinte. */
    public boolean admettre(UUID compte) {
        long maintenant = horloge.getAsLong();
        long debutFenetre = maintenant - FENETRE.toMillis();
        if (compteur.incrementAndGet() % NETTOYAGE_TOUS_LES == 0) {
            nettoyer(debutFenetre);
        }
        Deque<Long> fenetre = appels.computeIfAbsent(compte, cle -> new ArrayDeque<>());
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

    /** Retire les fenêtres dont tous les appels sont échus : la mémoire suit les comptes actifs. */
    private void nettoyer(long debutFenetre) {
        appels.entrySet().removeIf(entree -> {
            Deque<Long> fenetre = entree.getValue();
            synchronized (fenetre) {
                return fenetre.isEmpty() || fenetre.peekLast() <= debutFenetre;
            }
        });
    }

    /** Nombre de comptes suivis. */
    int comptesSuivis() {
        return appels.size();
    }
}
