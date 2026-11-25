package io.argos.commun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ConversionsTest {

    private static final Instant INSTANT = Instant.parse("2026-11-25T10:15:30Z");

    @Test
    void utcConserveLInstantEtLeDecalageZero() {
        assertEquals(INSTANT, Conversions.utc(INSTANT).toInstant());
        assertEquals(ZoneOffset.UTC, Conversions.utc(INSTANT).getOffset());
    }

    @Test
    void utcDUnInstantAbsentEstAbsent() {
        assertNull(Conversions.utc(null));
    }

    @Test
    void coupeAuDelaDeLaLongueurMax() {
        assertEquals("abc", Conversions.couper("abcdef", 3));
    }

    @Test
    void neCoupePasUnTexteCourtOuAbsent() {
        assertEquals("abc", Conversions.couper("abc", 3));
        assertEquals("", Conversions.couper("", 3));
        assertNull(Conversions.couper(null, 3));
    }
}
