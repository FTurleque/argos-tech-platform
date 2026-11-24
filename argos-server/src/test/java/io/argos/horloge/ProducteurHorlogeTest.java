package io.argos.horloge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** Producteur de l'horloge commune : fuseau {@code argos.alerting.fuseau} (AUD-QUA-10, Q-1). */
class ProducteurHorlogeTest {

    @Test
    void lHorlogeProduitePorteLeFuseauConfigure() {
        ProducteurHorloge producteur = new ProducteurHorloge();
        producteur.fuseau = "America/Montreal";

        Clock horloge = producteur.horloge();

        assertEquals(ZoneId.of("America/Montreal"), horloge.getZone());
    }
}
