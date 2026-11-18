package io.argos.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Lien public d'un avis cité par {@code check_dependency} : construit par Argos, identifiant encodé. */
class LienSourceTest {

    @Test
    void pageDeLAvisChezOsvEtALaNvd() {
        assertEquals(Optional.of("https://osv.dev/vulnerability/GHSA-abcd-0001"),
                OutilsSecurite.lienSource("OSV", "GHSA-abcd-0001"));
        assertEquals(Optional.of("https://nvd.nist.gov/vuln/detail/CVE-2026-0001"),
                OutilsSecurite.lienSource("nvd", "CVE-2026-0001"));
    }

    @Test
    void identifiantEncodeEtSourceSansPageConnue() {
        assertEquals(Optional.of("https://osv.dev/vulnerability/a%2F..%3Fx%3D1"),
                OutilsSecurite.lienSource("OSV", "a/..?x=1"));
        assertEquals(Optional.empty(), OutilsSecurite.lienSource("KEV", "CVE-2026-0001"));
        assertEquals(Optional.empty(), OutilsSecurite.lienSource("OSV", " "));
        assertEquals(Optional.empty(), OutilsSecurite.lienSource(null, "x"));
    }
}
