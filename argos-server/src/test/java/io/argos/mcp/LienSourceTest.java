package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Lien public d'un avis cité par {@code check_dependency} : construit par Argos, identifiant encodé. */
class LienSourceTest {

    @Test
    void pageDeLAvisChezOsvEtALaNvd() {
        assertEquals("https://osv.dev/vulnerability/GHSA-abcd-0001", OutilsSecurite.lienSource("OSV", "GHSA-abcd-0001"));
        assertEquals("https://nvd.nist.gov/vuln/detail/CVE-2026-0001", OutilsSecurite.lienSource("nvd", "CVE-2026-0001"));
    }

    @Test
    void identifiantEncodeEtSourceSansPageConnue() {
        assertEquals("https://osv.dev/vulnerability/a%2F..%3Fx%3D1", OutilsSecurite.lienSource("OSV", "a/..?x=1"));
        assertNull(OutilsSecurite.lienSource("KEV", "CVE-2026-0001"));
        assertNull(OutilsSecurite.lienSource("OSV", " "));
        assertNull(OutilsSecurite.lienSource(null, "x"));
    }
}
