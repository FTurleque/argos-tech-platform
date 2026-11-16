package io.argos.api.comptes;

import static io.argos.testsupport.Api.en;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import io.argos.testsupport.Api;

/** ACC-06 : journal des refus d'accès. */
@QuarkusTest
class JournalRefusTest {

    @Inject
    DataSource dataSource;

    /** ACC-06-S1 : le refus d'accès à un autre espace est journalisé sans donnée de la ressource. */
    @Test
    void refusJournalise() throws Exception {
        String espaceB = Api.creerEspace("alice", "Projet Mercure très secret");
        Api.assurerCompte("frank");
        String frank = Api.idCompte("frank");
        OffsetDateTime avant = OffsetDateTime.now().minusSeconds(5);

        en("frank").when().get("/api/v1/espaces/" + espaceB).then().statusCode(404);

        List<List<String>> lignes = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT * FROM refus_acces WHERE compte_id = ?::uuid AND ressource LIKE ? ORDER BY id")) {
            ps.setString(1, frank);
            ps.setString(2, "%" + espaceB);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    List<String> valeurs = new ArrayList<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        valeurs.add(rs.getString(i));
                    }
                    lignes.add(valeurs);
                    assertEquals("GET /api/v1/espaces/" + espaceB, rs.getString("ressource"));
                    assertEquals("NON_MEMBRE", rs.getString("motif"));
                    OffsetDateTime horodatage = rs.getObject("horodatage", OffsetDateTime.class);
                    assertNotNull(horodatage);
                    assertTrue(horodatage.isAfter(avant));
                }
            }
        }
        assertEquals(1, lignes.size());
        // Aucune donnée de la ressource refusée (ici le nom de l'espace) dans l'entrée.
        assertFalse(String.join("|", lignes.get(0).stream().map(String::valueOf).toList()).contains("Mercure"));
    }

    /** Le refus pour rôle insuffisant est aussi journalisé. */
    @Test
    void refusPourRoleInsuffisantJournalise() throws Exception {
        String espace = Api.creerEspace("alice", "Espace des lecteurs");
        Api.ajouterMembre("alice", espace, "frank", "LECTEUR");
        String frank = Api.idCompte("frank");

        en("frank").when().get("/api/v1/espaces/" + espace + "/invitations").then().statusCode(403);

        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT motif FROM refus_acces WHERE compte_id = ?::uuid AND ressource = ?")) {
            ps.setString(1, frank);
            ps.setString(2, "GET /api/v1/espaces/" + espace + "/invitations");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ROLE_INSUFFISANT", rs.getString(1));
            }
        }
    }
}
