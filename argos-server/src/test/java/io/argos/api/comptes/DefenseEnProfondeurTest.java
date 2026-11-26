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
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Api;
import io.argos.testsupport.TestDb;
import io.argos.workspace.Compte;
import io.argos.workspace.ComptesService;
import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;
import io.argos.workspace.EspacesService;
import io.argos.commun.RegleViolee;
import io.argos.workspace.Role;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Défense en profondeur sous le rôle applicatif {@code argos_app}, non super-utilisateur (ADR-0016) :
 * invitations non réinscriptibles par leur destinataire, moindre privilège sur les comptes et le
 * journal des refus, invariant INV-1 sûr en concurrence.
 */
@QuarkusTest
class DefenseEnProfondeurTest {

    /** Contexte vide : aucun compte, sujet, courriel ni espace courant. */
    private static final Contexte SANS_CONTEXTE = new Contexte(null, null, null, null);

    /** Interrompt un traitement réussi pour annuler sa transaction. */
    private static final class Annulation extends RuntimeException {
    }

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    @Inject
    TestDb baseDeTest;

    @Inject
    ComptesService comptes;

    @Inject
    EspacesService espaces;

    private Compte compte(String utilisateur) {
        UUID id = Api.compte(utilisateur);
        return comptes.parSujet(baseDeTest.string("SELECT sujet_oidc FROM compte WHERE id = '" + id + "'")).orElseThrow();
    }

    private static String etatSql(RuntimeException erreur) {
        Throwable cause = erreur;
        while (cause != null && !(cause instanceof SQLException)) {
            cause = cause.getCause();
        }
        return cause == null ? null : ((SQLException) cause).getSQLState();
    }

    private void executerSql(Contexte contexte, String sql, Object... parametres) {
        base.executer(contexte, connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                for (int indice = 0; indice < parametres.length; indice++) {
                    requete.setObject(indice + 1, parametres[indice]);
                }
                requete.executeUpdate();
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
        assertEquals("LECTEUR", baseDeTest.string("SELECT role FROM invitation WHERE id = '" + invitation + "'"));
        assertEquals("EN_ATTENTE", baseDeTest.string("SELECT etat FROM invitation WHERE id = '" + invitation + "'"));
    }

    /** argos_app ne lit ni le journal des refus ni les comptes sans lien avec le compte courant. */
    @Test
    void moindrePrivilegeSurLesComptesEtLeJournal() {
        Compte erin = compte("erin");
        UUID jules = Api.compte("jules");
        Contexte contexte = new Contexte(erin.id(), null, "erin@argos.test", null);

        RuntimeException journal = assertThrows(RuntimeException.class,
                () -> base.executer(contexte, connexion -> compter(connexion, "SELECT count(*) FROM refus_acces")));
        assertEquals("42501", etatSql(journal), "aucun droit d'argos_app sur le journal des refus");
        assertEquals(1L, (long) base.executer(contexte,
                connexion -> compter(connexion, "SELECT count(*) FROM compte WHERE id = '" + erin.id() + "'")));
        assertEquals(0L, (long) base.executer(contexte,
                connexion -> compter(connexion, "SELECT count(*) FROM compte WHERE id = '" + jules + "'")),
                "un compte sans espace commun ni invitation n'est pas lisible");
        assertEquals(0L, (long) base.executer(SANS_CONTEXTE, connexion -> compter(connexion, "SELECT count(*) FROM compte")),
                "sans contexte, aucun compte");
        assertEquals("f", baseDeTest.string("SELECT has_function_privilege('public', 'argos_est_membre(uuid)', 'EXECUTE')::text")
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
        try (Connection autre = dataSource.getConnection(); Statement instruction = autre.createStatement()) {
            // Transaction concurrente : erin perd son rôle d'administratrice, sans être encore validée.
            autre.setAutoCommit(false);
            instruction.execute("SELECT 1 FROM espace WHERE id = '" + espace + "' FOR UPDATE");
            instruction.executeUpdate("UPDATE appartenance SET role = 'LECTEUR' WHERE espace_id = '" + espace
                    + "' AND compte_id = '" + erin.id() + "'");

            Future<?> retrait = executeur.submit(() -> espaces.changerRole(alice, espace, alice.id(), Role.LECTEUR));
            // Le second changement attend le verrou de l'espace : sa session est observée en attente d'un verrou.
            Awaitility.await("second changement en attente du verrou de l'espace").atMost(Duration.ofSeconds(10))
                    .until(() -> baseDeTest.count("SELECT count(*) FROM pg_locks WHERE NOT granted"), enAttente -> enAttente >= 1);
            assertFalse(retrait.isDone(), "le second changement attend le verrou de l'espace");
            autre.commit();
            autre.setAutoCommit(true);

            ExecutionException erreur = assertThrows(ExecutionException.class, () -> retrait.get(30, TimeUnit.SECONDS));
            RegleViolee regle = assertInstanceOf(RegleViolee.class, erreur.getCause());
            assertEquals("DERNIER_ADMINISTRATEUR", regle.code());
        } finally {
            executeur.shutdownNow();
        }
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM appartenance WHERE espace_id = '" + espace
                + "' AND role = 'ADMINISTRATEUR'"));
    }

    private static long compter(Connection connexion, String sql) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(sql); ResultSet resultat = requete.executeQuery()) {
            resultat.next();
            return resultat.getLong(1);
        }
    }
}
