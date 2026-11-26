package io.argos.api.comptes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;
import io.argos.commun.Jdbc;
import io.argos.testsupport.Api;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Vue {@code compte_nom_public} publiée par {@code workspace} (ADR-0037, migration V23) : elle suit la RLS de la
 * table {@code compte}, comme une lecture directe sous le rôle applicatif (ACC-04).
 */
@QuarkusTest
class VueNomCompteTest {

    private static final String LECTURE = "SELECT id FROM compte_nom_public";

    @Inject
    ContexteBase base;

    private List<UUID> identifiantsVisibles(Contexte contexte) {
        return base.executer(contexte, connexion -> Jdbc.lister(connexion, LECTURE, Jdbc.sansParametre(),
                resultat -> resultat.getObject(1, UUID.class)));
    }

    @Test
    void sansContexteLaVueNeRenvoieRien() {
        assertEquals(List.of(), identifiantsVisibles(new Contexte(null, null, null, null)));
    }

    private List<UUID> identifiantsDeLaTable(Contexte contexte) {
        return base.executer(contexte, connexion -> Jdbc.lister(connexion, "SELECT id FROM compte", Jdbc.sansParametre(),
                resultat -> resultat.getObject(1, UUID.class)));
    }

    @Test
    void laVueNExposeQueLesComptesVisiblesDuCompteCourant() {
        String espace = Api.creerEspace("alice", "Espace de la vue des noms");
        Api.ajouterMembre("alice", espace, "frank", "LECTEUR");
        UUID alice = UUID.fromString(Api.idCompte("alice"));
        UUID frank = UUID.fromString(Api.idCompte("frank"));
        Api.assurerCompte("carla");
        UUID etranger = UUID.fromString(Api.idCompte("carla"));

        List<UUID> visiblesParFrank = identifiantsVisibles(Contexte.duCompte(frank));
        assertTrue(visiblesParFrank.contains(frank));
        assertTrue(visiblesParFrank.contains(alice), "un membre du même espace est visible");

        List<UUID> visiblesParCarla = identifiantsVisibles(Contexte.duCompte(etranger));
        assertTrue(visiblesParCarla.contains(etranger));
        assertEquals(Set.copyOf(identifiantsDeLaTable(Contexte.duCompte(etranger))), Set.copyOf(visiblesParCarla),
                "la vue suit la politique RLS de la table compte");
    }
}
