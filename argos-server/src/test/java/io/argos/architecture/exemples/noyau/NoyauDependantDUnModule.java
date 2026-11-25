package io.argos.architecture.exemples.noyau;

import io.argos.workspace.Role;

/**
 * Classe d'un faux noyau qui dépend d'un module métier, contre laquelle {@code ModuleBoundariesTest} vérifie que
 * sa règle du noyau la refuse. Jamais exécutée.
 */
public final class NoyauDependantDUnModule {

    private NoyauDependantDUnModule() {
    }

    public static Role role() {
        return Role.values()[0];
    }
}
