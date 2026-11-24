package io.argos.horloge;

import java.time.Clock;
import java.time.ZoneId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Seule source de l'heure murale du serveur (AUD-QUA-10, arc42 §8.16) : un {@link Clock} au fuseau
 * {@code argos.alerting.fuseau} (décision Q-1 du changement {@code injecter-horloge-et-executeurs} : fuseau configuré),
 * injecté dans chaque bean qui lit l'heure ({@code horloge.instant()}, {@link LectureHeure#jour(Clock)}). Une classe
 * hors CDI reçoit l'instant en paramètre. Les tests remplacent ce bean par une horloge réglable. Les durées mesurées
 * (délais, chronométrages) utilisent l'horloge monotone {@code System.nanoTime()}, qui n'est pas l'heure murale.
 */
@ApplicationScoped
public class ProducteurHorloge {

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @Produces
    @ApplicationScoped
    Clock horloge() {
        return Clock.system(ZoneId.of(fuseau));
    }
}
