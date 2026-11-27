package io.argos.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import io.argos.testsupport.ProprietesApplication;
import io.quarkus.scheduler.Scheduled;

/** INV-07-S4 : cadence de l'import planifié lue dans la configuration, au fuseau configuré, sans recompilation. */
class ImportPlanifieCadenceTest {

    private static final int ANNEE = 2026;
    private static final int NOVEMBRE = 11;
    private static final int VEILLE = 19;
    private static final int LENDEMAIN = 20;
    private static final int QUINZE_HEURES = 15;
    private static final int DEUX_HEURES = 2;
    private static final int TROIS_HEURES = 3;

    @Test
    void inv07S4CadenceConfiguree() throws NoSuchMethodException {
        Scheduled planification = ImportPlanifie.class.getDeclaredMethod("planifie").getAnnotation(Scheduled.class);
        assertEquals("inventory-import-quotidien", planification.identity());
        assertEquals("{argos.inventory.import.cron}", planification.cron(), "expression lue dans la configuration");
        assertEquals("{argos.alerting.fuseau}", planification.timeZone(), "fuseau lu dans la configuration");
        assertEquals(Scheduled.ConcurrentExecution.SKIP, planification.concurrentExecution());
        String cron = ProprietesApplication.parDefaut("argos.inventory.import.cron");
        assertEquals("0 0 3 * * ?", cron, "03:00 par défaut ([PROPOSITION] P-12)");
        assertEquals("${ARGOS_INVENTORY_IMPORT_CRON:0 0 3 * * ?}", ProprietesApplication.brute("argos.inventory.import.cron"),
                "expression remplaçable par la variable d'environnement");

        // Expression réglée à 02:00 (ARGOS_INVENTORY_IMPORT_CRON) : échéance suivante à 02:00 au fuseau configuré.
        ZoneId fuseau = ZoneId.of(ProprietesApplication.parDefaut("argos.alerting.fuseau"));
        CronParser parseur = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
        ZonedDateTime depuis = ZonedDateTime.of(ANNEE, NOVEMBRE, VEILLE, QUINZE_HEURES, 0, 0, 0, fuseau);
        assertEquals(ZonedDateTime.of(ANNEE, NOVEMBRE, LENDEMAIN, DEUX_HEURES, 0, 0, 0, fuseau),
                ExecutionTime.forCron(parseur.parse("0 0 2 * * ?")).nextExecution(depuis).orElseThrow());
        assertEquals(ZonedDateTime.of(ANNEE, NOVEMBRE, LENDEMAIN, TROIS_HEURES, 0, 0, 0, fuseau),
                ExecutionTime.forCron(parseur.parse(cron)).nextExecution(depuis).orElseThrow());
    }
}
