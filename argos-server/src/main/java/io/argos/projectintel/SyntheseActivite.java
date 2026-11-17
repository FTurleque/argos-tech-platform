package io.argos.projectintel;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Synthèse IA marquée de l'activité d'un projet (PIN-04).
 *
 * <p>La synthèse est demandée au point d'extension {@link SyntheseIa} avec un délai borné, à partir
 * des seuls indicateurs affichés. Elle porte toujours la mention « produite par l'IA » et la liste des
 * indicateurs dont elle découle. Elle n'est jamais source d'un indicateur (PIN-02) : une réponse qui
 * cite un nombre absent des indicateurs affichés est refusée (état {@code REFUSEE}).
 */
@ApplicationScoped
public class SyntheseActivite {

    private static final Logger LOG = Logger.getLogger(SyntheseActivite.class);

    /** Mention portée par toute synthèse (PIN-04-S1). */
    public static final String MENTION = "produite par l'IA";

    /** Longueur maximale d'une synthèse restituée. */
    static final int LONGUEUR_MAX = 2000;

    private static final Pattern NOMBRE = Pattern.compile("\\d+");

    private static final ExecutorService EXECUTEUR_IA = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), r -> {
                Thread t = new Thread(r, "argos-synthese-activite");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.AbortPolicy());

    /**
     * Synthèse restituée.
     *
     * @param etat {@code DISPONIBLE}, {@code INDISPONIBLE}, {@code PLAFOND_ATTEINT}, {@code REFUSEE} ou
     *             {@code SANS_DONNEES} (aucune collecte réussie)
     * @param texte texte de la synthèse, absent hors de l'état {@code DISPONIBLE}
     * @param mention toujours « produite par l'IA »
     * @param periode période des indicateurs dont elle découle
     * @param indicateurs noms des indicateurs dont elle découle, tels que restitués par l'API d'activité
     */
    public record Synthese(String etat, String texte, String mention, VueActivite.Periode periode,
            List<String> indicateurs) {
    }

    @Inject
    Instance<SyntheseIa> ia;

    @ConfigProperty(name = "argos.activite.synthese.delai-max", defaultValue = "PT20S")
    Duration delaiMax;

    /** Synthèse des indicateurs {@code vue}. */
    public Synthese produire(VueActivite vue) {
        List<String> sources = indicateurs(vue);
        if (vue.fraicheur().derniereMiseAJour() == null) {
            return new Synthese("SANS_DONNEES", null, MENTION, vue.periode(), sources);
        }
        if (!ia.isResolvable()) {
            return new Synthese("INDISPONIBLE", null, MENTION, vue.periode(), sources);
        }
        SyntheseIa modele = ia.get();
        if (!modele.budgetDisponible()) {
            return new Synthese("PLAFOND_ATTEINT", null, MENTION, vue.periode(), sources);
        }
        Future<String> demande;
        try {
            demande = EXECUTEUR_IA.submit(() -> modele.synthetiser(vue));
        } catch (RejectedExecutionException e) {
            return new Synthese("INDISPONIBLE", null, MENTION, vue.periode(), sources);
        }
        String texte;
        try {
            texte = demande.get(delaiMax.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            demande.cancel(true);
            Thread.currentThread().interrupt();
            return new Synthese("INDISPONIBLE", null, MENTION, vue.periode(), sources);
        } catch (Exception e) {
            demande.cancel(true);
            LOG.infof("synthèse de l'activité du projet %s indisponible : %s", vue.projectId(), e.toString());
            return new Synthese("INDISPONIBLE", null, MENTION, vue.periode(), sources);
        }
        String propre = nettoyer(texte);
        if (propre.isEmpty() || !fondeeSurLesIndicateurs(propre, vue)) {
            return new Synthese("REFUSEE", null, MENTION, vue.periode(), sources);
        }
        return new Synthese("DISPONIBLE", propre, MENTION, vue.periode(), sources);
    }

    /** Indicateurs restitués, dans l'ordre de l'API : la synthèse y renvoie. */
    static List<String> indicateurs(VueActivite vue) {
        List<String> noms = new ArrayList<>(List.of("compteurs.ticketsOuverts", "compteurs.ticketsFermes",
                "compteurs.fusionsOuvertes", "compteurs.fusionsFusionnees", "compteurs.pipelinesReussis",
                "compteurs.pipelinesEchoues", "compteurs.pipelinesAutres", "compteurs.versionsPubliees"));
        if (!vue.jalons().isEmpty()) {
            noms.add("jalons");
        }
        return List.copyOf(noms);
    }

    /**
     * Vrai si chaque nombre du texte figure parmi les indicateurs affichés (compteurs, jalons, période et
     * leurs dates) : l'IA ne calcule ni n'invente aucun chiffre (PIN-02).
     */
    static boolean fondeeSurLesIndicateurs(String texte, VueActivite vue) {
        Set<String> admis = new HashSet<>();
        VueActivite.Compteurs c = vue.compteurs();
        for (int v : new int[] {c.ticketsOuverts(), c.ticketsFermes(), c.fusionsOuvertes(), c.fusionsFusionnees(),
                c.pipelinesReussis(), c.pipelinesEchoues(), c.pipelinesAutres(), c.versionsPubliees(), vue.periode().jours()}) {
            admis.add(String.valueOf(v));
        }
        dates(admis, vue.periode().debut());
        dates(admis, vue.periode().fin());
        for (VueActivite.Jalon j : vue.jalons()) {
            admis.add(String.valueOf(j.ticketsFermes()));
            admis.add(String.valueOf(j.ticketsTotal()));
            if (j.avancement() != null) {
                admis.add(String.valueOf(j.avancement().pourcentage()));
            }
            dates(admis, j.echeance());
            Matcher titre = NOMBRE.matcher(j.titre() == null ? "" : j.titre());
            while (titre.find()) {
                admis.add(sansZeros(titre.group()));
            }
        }
        Matcher m = NOMBRE.matcher(texte);
        while (m.find()) {
            if (!admis.contains(sansZeros(m.group()))) {
                return false;
            }
        }
        return true;
    }

    private static void dates(Set<String> admis, LocalDate date) {
        if (date != null) {
            admis.add(String.valueOf(date.getYear()));
            admis.add(String.valueOf(date.getMonthValue()));
            admis.add(String.valueOf(date.getDayOfMonth()));
        }
    }

    private static String sansZeros(String nombre) {
        return nombre.replaceFirst("^0+(?=\\d)", "");
    }

    /** Texte brut : caractères de contrôle retirés (sauf retours à la ligne), longueur bornée. */
    private static String nettoyer(String texte) {
        if (texte == null) {
            return "";
        }
        String t = texte.replaceAll("[\\p{Cc}&&[^\\n]]|\\p{Cf}", "").strip();
        return t.length() > LONGUEUR_MAX ? t.substring(0, LONGUEUR_MAX) : t;
    }
}
