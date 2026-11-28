package io.argos.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Horaires des résumés périodiques (ALR-04, décision Q-3) lus dans la configuration livrée, profil de test, sans
 * démarrer l'application : résumé quotidien à 08:00 et hebdomadaire le lundi à 08:00, heure de Paris.
 */
class ResumesPeriodiquesTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final int ANNEE = 2026;
    private static final int NOVEMBRE = 11;
    private static final int MERCREDI_18 = 18;
    private static final int JEUDI_19 = 19;
    private static final int LUNDI_23 = 23;
    private static final int HEURE_DU_RESUME = 8;
    private static final int HEURE_DU_TEST = 9;
    private static final int DEMI_HEURE = 30;

    private static Config configuration() throws IOException {
        URL proprietes = Thread.currentThread().getContextClassLoader().getResource("application.properties");
        assertNotNull(proprietes, "application.properties sur le chemin de classes");
        return new SmallRyeConfigBuilder().withProfile("test").addDefaultInterceptors().addDefaultSources()
                .withSources(new PropertiesConfigSource(proprietes)).build();
    }

    @Test
    void horairesDesResumesConformesALaDecisionQ3() throws IOException {
        Config configuration = configuration();
        assertEquals("Europe/Paris", configuration.getValue("argos.alerting.fuseau", String.class));
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
        // Horloge simulée : un mercredi à 09:30, heure de Paris.
        ZonedDateTime mercredi = ZonedDateTime.of(ANNEE, NOVEMBRE, MERCREDI_18, HEURE_DU_TEST, DEMI_HEURE, 0, 0, PARIS);

        ZonedDateTime quotidien = ExecutionTime.forCron(parser.parse(
                configuration.getValue("argos.alerting.resume-quotidien.cron", String.class))).nextExecution(mercredi).orElseThrow();
        ZonedDateTime hebdo = ExecutionTime.forCron(parser.parse(
                configuration.getValue("argos.alerting.resume-hebdomadaire.cron", String.class))).nextExecution(mercredi)
                .orElseThrow();

        assertEquals(ZonedDateTime.of(ANNEE, NOVEMBRE, JEUDI_19, HEURE_DU_RESUME, 0, 0, 0, PARIS), quotidien);
        assertEquals(ZonedDateTime.of(ANNEE, NOVEMBRE, LUNDI_23, HEURE_DU_RESUME, 0, 0, 0, PARIS), hebdo);
        assertEquals(DayOfWeek.MONDAY, hebdo.getDayOfWeek());
    }
}
