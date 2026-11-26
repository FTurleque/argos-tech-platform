package io.argos.commun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;


import java.sql.SQLException;
import java.util.UUID;

import jakarta.inject.Inject;

import io.argos.commun.ContexteBase.Contexte;
import io.argos.testsupport.TestDb;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Frontière transactionnelle unique (ADR-0037) : validation, annulation, rôle applicatif et traduction des erreurs SQL. */
@QuarkusTest
class ContexteBaseTransactionTest {

    /** Contexte vide : aucun compte, sujet, courriel ni espace courant. */
    private static final Contexte SANS_CONTEXTE = new Contexte(null, null, null, null);

    private static final String TABLE = "contexte_essai";

    @Inject
    ContexteBase base;

    @Inject
    TestDb baseDeTest;

    @BeforeEach
    void creerTable() {
        baseDeTest.exec("CREATE TABLE " + TABLE + " (valeur integer)");
        baseDeTest.exec("GRANT SELECT, INSERT ON " + TABLE + " TO argos_app");
    }

    @AfterEach
    void supprimerTable() {
        baseDeTest.exec("DROP TABLE IF EXISTS " + TABLE);
    }

    private void inserer(java.sql.Connection connexion, int valeur) {
        Jdbc.modifier(connexion, "INSERT INTO " + TABLE + " VALUES (?)", Jdbc.parametres(valeur));
    }

    @Test
    void leTravailReussiEstValideSousLeRoleApplicatif() {
        String role = base.executer(Contexte.duCompte(UUID.randomUUID()), connexion -> {
            inserer(connexion, 1);
            return Jdbc.unique(connexion, "SELECT current_user", Jdbc.sansParametre(), resultat -> resultat.getString(1))
                    .orElseThrow();
        });
        assertEquals("argos_app", role);
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM " + TABLE));
    }

    @Test
    void uneExceptionMetierAnnuleLaTransactionEtEstRelanceeTelleQuelle() {
        DonneeInvalide refus = new DonneeInvalide("DONNEE_INVALIDE", "refus", "valeur");
        DonneeInvalide relancee = assertThrows(DonneeInvalide.class, () -> base.executer(SANS_CONTEXTE, connexion -> {
            inserer(connexion, 2);
            throw refus;
        }));
        assertEquals(refus, relancee);
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM " + TABLE));
    }

    @Test
    void uneErreurSqlAnnuleLaTransactionEtEstTraduiteEnErreurBase() {
        ErreurBase erreur = assertThrows(ErreurBase.class, () -> base.executer(SANS_CONTEXTE, connexion -> {
            inserer(connexion, 3);
            throw new SQLException("panne simulée");
        }));
        assertInstanceOf(SQLException.class, erreur.getCause());
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM " + TABLE));
    }

    @Test
    void deuxExecutionsSuccessivesSontIndependantes() {
        base.executer(SANS_CONTEXTE, connexion -> {
            inserer(connexion, 4);
            return Boolean.TRUE;
        });
        assertThrows(ErreurBase.class, () -> base.executer(SANS_CONTEXTE, connexion -> {
            inserer(connexion, 5);
            throw new SQLException("panne simulée");
        }));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM " + TABLE));
    }
}
