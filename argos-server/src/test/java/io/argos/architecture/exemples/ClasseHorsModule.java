package io.argos.architecture.exemples;

/**
 * Classe d'un paquet de premier niveau absent de la table {@code ModuleBoundariesTest.ALLOWED}
 * ({@code io.argos.architecture}), contre laquelle {@code ModuleBoundariesTest} vérifie que sa règle du module
 * déclaré la refuse. Jamais exécutée.
 */
public final class ClasseHorsModule {

    private ClasseHorsModule() {
    }
}
