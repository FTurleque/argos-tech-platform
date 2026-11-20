package io.argos.alerting;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.argos.workspace.subscription.SubscriptionRequest;
import io.argos.workspace.subscription.SubscriptionService;

/**
 * Règle d'alerte portée par un abonnement de veille (ALR-01, ABO-02) : canal, fréquence et critères.
 *
 * <p>Correspondance avec un impact de sécurité :
 * <ul>
 *   <li>seuls les critères {@code PROJECT}, {@code PACKAGE}, {@code SOURCE}, {@code TECHNOLOGY} et
 *       {@code SEVERITY} s'appliquent aux impacts ; une règle qui n'en porte aucun ne concerne aucun impact ;</li>
 *   <li>les critères d'un même type sont alternatifs, les types présents sont cumulatifs ;</li>
 *   <li>{@code SEVERITY} est une gravité minimale : elle conditionne l'alerte immédiate, jamais l'entrée
 *       dans un résumé (ALR-01-S2) ; une gravité inconnue ou non graduée ne l'atteint pas.</li>
 * </ul>
 * Une règle ne décide jamais de ce que le destinataire peut voir : les impacts évalués sont déjà
 * restreints à ses espaces (ABO-05, ACC-04).
 */
public final class RegleAlerte {

    /** Canaux diffusés : e-mail et client desktop (ALR-06 ; aucun canal de messagerie d'équipe, Q-2). */
    public static final Set<String> CANAUX = Set.of("EMAIL", "DESKTOP");

    /** Fréquences : alerte immédiate, résumé quotidien ou hebdomadaire. */
    public static final String IMMEDIATE = "IMMEDIATE";
    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";

    private static final Set<String> TYPES_IMPACT = Set.of("PROJECT", "PACKAGE", "SOURCE", "TECHNOLOGY", "SEVERITY");

    /** Technologie → types PURL, mêmes correspondances indicatives que la recherche de veille. */
    private static final Map<String, Set<String>> TECHNOLOGIES = Map.ofEntries(
            Map.entry("java", Set.of("maven")), Map.entry("javascript", Set.of("npm")), Map.entry("node", Set.of("npm")),
            Map.entry("typescript", Set.of("npm")), Map.entry("python", Set.of("pypi")), Map.entry("rust", Set.of("cargo")),
            Map.entry("go", Set.of("golang")), Map.entry(".net", Set.of("nuget")), Map.entry("php", Set.of("composer")),
            Map.entry("ruby", Set.of("gem")));

    private final String canal;
    private final String frequence;
    private final Set<String> projets = new HashSet<>();
    private final Set<String> paquets = new HashSet<>();
    private final Set<String> sources = new HashSet<>();
    private final Set<String> typesPurl = new HashSet<>();
    private final int graviteMinimale;
    private final boolean applicableAuxImpacts;

    private RegleAlerte(String canal, String frequence, List<SubscriptionRequest.Filter> filtres) {
        this.canal = canal;
        this.frequence = frequence;
        int minimale = 0;
        boolean applicable = false;
        for (SubscriptionRequest.Filter filtre : filtres) {
            String type = filtre.type().toUpperCase(Locale.ROOT);
            String valeur = filtre.value().strip();
            applicable |= TYPES_IMPACT.contains(type);
            switch (type) {
                case "PROJECT" -> projets.add(valeur.toLowerCase(Locale.ROOT));
                case "PACKAGE" -> paquets.add(sansVersion(valeur));
                case "SOURCE" -> sources.add(valeur.toUpperCase(Locale.ROOT));
                case "TECHNOLOGY" -> {
                    String minuscule = valeur.toLowerCase(Locale.ROOT);
                    typesPurl.addAll(TECHNOLOGIES.getOrDefault(minuscule, Set.of(minuscule)));
                }
                case "SEVERITY" -> {
                    int rang = Gravite.rang(valeur);
                    minimale = minimale == 0 ? rang : Math.min(minimale, rang);
                }
                default -> {
                    // VENDOR, THEME, DOMAIN : critères de l'actualité, sans effet sur les impacts.
                }
            }
        }
        this.graviteMinimale = minimale;
        this.applicableAuxImpacts = applicable;
    }

    /** Règle d'un abonnement actif ; vide si son canal ou sa fréquence n'est pas diffusé. */
    public static Optional<RegleAlerte> depuis(SubscriptionService.Subscription abonnement) {
        return depuis(abonnement.channel(), abonnement.frequency(), abonnement.filters());
    }

    static Optional<RegleAlerte> depuis(String canal, String frequence, List<SubscriptionRequest.Filter> filtres) {
        String canalNormalise = canal == null ? "" : canal.strip().toUpperCase(Locale.ROOT);
        String frequenceNormalisee = frequence == null ? "" : frequence.strip().toUpperCase(Locale.ROOT);
        if (!CANAUX.contains(canalNormalise) || !Set.of(IMMEDIATE, DAILY, WEEKLY).contains(frequenceNormalisee)) {
            return Optional.empty();
        }
        return Optional.of(new RegleAlerte(canalNormalise, frequenceNormalisee, filtres));
    }

    public String canal() {
        return canal;
    }

    public String frequence() {
        return frequence;
    }

    public boolean immediate() {
        return IMMEDIATE.equals(frequence);
    }

    /** Fréquence du résumé qui reçoit les éléments non alertés : quotidien pour une règle immédiate. */
    public String frequenceResume() {
        return immediate() ? DAILY : frequence;
    }

    /** Vrai si l'impact (projet, paquet, source du signal) satisfait les critères de la règle. */
    public boolean concerne(String projetId, String clePaquet, String sourceSignal) {
        return applicableAuxImpacts && projetRetenu(projetId) && paquetRetenu(clePaquet) && sourceRetenue(sourceSignal)
                && (typesPurl.isEmpty() || typesPurl.contains(typePurl(clePaquet)));
    }

    /** Vrai sans filtre de projet, ou si le projet figure parmi ceux de la règle. */
    private boolean projetRetenu(String projetId) {
        return projets.isEmpty() || (projetId != null && projets.contains(projetId.toLowerCase(Locale.ROOT)));
    }

    /** Vrai sans filtre de paquet, ou si le paquet figure parmi ceux de la règle. */
    private boolean paquetRetenu(String clePaquet) {
        return paquets.isEmpty() || (clePaquet != null && paquets.contains(clePaquet));
    }

    /** Vrai sans filtre de source, ou si la source du signal figure parmi celles de la règle. */
    private boolean sourceRetenue(String sourceSignal) {
        return sources.isEmpty() || (sourceSignal != null && sources.contains(sourceSignal.toUpperCase(Locale.ROOT)));
    }

    /** Vrai si la gravité du signal atteint la gravité minimale de la règle (toujours vrai sans minimum). */
    public boolean seuilAtteint(String gravite) {
        return graviteMinimale == 0 || Gravite.rang(gravite) >= graviteMinimale;
    }

    private static String sansVersion(String paquet) {
        int arobase = paquet.indexOf('@', paquet.startsWith("pkg:npm/@") ? 9 : 0);
        return arobase < 0 ? paquet : paquet.substring(0, arobase);
    }

    private static String typePurl(String clePaquet) {
        if (clePaquet == null || !clePaquet.startsWith("pkg:")) {
            return "";
        }
        int slash = clePaquet.indexOf('/', 4);
        return (slash < 0 ? clePaquet.substring(4) : clePaquet.substring(4, slash)).toLowerCase(Locale.ROOT);
    }
}
