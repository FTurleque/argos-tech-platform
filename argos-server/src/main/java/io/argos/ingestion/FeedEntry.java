package io.argos.ingestion;

import java.time.Instant;
import java.util.Comparator;

/**
 * Avis annoncé par une source comme publié ou modifié, avec sa date de modification.
 * La position de curseur associée ordonne les avis par (date, identifiant).
 */
public record FeedEntry(String externalId, Instant modified) {

    public static final Comparator<FeedEntry> ORDER =
            Comparator.comparing(FeedEntry::modified).thenComparing(FeedEntry::externalId);

    /** Position de curseur atteinte une fois cet avis enregistré durablement (COL-05). */
    public String position() {
        return modified + "|" + externalId;
    }

    /** Vrai si l'avis est postérieur à la position donnée ({@code null} : aucun curseur). */
    public boolean isAfter(String position) {
        if (position == null || position.isBlank()) {
            return true;
        }
        int sep = position.indexOf('|');
        Instant at = Instant.parse(sep < 0 ? position : position.substring(0, sep));
        String id = sep < 0 ? "" : position.substring(sep + 1);
        int c = modified.compareTo(at);
        return c > 0 || (c == 0 && externalId.compareTo(id) > 0);
    }
}
