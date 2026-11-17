package io.argos.aigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Filtre des données sortantes (Q-4, QS-09) : secrets refusés, données personnelles masquées,
 * identifiants publics de veille conservés.
 */
class FiltreDonneesTest {

    @Test
    void secretsEtJetonsSontRefuses() {
        List<String> secrets = List.of(
                "clé : -----BEGIN RSA PRIVATE KEY-----\nMIIE...",
                "jeton eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U",
                "ghp_abcdefghijklmnopqrstuvwxyz0123456789",
                "glpat-abcdefghijklmnopqrstu",
                "AKIAABCDEFGHIJKLMNOP",
                "sk-abcdefghijklmnopqrstuvwxyz",
                "Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345",
                "password=Sup3rSecret!",
                "mot de passe : hunter2hunter2");
        for (String s : secrets) {
            assertTrue(FiltreDonnees.filtrer(s).secret(), s);
        }
    }

    @Test
    void donneesPersonnellesMasquees() {
        FiltreDonnees.Resultat r = FiltreDonnees.filtrer(
                "Contact : jean.dupont@exemple.fr ou 06 12 34 56 78, IBAN FR76 3000 6000 0112 3456 7890 189.");
        assertFalse(r.secret());
        assertEquals(3, r.masquees(), r.texte());
        assertFalse(r.texte().contains("jean.dupont"));
        assertFalse(r.texte().contains("06 12 34 56 78"));
        assertFalse(r.texte().contains("3000 6000"));
        assertTrue(r.texte().contains("[courriel masqué]"));
    }

    @Test
    void identifiantsPublicsConserves() {
        String texte = "CVE-2026-12345 touche org.exemple:lib 1.2.3 (GHSA-abcd-efgh-ijkl), corrigé en 1.3.0 le 2026-11-15 ; "
                + "hôte 192.168.10.20.";
        FiltreDonnees.Resultat r = FiltreDonnees.filtrer(texte);
        assertFalse(r.secret());
        assertEquals(0, r.masquees(), r.texte());
        assertEquals(texte, r.texte());
    }
}
