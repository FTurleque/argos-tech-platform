package io.argos.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Horloge réglable des tests (changement {@code injecter-horloge-et-executeurs}, arc42 §8.11) : instant fixé, avancé
 * à la demande, sans attente réelle. Elle remplace le {@link Clock} du serveur par
 * {@code QuarkusMock.installMockForType(horloge, Clock.class)} ou par une substitution de champ.
 */
public final class HorlogeReglable extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId fuseau;

    public HorlogeReglable(Instant depart, ZoneId fuseau) {
        this(new AtomicReference<>(depart), fuseau);
    }

    private HorlogeReglable(AtomicReference<Instant> instant, ZoneId fuseau) {
        this.instant = instant;
        this.fuseau = fuseau;
    }

    /** Fixe l'instant courant. */
    public void regler(Instant nouvelInstant) {
        instant.set(nouvelInstant);
    }

    /** Avance l'horloge de la durée donnée. */
    public void avancer(Duration duree) {
        instant.updateAndGet(courant -> courant.plus(duree));
    }

    @Override
    public ZoneId getZone() {
        return fuseau;
    }

    /** Même instant réglable, vu dans un autre fuseau. */
    @Override
    public Clock withZone(ZoneId autreFuseau) {
        return new HorlogeReglable(instant, autreFuseau);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
