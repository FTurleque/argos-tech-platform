package io.argos.api.comptes;

import static io.argos.testsupport.Api.en;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import io.argos.testsupport.Api;

/** ACC-04 : cloisonnement par espace, dans le service (API) et dans la base (RLS). */
@QuarkusTest
class CloisonnementTest {

    @Inject
    DataSource dataSource;

    @Inject
    ContexteBase base;

    /** ACC-04-S1 : membre du seul espace A, l'utilisateur ne lit rien de l'espace B. */
    @Test
    void lectureDUnAutreEspaceRefusee() {
        String espaceA = Api.creerEspace("alice", "Espace A");
        String espaceB = Api.creerEspace("alice", "Espace B confidentiel");
        Api.inviter("alice", espaceB, "secret-b@argos.test", "LECTEUR");
        Api.ajouterMembre("alice", espaceA, "frank", "CONTRIBUTEUR");

        en("frank").when().get("/api/v1/espaces/" + espaceA).then().statusCode(200);
        for (String chemin : List.of("", "/membres", "/invitations")) {
            en("frank").when().get("/api/v1/espaces/" + espaceB + chemin)
                    .then().statusCode(404)
                    .body("code", equalTo("INTROUVABLE"))
                    .body(not(containsString("confidentiel")))
                    .body(not(containsString("secret-b")));
        }
        en("frank").contentType(ContentType.JSON).body(Map.of("email", "x@argos.test", "role", "LECTEUR"))
                .when().post("/api/v1/espaces/" + espaceB + "/invitations").then().statusCode(404);
        en("frank").when().get("/api/v1/espaces").then().statusCode(200).body("id", not(hasItem(espaceB)));
        // Un espace inexistant répond comme un espace d'autrui (Q-4).
        en("frank").when().get("/api/v1/espaces/" + UUID.randomUUID()).then().statusCode(404);
    }

    /** ACC-04-S2 : sous le rôle applicatif, une requête sans espace courant ne renvoie aucune ligne. */
    @Test
    void requeteEnBaseSansContexteNeRenvoieRien() throws Exception {
        String espace = Api.creerEspace("alice", "Espace pour la RLS");
        Api.inviter("alice", espace, "rls@argos.test", "LECTEUR");
        // Le propriétaire des tables (hors RLS) voit bien les lignes.
        assertTrue(compterEnProprietaire("SELECT count(*) FROM invitation") > 0);
        assertTrue(compterEnProprietaire("SELECT count(*) FROM appartenance") > 0);

        // Rôle applicatif sans aucun contexte : zéro ligne d'espace.
        for (String table : List.of("espace", "appartenance", "invitation")) {
            long lignes = base.executer(Contexte.aucun(), connexion -> compter(connexion, "SELECT count(*) FROM " + table));
            assertEquals(0, lignes, "lignes visibles sans contexte dans " + table);
        }
    }

    /** RLS : un espace courant transmis sans appartenance ne donne accès à rien ; avec, aux seules lignes de l'espace. */
    @Test
    void rlsLimiteeAuxEspacesDuCompte() throws Exception {
        String espaceA = Api.creerEspace("alice", "Espace RLS A");
        String espaceB = Api.creerEspace("alice", "Espace RLS B");
        Api.inviter("alice", espaceB, "invite-b@argos.test", "LECTEUR");
        Api.ajouterMembre("alice", espaceA, "frank", "LECTEUR");
        UUID frank = UUID.fromString(Api.idCompte("frank"));
        UUID uuidEspaceA = UUID.fromString(espaceA);
        UUID uuidEspaceB = UUID.fromString(espaceB);

        // Espace courant B transmis pour un compte non membre de B : rien.
        Contexte frankDansB = new Contexte(frank, null, uuidEspaceB);
        assertEquals(0L, (long) base.executer(frankDansB,
                connexion -> compter(connexion, "SELECT count(*) FROM invitation WHERE espace_id = '" + uuidEspaceB + "'")));
        assertEquals(0L, (long) base.executer(frankDansB,
                connexion -> compter(connexion, "SELECT count(*) FROM appartenance WHERE espace_id = '" + uuidEspaceB + "'")));
        assertEquals(0L, (long) base.executer(frankDansB,
                connexion -> compter(connexion, "SELECT count(*) FROM espace WHERE id = '" + uuidEspaceB + "'")));

        // Espace courant A, dont il est membre : uniquement les lignes de A.
        List<String> espacesVus = base.executer(new Contexte(frank, null, uuidEspaceA), connexion -> {
            List<String> ids = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement("SELECT DISTINCT espace_id FROM appartenance");
                    ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    ids.add(resultat.getString(1));
                }
            }
            return ids;
        });
        assertTrue(espacesVus.contains(espaceA));
        assertFalse(espacesVus.contains(espaceB));

        // Écriture refusée par la base elle-même pour un non-administrateur (défense en profondeur).
        long modifiees = base.executer(new Contexte(frank, null, uuidEspaceA), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "UPDATE appartenance SET role = 'ADMINISTRATEUR' WHERE espace_id = ?")) {
                requete.setObject(1, uuidEspaceA);
                return (long) requete.executeUpdate();
            }
        });
        assertEquals(0, modifiees);
    }

    private long compterEnProprietaire(String sql) throws Exception {
        try (Connection connexion = dataSource.getConnection()) {
            return compter(connexion, sql);
        }
    }

    private static long compter(Connection connexion, String sql) throws java.sql.SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(sql); ResultSet resultat = requete.executeQuery()) {
            resultat.next();
            return resultat.getLong(1);
        }
    }
}
