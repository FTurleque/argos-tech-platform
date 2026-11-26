package io.argos.commun;

import java.util.List;
import java.util.function.Function;

/**
 * Page d'une liste renvoyée par l'API (ADR-0040) : éléments de la page, numéro de page à partir de 0, taille de page
 * appliquée et nombre total d'éléments de la liste. Les noms des champs JSON ({@code items}, {@code page},
 * {@code size}, {@code total}) sont ceux de toutes les listes paginées de l'API.
 *
 * @param <T> type des éléments
 */
public record Page<T>(List<T> items, int page, int size, long total) {

    public Page {
        items = List.copyOf(items);
    }

    /** Page des éléments lus pour la demande {@code pagination}, sur {@code total} éléments au total. */
    public static <T> Page<T> depuis(List<T> items, Pagination pagination, long total) {
        return new Page<>(items, pagination.page(), pagination.taille(), total);
    }

    /**
     * Page extraite d'une liste déjà triée et tenue en mémoire, pour une liste bornée en amont dont le filtre final
     * s'applique hors de la base.
     */
    public static <T> Page<T> extraire(List<T> tous, Pagination pagination) {
        int debut = (int) Math.min(pagination.decalage(), tous.size());
        int fin = Math.min(debut + pagination.taille(), tous.size());
        return depuis(tous.subList(debut, fin), pagination, tous.size());
    }

    /** Même page, dont chaque élément est transformé par {@code conversion}. */
    public <R> Page<R> convertir(Function<? super T, ? extends R> conversion) {
        List<R> converties = items.stream().<R>map(conversion).toList();
        return new Page<>(converties, page, size, total);
    }
}
