package io.argos.ingestion.culture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Lecture d'une réponse de l'API data.culture.gouv.fr, simulée (ajouter-profil-artiste, ART-05). */
class LecteurDataCultureTest {

    static final LecteurDataCulture.Champs CHAMPS = new LecteurDataCulture.Champs("identifiant", "titre", "organisme",
            "criteres", "lien", "discipline", "region", "date_limite");

    private static List<LecteurDataCulture.Enregistrement> lire(String json, int max) {
        return LecteurDataCulture.lire(json.getBytes(StandardCharsets.UTF_8), CHAMPS, max);
    }

    @Test
    void lesChampsSontLusEnTexteBrut() {
        List<LecteurDataCulture.Enregistrement> lus = lire("""
                {"total_count": 2, "results": [
                  {"identifiant": "AAP-1", "titre": "<b>Résidence</b> <script>alert(1)</script>arts visuels",
                   "organisme": "DRAC Occitanie", "criteres": "<p>Artistes professionnels</p>",
                   "lien": "https://www.culture.gouv.fr/aap-1", "discipline": ["Arts visuels", "Design"],
                   "region": "Occitanie", "date_limite": "2026-12-06"},
                  {"titre": "Aide sans date", "region": "Bretagne ; Normandie", "date_limite": "bientôt"}
                ]}""", 10);
        assertEquals(2, lus.size());
        LecteurDataCulture.Enregistrement a = lus.get(0);
        assertEquals("AAP-1", a.identifiant());
        assertEquals("Résidence arts visuels", a.titre());
        assertEquals("Artistes professionnels", a.criteres());
        assertEquals(List.of("Arts visuels", "Design"), a.disciplines());
        assertEquals(List.of("Occitanie"), a.territoires());
        assertEquals(LocalDate.of(2026, 12, 6), a.dateLimite());
        LecteurDataCulture.Enregistrement b = lus.get(1);
        assertEquals("Aide sans date", b.identifiant(), "sans identifiant : le titre en tient lieu");
        assertEquals(List.of("Bretagne", "Normandie"), b.territoires());
        assertNull(b.dateLimite(), "date illisible : date à confirmer");
    }

    @Test
    void lesDatesHorodateesSontRameneesAuJour() {
        assertEquals(LocalDate.of(2026, 12, 6), LecteurDataCulture.date("2026-12-06T23:59:00+01:00"));
        assertNull(LecteurDataCulture.date(""));
    }

    @Test
    void lesEnregistrementsSontBornesEtLesTitresVidesIgnores() {
        List<LecteurDataCulture.Enregistrement> lus = lire("""
                {"results": [{"titre": ""}, {"titre": "A"}, {"titre": "B"}, {"titre": "C"}, 42]}""", 2);
        assertEquals(List.of("A", "B"), lus.stream().map(LecteurDataCulture.Enregistrement::titre).toList());
    }

    @Test
    void uneReponseInattendueEstRefusee() {
        assertThrows(LecteurDataCulture.ReponseInvalide.class, () -> lire("<html></html>", 10));
        assertThrows(LecteurDataCulture.ReponseInvalide.class, () -> lire("{\"records\": []}", 10));
        assertThrows(LecteurDataCulture.ReponseInvalide.class,
                () -> lire("{\"results\": [], \"results\": []}", 10), "clé dupliquée refusée");
    }
}
