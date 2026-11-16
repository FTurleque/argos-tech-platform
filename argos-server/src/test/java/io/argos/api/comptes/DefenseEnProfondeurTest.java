package io.argos.api.comptes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.argos.workspace.Compte;
import io.argos.workspace.ComptesService;
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.argos.workspace.EspacesService;
import io.argos.workspace.RegleViolee;
import io.argos.workspace.Role;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Défense en profondeur sous le rôle applicatif {@code argos_app}, non super-utilisateur (ADR-0016) :
 * invitations non réinscriptibles par leur destinataire, moindre privilège sur les comptes et le
 * journal des refus, invariant INV-1 sûr en concurrence.
 */
@QuarkusTest
class DefenseEnProfondeurTest {

    /** Interrompt un traitement réussi pour annuler sa transaction. */
    private static final class Annulation extends RuntimeException {
    }

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    @Inject
    TestDb db;

    @Inject
    ComptesService comptes;

    @Inject
    EspacesService espaces;

    private Compte compte(String utilisateur) {
        UUID id = Api.compte(utilisateur);
        return comptes.parSujet(db.string("SELECT sujet_oidc FROM compte WHERE id = '" + id + "'")).orElseThrow();
    }

    private static String etatSql(RuntimeException e) {
        Throwable t = e;
        while (t != null && !(t instanceof SQLException)) {
            t = t.getCause();
        }
        return t == null ? null : ((SQLException) t).getSQLState();
    }

    private void executerSql(Contexte contexte, String sql, Object... parametres) {
        base.executer(contexte, c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < parametres.length; i++) {
                    ps.setObject(i + 1, parametres[i]);
                }
                ps.executeUpdate();
            }
            throw new Annulation();
        });
    }

    /** Le destinataire d'une invitation ne peut modifier que son statut d'acceptation. */
    @Test
    void leDestinataireNeReecritNiLeRoleNiLEspaceDeSonInvitation() {
        String espace = Api.creerEspace("alice", "Espace protégé");
        String autre = Api.creerEspace("alice", "Autre espace");
        UUID invitation = UUID.fromString(Api.inviter("alice", espace, "erin@argos.test", "LECTEUR"));
        Compte erin = compte("erin");
        Contexte destinataire = new Contexte(erin.id(), null, "erin@argos.test", null);

        RuntimeException role = assertThrows(RuntimeException.class, () -> executerSql(destinataire,
                "UPDATE invitation SET role = 'ADMINISTRATEUR' WHERE id = ?", invitation));
        assertEquals("42501", etatSql(role), "colonne role non modifiable par argos_app");
        RuntimeException deplacement = assertThrows(RuntimeException.class, () -> executerSql(destinataire,
                "UPDATE invitation SET espace_id = ?::uuid WHERE id = ?", autre, invitation));
        assertEquals("42501", etatSql(deplacement), "colonne espace_id non modifiable par argos_app");
        RuntimeException revocation = assertThrows(RuntimeException.class, () -> executerSql(destinataire,
                "UPDATE invitation SET etat = 'REVOQUEE' WHERE id = ?", invitation));
        assertEquals("42501", etatSql(revocation), "le destinataire ne fait qu'accepter");
        RuntimeException administrateur = assertThrows(RuntimeException.class, () -> executerSql(destinataire,
                "INSERT INTO appartenance (espace_id, compte_id, role) VALUES (?::uuid, argos_compte_courant(), 'ADMINISTRATEUR')",
                espace));
        assertEquals("42501", etatSql(administrateur), "le rôle inséré doit être celui de l'invitation");

        // Le parcours prévu reste possible : entrer avec le rôle de l'invitation, puis l'accepter à son nom.
        assertThrows(Annulation.class, () -> executerSql(destinataire.dansEspace(UUID.fromString(espace)),
                "INSERT INTO appartenance (espace_id, compte_id, role) VALUES (?::uuid, argos_compte_courant(), 'LECTEUR')",
                espace));
        assertThrows(Annulation.class, () -> executerSql(destinataire,
                "UPDATE invitation SET etat = 'ACCEPTEE', destinataire_id = argos_compte_courant() WHERE id = ?", invitation));
        assertEquals("LECTEUR", db.string("SELECT role FROM invitation WHERE id = '" + invitation + "'"));
        assertEquals("EN_ATTENTE", db.string("SELECT etat FROM invitation WHERE id = '" + invitation + "'"));
    }

    /** argos_app ne lit ni le journal des refus ni les comptes sans lien avec le compte courant. */
    @Test
    void moindrePrivilegeSurLesComptesEtLeJournal() {
        Compte erin = compte("erin");
        UUID jules = Api.compte("jules");
        Contexte contexte = new Contexte(erin.id(), null, "erin@argos.test", null);

        RuntimeException journal = assertThrows(RuntimeException.class,
                () -> base.executer(contexte, c -> compter(c, "SELECT count(*) FROM refus_acces")));
        assertEquals("42501", etatSql(journal), "aucun droit d'argos_app sur le journal des refus");
        assertEquals(1L, (long) base.executer(contexte, c -> compter(c, "SELECT count(*) FROM compte WHERE id = '" + erin.id() + "'")));
        assertEquals(0L, (long) base.executer(contexte, c -> compter(c, "SELECT count(*) FROM compte WHERE id = '" + jules + "'")),
                "un compte sans espace commun ni invitation n'est pas lisible");
        assertEquals(0L, (long) base.executer(Contexte.aucun(), c -> compter(c, "SELECT count(*) FROM compte")),
                "sans contexte, aucun compte");
        assertEquals("f", db.string("SELECT has_function_privilege('public', 'argos_est_membre(uuid)', 'EXECUTE')::text")
                .substring(0, 1));
    }

    /** INV-1 en concurrence : un retrait de rôle attend la fin d'un autre changement dans le même espace. */
    @Test
    void inv1SurEnConcurrence() throws Exception {
        Compte alice = compte("alice");
        Compte erin = compte("erin");
        UUID espace = UUID.fromString(Api.creerEspace("alice", "Deux administrateurs"));
        Api.ajouterMembre("alice", espace.toString(), "erin", "ADMINISTRATEUR");

        ExecutorService executeur = Executors.newSingleThreadExecutor();
        try (Connection autre = dataSource.getConnection(); Statement st = autre.createStatement()) {
            // Transaction concurrente : erin perd son rôle d'administratrice, sans être encore validée.
            autre.setAutoCommit(false);
            st.execute("SELECT 1 FROM espace WHERE id = '" + espace + "' FOR UPDATE");
            st.executeUpdate("UPDATE appartenance SET role = 'LECTEUR' WHERE espace_id = '" + espace
                    + "' AND compte_id = '" + erin.id() + "'");

            Future<?> retrait = executeur.submit(() -> espaces.changerRole(alice, espace, alice.id(), Role.LECTEUR));
            Thread.sleep(1000);
            assertFalse(retrait.isDone(), "le second changement attend le verrou de l'espace");
            autre.commit();
            autre.setAutoCommit(true);

            ExecutionException e = assertThrows(ExecutionException.class, () -> retrait.get(30, TimeUnit.SECONDS));
            RegleViolee regle = assertInstanceOf(RegleViolee.class, e.getCause());
            assertEquals("DERNIER_ADMINISTRATEUR", regle.code());
        } finally {
            executeur.shutdownNow();
        }
        assertEquals(1, db.count("SELECT count(*) FROM appartenance WHERE espace_id = '" + espace
                + "' AND role = 'ADMINISTRATEUR'"));
    }

    private static long compter(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
