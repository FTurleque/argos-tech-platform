package io.argos.observabilite;

import java.util.ArrayList;
import java.util.List;

/**
 * Erreurs imprévues retenues pendant un passage sur des unités indépendantes (sources, projets, espaces, produits,
 * textes), règle des frontières d'erreur d'arc42 §8.5 ([DÉCISION] du porteur du projet, 2026-11-24 : isoler puis
 * relancer). À la frontière, l'unité fautive est journalisée avec sa cause et comptée en échec, les unités suivantes
 * sont traitées, puis {@link #relancer()} relance la première erreur, les suivantes en erreurs supprimées : le
 * passage se termine en échec visible et aucune erreur n'est absorbée.
 *
 * <p>Instance propre à un passage, non partagée entre fils.
 */
public final class ErreursImprevues {

    private final List<RuntimeException> retenues = new ArrayList<>();

    /** Retient une erreur imprévue d'une unité ; elle doit déjà être journalisée avec sa cause. */
    public void retenir(RuntimeException erreur) {
        retenues.add(erreur);
    }

    /** Nombre d'unités en erreur imprévue. */
    public int nombre() {
        return retenues.size();
    }

    /** Erreurs retenues, dans l'ordre du passage. */
    public List<RuntimeException> retenues() {
        return List.copyOf(retenues);
    }

    /** Relance la première erreur retenue, les suivantes attachées comme erreurs supprimées ; sans effet sinon. */
    public void relancer() {
        relancer(retenues);
    }

    /** Relance la première erreur de la liste, les suivantes attachées comme erreurs supprimées ; sans effet si vide. */
    public static void relancer(List<RuntimeException> erreurs) {
        if (erreurs.isEmpty()) {
            return;
        }
        RuntimeException premiere = erreurs.get(0);
        erreurs.subList(1, erreurs.size()).forEach(premiere::addSuppressed);
        throw premiere;
    }
}
