package io.argos.signal.opportunite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.argos.catalog.profil.ListeObjets;
import io.argos.catalog.profil.ValeurListe;

/** Correspondance des valeurs collectées avec les listes du profil, et liens sûrs (ajouter-profil-artiste). */
class CorrespondanceListesTest {

    private static final Optional<ListeObjets> DISCIPLINES = Optional.of(new ListeObjets("discipline", List.of(
            new ValeurListe("arts-visuels", "Arts visuels"), new ValeurListe("design-metiers-art", "Design et métiers d'art"),
            new ValeurListe("cinema-audiovisuel", "Cinéma et audiovisuel"))));
    private static final Optional<ListeObjets> TERRITOIRES = Optional.of(new ListeObjets("territoire", List.of(
            new ValeurListe("france", "France entière"), new ValeurListe("ile-de-france", "Île-de-France"),
            new ValeurListe("provence-alpes-cote-d-azur", "Provence-Alpes-Côte d'Azur"))));

    @Test
    void lesLibellesIdentifiantsEtSynonymesCorrespondent() {
        assertEquals(List.of("arts-visuels", "design-metiers-art", "cinema-audiovisuel"), CorrespondanceListes.identifiants(
                List.of("ARTS VISUELS", "Design et métiers d’art", "Cinéma", "Peinture"), DISCIPLINES));
        assertEquals(List.of("ile-de-france", "provence-alpes-cote-d-azur", "france"), CorrespondanceListes.identifiants(
                List.of("ILE DE FRANCE", "Provence-Alpes-Côte d'Azur", "National"), TERRITOIRES));
        assertEquals(List.of(), CorrespondanceListes.identifiants(List.of("Occitanie"), Optional.empty()));
    }

    @Test
    void seulsLesLiensHttpEtHttpsSontConserves() {
        assertEquals("https://www.culture.gouv.fr/aap", CorrespondanceListes.lienSur("https://www.culture.gouv.fr/aap"));
        assertNull(CorrespondanceListes.lienSur("javascript:alert(1)"));
        assertNull(CorrespondanceListes.lienSur("data:text/html,x"));
        assertNull(CorrespondanceListes.lienSur("https://moi:secret@exemple.test/"));
        assertNull(CorrespondanceListes.lienSur("/relatif"));
        assertNull(CorrespondanceListes.lienSur(null));
    }
}
