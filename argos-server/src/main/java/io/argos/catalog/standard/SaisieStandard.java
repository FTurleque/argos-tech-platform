package io.argos.catalog.standard;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import io.argos.catalog.PackageKey;
import io.argos.workspace.DonneeInvalide;

/**
 * Entrée du référentiel telle que la saisit un administrateur d'espace (RAD-01). Les valeurs sont
 * des données non fiables : {@link #valider()} les contrôle toutes et refuse la saisie au premier
 * champ manquant ou invalide, en nommant ce champ.
 *
 * @param cle                clé de la technologie : PURL sans version ({@code pkg:maven/org.springframework.boot/spring-boot})
 * @param libelle            nom affiché ; à défaut, le nom du paquet
 * @param statut             {@code ADOPT}, {@code TRIAL}, {@code ASSESS} ou {@code HOLD}
 * @param versionRecommandee version recommandée, obligatoire pour {@code ADOPT}
 * @param responsable        personne ou équipe responsable du standard
 * @param justification      justification du statut
 * @param dateRevue          date de la prochaine revue, au format {@code AAAA-MM-JJ}
 * @param produitCycleVie    identifiant du produit sur endoflife.date (par exemple {@code spring-boot}), facultatif
 */
public record SaisieStandard(String cle, String libelle, String statut, String versionRecommandee, String responsable,
        String justification, String dateRevue, String produitCycleVie) {

    /** Saisie contrôlée et normalisée. */
    public record Validee(String cle, String libelle, ReferentielStandards.Statut statut, String versionRecommandee,
            String responsable, String justification, LocalDate dateRevue, String produitCycleVie) {
    }

    private static final Pattern PRODUIT = Pattern.compile("^[a-z0-9][a-z0-9._+-]{0,99}$");
    /** Caractères de contrôle refusés, hors tabulation et retours à la ligne. */
    private static final Pattern CONTROLE = Pattern.compile("[\\p{Cntrl}&&[^\\t\\n\\r]]");

    /** Contrôle la saisie ; {@link DonneeInvalide} nomme le premier champ manquant ou invalide. */
    public Validee valider() {
        String cleValidee = obligatoire("cle", cle, 500);
        PackageKey.Purl purl = PackageKey.parse(cleValidee).orElseThrow(() -> new DonneeInvalide("DONNEE_INVALIDE",
                "La clé doit être un PURL sans version, par exemple pkg:maven/org.springframework.boot/spring-boot.", "cle"));
        if (purl.version() != null) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "La clé ne doit pas porter de version : la version recommandée "
                    + "se saisit à part.", "cle");
        }
        String nomParDefaut = purl.key().substring(purl.key().lastIndexOf('/') + 1);
        String libelleValide = facultatif("libelle", libelle, 200);
        ReferentielStandards.Statut statutValide = statut(obligatoire("statut", statut, 8));
        String versionValidee = facultatif("versionRecommandee", versionRecommandee, 100);
        if (statutValide == ReferentielStandards.Statut.ADOPT && versionValidee == null) {
            throw manquant("versionRecommandee", "La version recommandée est obligatoire pour le statut ADOPT.");
        }
        String responsableValide = obligatoire("responsable", responsable, 200);
        String justificationValidee = obligatoire("justification", justification, 2000);
        LocalDate dateRevueValidee = date(obligatoire("dateRevue", dateRevue, 10));
        String produitValide = facultatif("produitCycleVie", produitCycleVie, 100);
        if (produitValide != null) {
            produitValide = produitValide.toLowerCase(Locale.ROOT);
            if (!PRODUIT.matcher(produitValide).matches()) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Identifiant de produit endoflife.date invalide "
                        + "(lettres minuscules, chiffres, « . », « _ », « + » et « - »).", "produitCycleVie");
            }
        }
        return new Validee(purl.key(), libelleValide == null ? nomParDefaut : libelleValide, statutValide, versionValidee,
                responsableValide, justificationValidee, dateRevueValidee, produitValide);
    }

    /** Statut saisi ; {@link DonneeInvalide} sur le champ {@code statut} s'il n'est pas reconnu. */
    static ReferentielStandards.Statut statut(String valeur) {
        try {
            return ReferentielStandards.Statut.valueOf(valeur.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException inconnu) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Statut inconnu : ADOPT, TRIAL, ASSESS ou HOLD.", "statut");
        }
    }

    static LocalDate date(String valeur) {
        try {
            return LocalDate.parse(valeur);
        } catch (DateTimeParseException invalide) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "La date de revue doit être au format AAAA-MM-JJ.", "dateRevue");
        }
    }

    static String obligatoire(String champ, String valeur, int longueurMax) {
        String saisie = facultatif(champ, valeur, longueurMax);
        if (saisie == null) {
            throw manquant(champ, "Le champ " + champ + " est obligatoire.");
        }
        return saisie;
    }

    static String facultatif(String champ, String valeur, int longueurMax) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String nettoyee = valeur.strip();
        if (nettoyee.length() > longueurMax) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Le champ " + champ + " dépasse " + longueurMax + " caractères.",
                    champ);
        }
        if (CONTROLE.matcher(nettoyee).find()) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Le champ " + champ + " contient des caractères de contrôle.",
                    champ);
        }
        return nettoyee;
    }

    private static DonneeInvalide manquant(String champ, String message) {
        return new DonneeInvalide("CHAMP_MANQUANT", message, champ);
    }
}
