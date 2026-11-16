package io.argos.workspace;

/**
 * Rôle d'un compte dans un espace (ACC-03, ADR-0016). L'ordre de déclaration est
 * l'ordre croissant des droits. L'administration de la plateforme n'est pas un rôle
 * d'espace et ne donne aucun accès implicite aux espaces (INV-3).
 */
public enum Role {
    LECTEUR,
    CONTRIBUTEUR,
    ADMINISTRATEUR;

    public boolean auMoins(Role minimum) {
        return compareTo(minimum) >= 0;
    }
}
