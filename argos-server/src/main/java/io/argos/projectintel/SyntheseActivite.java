package io.argos.projectintel;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.execution.ExecuteurBorne;

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
    Instance<SyntheseIa> syntheseIa;

    @Inject
    ExecuteurSyntheseIa executeurIa;

    @ConfigProperty(name = "argos.activite.synthese.delai-max", defaultValue = "PT20S")
    Duration delaiMax;

    /** Synthèse des indicateurs {@code vue}. */
    public Synthese produire(VueActivite vue) {
        List<String> sources = indicateurs(vue);
        if (vue.fraicheur().derniereMiseAJour() == null) {
            return new Synthese("SANS_DONNEES", null, MENTION, vue.periode(), sources);
        }
        if (!syntheseIa.isResolvable()) {
            return new Synthese("INDISPONIBLE", null, MENTION, vue.periode(), sources);
        }
        SyntheseIa modele = syntheseIa.get();
        if (!modele.budgetDisponible()) {
            return new Synthese("PLAFOND_ATTEINT", null, MENTION, vue.periode(), sources);
        }
        ExecuteurBorne.Issue<String> issue = executeurIa.executer(() -> modele.synthetiser(vue), delaiMax);
        if (issue.etat() != ExecuteurBorne.Etat.OBTENU) {
            if (issue.etat() != ExecuteurBorne.Etat.INTERROMPUE) {
                LOG.infof(issue.cause().orElseThrow(), "synthèse de l'activité du projet %s indisponible (%s)", vue.projectId(),
                        issue.etat());
            }
            return new Synthese("INDISPONIBLE", null, MENTION, vue.periode(), sources);
        }
        String propre = nettoyer(issue.valeur().orElse(""));
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
        VueActivite.Compteurs compteurs = vue.compteurs();
        for (int valeur : new int[] {compteurs.ticketsOuverts(), compteurs.ticketsFermes(), compteurs.fusionsOuvertes(),
                compteurs.fusionsFusionnees(), compteurs.pipelinesReussis(), compteurs.pipelinesEchoues(),
                compteurs.pipelinesAutres(), compteurs.versionsPubliees(), vue.periode().jours()}) {
            admis.add(String.valueOf(valeur));
        }
        dates(admis, vue.periode().debut());
        dates(admis, vue.periode().fin());
        for (VueActivite.Jalon jalon : vue.jalons()) {
            admis.add(String.valueOf(jalon.ticketsFermes()));
            admis.add(String.valueOf(jalon.ticketsTotal()));
            if (jalon.avancement() != null) {
                admis.add(String.valueOf(jalon.avancement().pourcentage()));
            }
            dates(admis, jalon.echeance());
            Matcher titre = NOMBRE.matcher(jalon.titre() == null ? "" : jalon.titre());
            while (titre.find()) {
                admis.add(sansZeros(titre.group()));
            }
        }
        Matcher correspondance = NOMBRE.matcher(texte);
        while (correspondance.find()) {
            if (!admis.contains(sansZeros(correspondance.group()))) {
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
        String nettoye = texte.replaceAll("[\\p{Cc}&&[^\\n]]|\\p{Cf}", "").strip();
        return nettoye.length() > LONGUEUR_MAX ? nettoye.substring(0, LONGUEUR_MAX) : nettoye;
    }
}
