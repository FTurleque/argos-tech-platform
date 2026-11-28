package io.argos.api.preference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.api.Erreur;
import io.argos.api.UtilisateurCourant;
import io.argos.catalog.miseenpage.CatalogueMisesEnPage;
import io.argos.catalog.profil.CatalogueProfils;
import io.argos.workspace.Compte;
import io.argos.workspace.preference.PreferencesRepository;
import io.argos.workspace.preference.PreferencesService;
import io.argos.workspace.preference.PreferencesUtilisateur;

/**
 * Préférences de l'utilisateur courant hors du conteneur (AFF-01, AFF-03, AFF-04, PRF-01, INV-2) : la ressource est
 * construite avec le catalogue livré, un utilisateur courant fixe et des préférences gardées en mémoire. La clé des
 * préférences est l'identifiant du compte Argos. L'authentification, l'isolement en base (RLS) et l'enregistrement réel
 * restent vérifiés par {@code PreferencesResourceTest}.
 */
class PreferencesResourceUnitaireTest {

    private static final int REFUS = Response.Status.BAD_REQUEST.getStatusCode();

    private final Compte ines = new Compte(UUID.randomUUID(), "sujet-ines", "Inès", "ines@argos.test", false, null);
    private PreferencesEnMemoire enregistrees;
    private PreferencesResource ressource;

    @BeforeEach
    void preparer() {
        enregistrees = new PreferencesEnMemoire();
        ressource = ressource(new CatalogueMisesEnPage(new ObjectMapper(), Optional.empty()));
    }

    private PreferencesResource ressource(CatalogueMisesEnPage misesEnPage) {
        CatalogueProfils profils = new CatalogueProfils(new ObjectMapper(), List.of("developpeur", "expert-metier", "artiste"),
                Optional.empty());
        return new PreferencesResource(new UtilisateurFixe(ines), new PreferencesService(enregistrees),
                new ResolutionPreferences(misesEnPage, profils, List.of("developpeur")), misesEnPage, profils);
    }

    /** Utilisateur courant établi d'avance, sans jeton. */
    private static final class UtilisateurFixe extends UtilisateurCourant {

        private final Compte compte;

        UtilisateurFixe(Compte compte) {
            this.compte = compte;
        }

        @Override
        public Compte compte() {
            return compte;
        }
    }

    /** Préférences gardées en mémoire, par identifiant de compte ; profils triés comme en base. */
    private static final class PreferencesEnMemoire extends PreferencesRepository {

        private final Map<UUID, PreferencesUtilisateur> parCompte = new HashMap<>();

        PreferencesEnMemoire() {
            super(null, null);
        }

        @Override
        public PreferencesUtilisateur lire(UUID compte) {
            return parCompte.getOrDefault(compte, PreferencesUtilisateur.aucune(compte));
        }

        @Override
        public void enregistrerMiseEnPage(UUID compte, String miseEnPage) {
            parCompte.put(compte, new PreferencesUtilisateur(compte, Optional.of(miseEnPage), lire(compte).profilsActifs()));
        }

        @Override
        public void enregistrerProfils(UUID compte, Set<String> profilsActifs) {
            List<String> tries = new ArrayList<>(profilsActifs);
            tries.sort(null);
            parCompte.put(compte, new PreferencesUtilisateur(compte, lire(compte).miseEnPage(), tries));
        }
    }

    private static Erreur refus(WebApplicationException exception) {
        assertEquals(REFUS, exception.getResponse().getStatus());
        return (Erreur) exception.getResponse().getEntity();
    }

    private WebApplicationException miseEnPageRefusee(String miseEnPage) {
        return assertThrows(WebApplicationException.class,
                () -> ressource.choisirMiseEnPage(new PreferencesResource.ChoixMiseEnPage(miseEnPage)));
    }

    private WebApplicationException profilsRefuses(String... profils) {
        return assertThrows(WebApplicationException.class,
                () -> ressource.remplacerProfils(new PreferencesResource.ChoixProfils(List.of(profils))));
    }

    /** AFF-04-S1 : sans choix, la mise en page classique s'applique, sans message. */
    @Test
    void aff04S1SansChoixLaMiseEnPageClassiqueSApplique() {
        PreferencesEffectives effectives = ressource.lire();

        assertEquals("classique", effectives.miseEnPage());
        assertNull(effectives.miseEnPageChoisie());
        assertFalse(effectives.miseEnPageIndisponible());
        assertNull(effectives.message());
    }

    /** AFF-01-S1, AFF-03 : une mise en page publiée est enregistrée sous l'identifiant du compte et relue par tout client. */
    @Test
    void aff01S1ChoixDUneMiseEnPagePubliee() {
        ressource.choisirMiseEnPage(new PreferencesResource.ChoixMiseEnPage("classique"));

        PreferencesEffectives choisie = ressource.choisirMiseEnPage(new PreferencesResource.ChoixMiseEnPage("gazette"));

        assertEquals("gazette", choisie.miseEnPage());
        assertEquals("gazette", choisie.miseEnPageChoisie());
        assertEquals("gazette", ressource.lire().miseEnPage());
        assertEquals(Optional.of("gazette"), enregistrees.parCompte.get(ines.id()).miseEnPage(),
                "clé des préférences : identifiant du compte Argos");
    }

    /** AFF-01-S2 : une mise en page absente du catalogue est refusée et la précédente est conservée. */
    @Test
    void aff01S2MiseEnPageAbsenteDuCatalogue() {
        ressource.choisirMiseEnPage(new PreferencesResource.ChoixMiseEnPage("journal"));

        Erreur erreur = refus(miseEnPageRefusee("psychedelique"));

        assertEquals("MISE_EN_PAGE_INCONNUE", erreur.code());
        assertTrue(erreur.message().contains("n'existe pas"), erreur.message());
        assertEquals("journal", ressource.lire().miseEnPage());
    }

    @Test
    void uneDemandeSansMiseEnPageEstRefusee() {
        assertEquals("MISE_EN_PAGE_INCONNUE", refus(miseEnPageRefusee(null)).code());
        assertEquals("MISE_EN_PAGE_INCONNUE", refus(assertThrows(WebApplicationException.class,
                () -> ressource.choisirMiseEnPage(null))).code());
    }

    /** PRF-01-S1 : deux profils se cumulent. */
    @Test
    void prf01S1CumulDeDeuxProfils() {
        ressource.remplacerProfils(new PreferencesResource.ChoixProfils(List.of("developpeur")));

        PreferencesEffectives effectives = ressource.remplacerProfils(
                new PreferencesResource.ChoixProfils(List.of("developpeur", "expert-metier")));

        assertEquals(Set.of("developpeur", "expert-metier"), Set.copyOf(effectives.profilsActifs()));
        assertFalse(effectives.profilsParDefaut());
    }

    /** PRF-01-S2 : désactiver le dernier profil est refusé ; le profil actif est conservé. */
    @Test
    void prf01S2DesactivationDuDernierProfil() {
        ressource.remplacerProfils(new PreferencesResource.ChoixProfils(List.of("expert-metier")));

        Erreur erreur = refus(profilsRefuses());

        assertEquals("AU_MOINS_UN_PROFIL", erreur.code());
        assertTrue(erreur.message().contains("Au moins un profil"), erreur.message());
        assertEquals(List.of("expert-metier"), ressource.lire().profilsActifs());
    }

    /** PRF-01-S3 : un profil absent du catalogue est refusé ; les profils actifs sont conservés. */
    @Test
    void prf01S3ProfilAbsentDuCatalogue() {
        ressource.remplacerProfils(new PreferencesResource.ChoixProfils(List.of("developpeur")));

        Erreur erreur = refus(profilsRefuses("developpeur", "astrologue"));

        assertEquals("PROFIL_INCONNU", erreur.code());
        assertTrue(erreur.message().contains("n'existe pas"), erreur.message());
        assertEquals(List.of("developpeur"), ressource.lire().profilsActifs());
    }

    /** AFF-04-S2 : une mise en page choisie puis retirée du catalogue laisse place à classique, avec un message. */
    @Test
    void aff04S2MiseEnPageRetireeClassiqueEtMessage() {
        enregistrees.enregistrerMiseEnPage(ines.id(), "fantaisie");
        PreferencesResource apresRetrait = ressource(new CatalogueMisesEnPage(new ObjectMapper(), Optional.of(Set.of("fantaisie"))));

        PreferencesEffectives effectives = apresRetrait.lire();

        assertEquals("classique", effectives.miseEnPage());
        assertEquals("fantaisie", effectives.miseEnPageChoisie());
        assertTrue(effectives.miseEnPageIndisponible());
        assertTrue(effectives.message().contains("n'est plus disponible"), effectives.message());
    }

    /** INV-2 : une mise en page retirée ne figure plus au catalogue publié et ne peut plus être choisie. */
    @Test
    void uneMiseEnPageRetireeNePeutPlusEtreChoisie() {
        CatalogueMisesEnPage sansFantaisie = new CatalogueMisesEnPage(new ObjectMapper(), Optional.of(Set.of("fantaisie")));
        ressource = ressource(sansFantaisie);

        assertTrue(sansFantaisie.publiees().stream().noneMatch(miseEnPage -> miseEnPage.identifiant().equals("fantaisie")));
        assertEquals("MISE_EN_PAGE_INCONNUE", refus(miseEnPageRefusee("fantaisie")).code());
    }

    @Test
    void sansChoixLesProfilsParDefautSAppliquent() {
        PreferencesEffectives effectives = ressource.lire();

        assertEquals(List.of("developpeur"), effectives.profilsActifs());
        assertTrue(effectives.profilsParDefaut());
    }
}
