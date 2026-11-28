package io.argos.commun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Utilitaire JDBC commun (ADR-0037) : lecture, écriture, absence de ligne, traduction unique des erreurs SQL. */
@QuarkusTest
class JdbcTest {

    private static final String VALEURS =
            "SELECT valeur FROM (VALUES (1), (2), (3)) AS jeu(valeur) WHERE valeur >= ? ORDER BY valeur";

    @Inject
    Jdbc jdbc;

    @Inject
    DataSource dataSource;

    @Test
    void listeLesLignesDansLOrdreDuResultat() {
        List<Integer> valeurs = jdbc.lister(VALEURS, Jdbc.parametres(2), resultat -> resultat.getInt("valeur"));
        assertEquals(List.of(2, 3), valeurs);
    }

    @Test
    void listeVideQuandAucuneLigneNeCorrespond() {
        assertEquals(List.of(), jdbc.lister(VALEURS, Jdbc.parametres(9), resultat -> resultat.getInt(1)));
    }

    @Test
    void uniqueRendLaPremiereLigneOuVide() {
        assertEquals(Optional.of(1), jdbc.unique(VALEURS, Jdbc.parametres(1), resultat -> resultat.getInt(1)));
        assertEquals(Optional.empty(), jdbc.unique(VALEURS, Jdbc.parametres(9), resultat -> resultat.getInt(1)));
    }

    @Test
    void lireUnInstantNulDonneNul() {
        Optional<Instant> instant = jdbc.unique("SELECT CAST(NULL AS timestamptz)", Jdbc.sansParametre(),
                resultat -> Conversions.instant(resultat, 1));
        assertTrue(instant.isEmpty());
    }

    @Test
    void lireUnInstantDeLaBase() {
        Instant attendu = Instant.parse("2026-11-25T10:15:30Z");
        Instant instant = jdbc.unique("SELECT CAST(? AS timestamptz)", Jdbc.parametres(Conversions.utc(attendu)),
                resultat -> Conversions.instant(resultat, 1)).orElseThrow();
        assertEquals(Instant.parse("2026-11-25T10:15:30Z"), instant);
    }

    @Test
    void modifieSurUneConnexionDonneeEtCompteLesLignes() throws SQLException {
        try (Connection connexion = dataSource.getConnection()) {
            assertEquals(0, Jdbc.modifier(connexion, "DO 'BEGIN END'", Jdbc.sansParametre()));
            assertEquals(Optional.of(1), Jdbc.unique(connexion, "SELECT 1", Jdbc.sansParametre(),
                    resultat -> resultat.getInt(1)));
            assertEquals(List.of(1), Jdbc.lister(connexion, "SELECT 1", Jdbc.sansParametre(),
                    resultat -> resultat.getInt(1)));
        }
    }

    @Test
    void modifieSurLaSourceDeDonnees() {
        assertEquals(0, jdbc.modifier("DO 'BEGIN END'", Jdbc.sansParametre()));
    }

    @Test
    void uneErreurSqlEstTraduiteEnErreurBaseAvecSaCause() {
        ErreurBase erreur = assertThrows(ErreurBase.class,
                () -> jdbc.lister("SELECT * FROM table_inexistante", Jdbc.sansParametre(), resultat -> 0));
        assertInstanceOf(SQLException.class, erreur.getCause());
        assertTrue(erreur.getMessage().contains("table_inexistante"));
    }

}
