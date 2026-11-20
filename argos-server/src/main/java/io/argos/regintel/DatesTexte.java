package io.argos.regintel;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraction déterministe de la date d'entrée en vigueur d'un texte (REG-03). La date de publication
 * vient des métadonnées du Journal officiel ; la date d'effet est cherchée dans la notice et les
 * articles : « entre en vigueur le 1er janvier 2027 », « à compter du … », « le lendemain de sa
 * publication ». Plusieurs dates : la plus proche est retenue et le motif le signale. Aucune date
 * reconnue : la fiche affiche « date à confirmer » (REG-03-S2).
 */
public final class DatesTexte {

    /** Date d'effet extraite et passage qui l'a fournie. */
    public record DateEffet(LocalDate date, String motif) {
    }

    private static final Map<String, Integer> MOIS = Map.ofEntries(Map.entry("janvier", 1), Map.entry("fevrier", 2),
            Map.entry("mars", 3), Map.entry("avril", 4), Map.entry("mai", 5), Map.entry("juin", 6), Map.entry("juillet", 7),
            Map.entry("aout", 8), Map.entry("septembre", 9), Map.entry("octobre", 10), Map.entry("novembre", 11),
            Map.entry("decembre", 12));

    private static final String VIGUEUR = "(?:entre|entrent|entrera|entreront) en vigueur";
    private static final String DATE = "(\\d{1,2})(?:er)? (janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre) (\\d{4})";
    private static final Pattern DATE_FIXE = Pattern.compile(VIGUEUR + " (?:le |au |a compter du |a partir du )" + DATE);
    private static final Pattern DATE_AVANT = Pattern.compile("(?:a compter du|a partir du) " + DATE + "[^.]{0,80}?" + VIGUEUR);
    private static final Pattern LENDEMAIN = Pattern.compile(VIGUEUR + " (?:le )?lendemain de (?:sa|leur) publication");
    private static final Pattern IMMEDIATE = Pattern.compile(VIGUEUR + " (?:a la date|le jour) de (?:sa|leur) publication");

    /** Libellé affiché à la place d'une date d'effet absente. */
    public static final String DATE_A_CONFIRMER = "date à confirmer";

    private DatesTexte() {
    }

    /** Date d'entrée en vigueur du texte, si elle est énoncée dans son contenu. */
    public static Optional<DateEffet> dateEffet(LocalDate publication, String contenu) {
        if (contenu == null || contenu.isBlank()) {
            return Optional.empty();
        }
        String texteNormalise = Qualification.normaliser(contenu.replace('’', '\''));
        TreeMap<LocalDate, String> dates = new TreeMap<>();
        chercher(DATE_FIXE.matcher(texteNormalise), dates);
        chercher(DATE_AVANT.matcher(texteNormalise), dates);
        if (publication != null && LENDEMAIN.matcher(texteNormalise).find()) {
            dates.putIfAbsent(publication.plusDays(1), "entre en vigueur le lendemain de sa publication");
        }
        if (publication != null && IMMEDIATE.matcher(texteNormalise).find()) {
            dates.putIfAbsent(publication, "entre en vigueur à la date de sa publication");
        }
        if (dates.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<LocalDate, String> premiere = dates.firstEntry();
        String motif = dates.size() > 1
                ? premiere.getValue() + " (plusieurs dates d'effet dans le texte : la plus proche est retenue, à vérifier)"
                : premiere.getValue();
        return Optional.of(new DateEffet(premiere.getKey(), motif.length() > 300 ? motif.substring(0, 300) : motif));
    }

    private static void chercher(Matcher correspondance, TreeMap<LocalDate, String> dates) {
        while (correspondance.find()) {
            try {
                LocalDate date = LocalDate.of(Integer.parseInt(correspondance.group(3)), MOIS.get(correspondance.group(2)),
                        Integer.parseInt(correspondance.group(1)));
                dates.putIfAbsent(date, correspondance.group());
            } catch (DateTimeException | NumberFormatException invalide) {
                // date impossible (31 février) : ignorée
            }
        }
    }
}
