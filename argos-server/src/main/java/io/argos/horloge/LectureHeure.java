package io.argos.horloge;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Dates lues sur l'horloge commune (arc42 §8.16) : seul endroit, avec {@link ProducteurHorloge}, où le serveur
 * convertit l'heure en date locale. Un bean lit l'instant par {@code horloge.instant()} et la date du jour par
 * {@link #jour(Clock)}, au fuseau de l'horloge reçue ({@code horloge.withZone(...)} pour un autre fuseau).
 */
public final class LectureHeure {

    private LectureHeure() {
    }

    /** Date du jour au fuseau de l'horloge. */
    public static LocalDate jour(Clock horloge) {
        return LocalDate.now(horloge);
    }

    /** Date et heure avec le décalage du fuseau de l'horloge. */
    public static OffsetDateTime dateHeure(Clock horloge) {
        return OffsetDateTime.now(horloge);
    }
}
