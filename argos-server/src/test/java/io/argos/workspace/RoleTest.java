package io.argos.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Ordre croissant des droits des rôles d'espace (ACC-03). */
class RoleTest {

    @Test
    void ordreDesRoles() {
        assertTrue(Role.ADMINISTRATEUR.auMoins(Role.CONTRIBUTEUR));
        assertTrue(Role.CONTRIBUTEUR.auMoins(Role.CONTRIBUTEUR));
        assertFalse(Role.LECTEUR.auMoins(Role.CONTRIBUTEUR));
    }
}
