package io.argos.catalog.profil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Règles qu'un manifeste doit respecter pour entrer au catalogue (ADR-0024).
 *
 * <p>Les identifiants de connecteurs et de stratégies ne sont pas confrontés au code ici :
 * {@code catalog} ne dépend pas d'{@code impact-engine} (arc42 §5).
 */
public final class ValidateurManifeste {

    /** Identifiant de profil ou de rubrique : minuscules, chiffres et tirets. */
    static final Pattern IDENTIFIANT = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

    private ValidateurManifeste() {
    }

    /** Erreurs du manifeste ; liste vide s'il est valide. */
    public static List<String> erreurs(ManifesteProfil manifeste) {
        List<String> erreurs = new ArrayList<>();
        if (manifeste.formatManifeste() != ManifesteProfil.FORMAT_COURANT) {
            erreurs.add("format de manifeste " + manifeste.formatManifeste() + " non pris en charge (attendu : "
                    + ManifesteProfil.FORMAT_COURANT + ")");
        }
        if (manifeste.identifiant() == null || !IDENTIFIANT.matcher(manifeste.identifiant()).matches()) {
            erreurs.add("identifiant absent ou mal formé : " + manifeste.identifiant());
        }
        exigerTexte(erreurs, "version", manifeste.version());
        exigerTexte(erreurs, "libelle", manifeste.libelle());
        exigerTexte(erreurs, "description", manifeste.description());
        exigerNonVide(erreurs, "categoriesSources", manifeste.categoriesSources());
        exigerNonVide(erreurs, "connecteurs", manifeste.connecteurs());
        exigerNonVide(erreurs, "typesObjetsSuivis", manifeste.typesObjetsSuivis());
        exigerNonVide(erreurs, "rubriques", manifeste.rubriques());
        exigerNonVide(erreurs, "strategies", manifeste.strategies());

        Set<String> rubriques = new HashSet<>();
        for (Rubrique rubrique : manifeste.rubriques()) {
            if (rubrique == null || rubrique.identifiant() == null || !IDENTIFIANT.matcher(rubrique.identifiant()).matches()) {
                erreurs.add("rubrique sans identifiant valide");
                continue;
            }
            if (!rubriques.add(rubrique.identifiant())) {
                erreurs.add("rubrique en double : " + rubrique.identifiant());
            }
            exigerTexte(erreurs, "libellé de la rubrique " + rubrique.identifiant(), rubrique.libelle());
            for (String categorie : rubrique.categories()) {
                if (categorie == null || !manifeste.categoriesSources().contains(categorie)) {
                    erreurs.add("catégorie de la rubrique " + rubrique.identifiant() + " absente de categoriesSources : "
                            + categorie);
                }
            }
        }
        for (StrategieDeclaree strategie : manifeste.strategies()) {
            if (strategie == null || strategie.identifiant() == null || strategie.identifiant().isBlank()) {
                erreurs.add("stratégie sans identifiant");
            } else if (!strategie.deterministe()) {
                erreurs.add("stratégie non déterministe refusée (ADR-0013) : " + strategie.identifiant());
            }
        }
        Set<String> types = new HashSet<>();
        for (ListeObjets liste : manifeste.listes()) {
            if (liste == null || liste.typeObjet() == null || !manifeste.typesObjetsSuivis().contains(liste.typeObjet())) {
                erreurs.add("liste d'un type d'objet non déclaré dans typesObjetsSuivis : "
                        + (liste == null ? null : liste.typeObjet()));
                continue;
            }
            if (!types.add(liste.typeObjet())) {
                erreurs.add("liste en double : " + liste.typeObjet());
            }
            if (liste.valeurs().isEmpty()) {
                erreurs.add("liste vide : " + liste.typeObjet());
            }
            Set<String> valeurs = new HashSet<>();
            for (ValeurListe valeur : liste.valeurs()) {
                if (valeur == null || valeur.identifiant() == null || !IDENTIFIANT.matcher(valeur.identifiant()).matches()) {
                    erreurs.add("valeur sans identifiant valide dans la liste " + liste.typeObjet());
                    continue;
                }
                if (!valeurs.add(valeur.identifiant())) {
                    erreurs.add("valeur en double dans la liste " + liste.typeObjet() + " : " + valeur.identifiant());
                }
                exigerTexte(erreurs, "libellé de la valeur " + valeur.identifiant(), valeur.libelle());
            }
        }
        for (GardeFou gardeFou : manifeste.gardeFous()) {
            if (gardeFou == null || gardeFou.type() == null) {
                erreurs.add("garde-fou sans type connu");
            }
        }
        return erreurs;
    }

    private static void exigerTexte(List<String> erreurs, String champ, String valeur) {
        if (valeur == null || valeur.isBlank()) {
            erreurs.add(champ + " absent");
        }
    }

    private static void exigerNonVide(List<String> erreurs, String champ, List<?> valeurs) {
        if (valeurs.isEmpty()) {
            erreurs.add(champ + " vide");
        }
    }
}
