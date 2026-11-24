package io.argos.architecture.exemples;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Lectures directes de l'heure système, contre lesquelles {@code HorlogeInjecteeTest} vérifie que sa règle les refuse.
 * Jamais exécutée.
 */
public final class LectureDirecteHeure {

    private LectureDirecteHeure() {
    }

    public static Instant instant() {
        return Instant.now();
    }

    public static LocalDate jour() {
        return LocalDate.now();
    }

    public static long millisecondes() {
        return System.currentTimeMillis();
    }
}
