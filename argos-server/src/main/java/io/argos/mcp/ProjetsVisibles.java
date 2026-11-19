package io.argos.mcp;

import java.util.UUID;
import java.util.stream.Stream;

import io.argos.inventory.InventoryQueries;

/**
 * Projets lisibles par l'appelant d'un outil MCP, lus par la requête paginée de l'API REST
 * ({@link InventoryQueries#projects(UUID, int, int)}, décision G6 du porteur du projet, 2026-11-19) : une seule
 * requête de liste des projets visibles, sous la RLS de {@code project} avec le compte de l'appelant (ADR-0016).
 * Les pages de taille maximale sont lues à la demande, dans l'ordre de la requête (nom du projet, nom de l'espace,
 * identifiant), jusqu'à la dernière ou jusqu'à ce que l'outil n'en demande plus.
 */
final class ProjetsVisibles {

    private ProjetsVisibles() {
    }

    /** Projets lisibles par le compte, page après page ; flux vide s'il n'en lit aucun. */
    static Stream<InventoryQueries.ProjectItem> du(UUID compteId, InventoryQueries inventaires) {
        return Stream.iterate(inventaires.projects(compteId, 0, InventoryQueries.MAX_PAGE_SIZE), page -> page != null,
                        page -> derniere(page) ? null : inventaires.projects(compteId, page.page() + 1, page.size()))
                .flatMap(page -> page.items().stream());
    }

    /** Vrai quand la page est la dernière : aucune ligne, ou fin de la liste atteinte. */
    private static boolean derniere(InventoryQueries.ProjectPage page) {
        return page.items().isEmpty() || (long) (page.page() + 1) * page.size() >= page.total();
    }
}
