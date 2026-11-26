package io.argos.projectintel;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.inventory.ProjectDirectory;
import io.argos.scm.RepositoryRef;
import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;

/** Collecte immédiate de l'activité d'un projet par l'API (PIN-06). */
@QuarkusTest
class CollecteImmediateTest {

    @Inject
    TestDb baseDeTest;

    @Inject
    ProjectDirectory projets;

    private UUID espace;

    @BeforeEach
    void preparer() {
        baseDeTest.reset();
        espace = Api.nouvelEspace("alice", "Équipe A");
        Api.assurerCompte("jules");
        Api.ajouterMembre("alice", espace.toString(), "hugo", "CONTRIBUTEUR");
        Api.ajouterMembre("alice", espace.toString(), "ines", "LECTEUR");
    }

    private static String collecte(UUID projet) {
        return "/api/v1/projects/" + projet + "/activite/collecte";
    }

    @Test
    void pin06S1CollecteDUnProjetDoteDUnDepot() {
        UUID projet = projets.create(espace, "app", new RepositoryRef("GITHUB", "org/immediat", "main", null, null), List.of());
        ActiviteProjetsTest.depotGithub("org/immediat");

        Api.enTantQue("hugo").when().post(collecte(projet)).then().statusCode(200).body("issue", equalTo("A_JOUR"));

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM activite_collecte WHERE project_id = '" + projet + "'"));
    }

    @Test
    void pin06S2ProjetSansDepot() {
        UUID projet = projets.create(espace, "sans-depot", null, null);

        Api.enTantQue("alice").when().post(collecte(projet)).then().statusCode(200).body("issue", equalTo("SANS_DEPOT"));
    }

    @Test
    void pin06S3LecteurOuNonMembre() {
        UUID projet = projets.create(espace, "app", null, null);

        Api.enTantQue("ines").when().post(collecte(projet)).then().statusCode(403);
        Api.enTantQue("jules").when().post(collecte(projet)).then().statusCode(404);

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("ines")
                + "' AND motif = 'ROLE_INSUFFISANT'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM refus_acces WHERE compte_id = '" + Api.compte("jules")
                + "' AND motif = 'NON_MEMBRE'"));
    }
}
