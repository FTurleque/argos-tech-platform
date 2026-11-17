package io.argos.scm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Validation du dépôt GitHub {@code owner/repo} avant tout appel avec le jeton configuré. */
class DepotGithubTest {

    @Test
    void depotOwnerRepoAccepte() {
        assertTrue(ForgeHttp.depotGithubValide("argos/argos-tech-platform"));
        assertTrue(ForgeHttp.depotGithubValide("org.exemple/lib_1.2"));
        assertTrue(ForgeHttp.depotGithubValide("a/.github"));
    }

    @Test
    void segmentsFaitsDePointsOuFormeInvalideRefuses() {
        assertFalse(ForgeHttp.depotGithubValide(null));
        assertFalse(ForgeHttp.depotGithubValide("../.."));
        assertFalse(ForgeHttp.depotGithubValide("argos/.."));
        assertFalse(ForgeHttp.depotGithubValide("../argos"));
        assertFalse(ForgeHttp.depotGithubValide("./repo"));
        assertFalse(ForgeHttp.depotGithubValide("argos/repo/issues"));
        assertFalse(ForgeHttp.depotGithubValide("argos"));
        assertFalse(ForgeHttp.depotGithubValide("argos/repo?x=1"));
    }
}
