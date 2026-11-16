package io.argos.aigateway;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtre des données sortantes (Q-4) : aucun secret, aucun jeton ni aucune donnée personnelle ne part
 * vers le modèle, quelle que soit la classe déclarée par le module appelant.
 *
 * <ul>
 *   <li>Un secret ou un jeton reconnu (clé privée, JWT, jeton de forge ou de fournisseur cloud, en-tête
 *       {@code Bearer}, affectation {@code password=…}) fait refuser toute la demande : sa présence
 *       révèle une erreur de l'appelant.</li>
 *   <li>Une donnée personnelle reconnue (adresse électronique, numéro de téléphone, IBAN) est masquée ;
 *       le nombre de masquages est tracé, jamais la valeur.</li>
 * </ul>
 *
 * <p>Le filtre est une défense en profondeur : la classification par l'appelant reste la règle (IAG-02).
 */
final class FiltreDonnees {

    /** Résultat du filtrage d'une valeur. */
    record Resultat(String texte, int masquees, boolean secret) {
    }

    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{30,}"),
            Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{20,}"),
            Pattern.compile("\\bglpat-[A-Za-z0-9_-]{20,}"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}"),
            Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}"),
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]{20,}=*"),
            Pattern.compile("(?i)\\b(password|passwd|pwd|mot de passe|secret|api[_-]?key|access[_-]?token|token|jeton)"
                    + "\\s*[:=]\\s*\\S{6,}"));

    private static final Pattern COURRIEL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern TELEPHONE = Pattern.compile(
            "(?<![\\d.-])(?:\\+\\d{1,3}[ .-]?)?(?:\\(0\\)[ .-]?)?0?[1-9](?:[ .-]?\\d{2}){4}(?![\\d.-])");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){3,7}(?: ?[A-Z0-9]{1,3})?\\b");

    private FiltreDonnees() {
    }

    static Resultat filtrer(String valeur) {
        for (Pattern p : SECRETS) {
            if (p.matcher(valeur).find()) {
                return new Resultat("", 0, true);
            }
        }
        int[] masquees = {0};
        String t = masquer(valeur, COURRIEL, "[courriel masqué]", masquees);
        t = masquer(t, IBAN, "[IBAN masqué]", masquees);
        t = masquer(t, TELEPHONE, "[téléphone masqué]", masquees);
        return new Resultat(t, masquees[0], false);
    }

    private static String masquer(String texte, Pattern motif, String remplacement, int[] compteur) {
        Matcher m = motif.matcher(texte);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            compteur[0]++;
            m.appendReplacement(b, Matcher.quoteReplacement(remplacement));
        }
        m.appendTail(b);
        return b.toString();
    }
}
