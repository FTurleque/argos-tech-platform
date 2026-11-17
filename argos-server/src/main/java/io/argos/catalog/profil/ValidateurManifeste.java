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
    public static List<String> erreurs(ManifesteProfil m) {
        List<String> erreurs = new ArrayList<>();
        if (m.formatManifeste() != ManifesteProfil.FORMAT_COURANT) {
            erreurs.add("format de manifeste " + m.formatManifeste() + " non pris en charge (attendu : "
                    + ManifesteProfil.FORMAT_COURANT + ")");
        }
        if (m.identifiant() == null || !IDENTIFIANT.matcher(m.identifiant()).matches()) {
            erreurs.add("identifiant absent ou mal formé : " + m.identifiant());
        }
        exigerTexte(erreurs, "version", m.version());
        exigerTexte(erreurs, "libelle", m.libelle());
        exigerTexte(erreurs, "description", m.description());
        exigerNonVide(erreurs, "categoriesSources", m.categoriesSources());
        exigerNonVide(erreurs, "connecteurs", m.connecteurs());
        exigerNonVide(erreurs, "typesObjetsSuivis", m.typesObjetsSuivis());
        exigerNonVide(erreurs, "rubriques", m.rubriques());
        exigerNonVide(erreurs, "strategies", m.strategies());

        Set<String> rubriques = new HashSet<>();
        for (Rubrique r : m.rubriques()) {
            if (r == null || r.identifiant() == null || !IDENTIFIANT.matcher(r.identifiant()).matches()) {
                erreurs.add("rubrique sans identifiant valide");
                continue;
            }
            if (!rubriques.add(r.identifiant())) {
                erreurs.add("rubrique en double : " + r.identifiant());
            }
            exigerTexte(erreurs, "libellé de la rubrique " + r.identifiant(), r.libelle());
        }
        for (StrategieDeclaree s : m.strategies()) {
            if (s == null || s.identifiant() == null || s.identifiant().isBlank()) {
                erreurs.add("stratégie sans identifiant");
            } else if (!s.deterministe()) {
                erreurs.add("stratégie non déterministe refusée (ADR-0013) : " + s.identifiant());
            }
        }
        Set<String> types = new HashSet<>();
        for (ListeObjets l : m.listes()) {
            if (l == null || l.typeObjet() == null || !m.typesObjetsSuivis().contains(l.typeObjet())) {
                erreurs.add("liste d'un type d'objet non déclaré dans typesObjetsSuivis : "
                        + (l == null ? null : l.typeObjet()));
                continue;
            }
            if (!types.add(l.typeObjet())) {
                erreurs.add("liste en double : " + l.typeObjet());
            }
            if (l.valeurs().isEmpty()) {
                erreurs.add("liste vide : " + l.typeObjet());
            }
            Set<String> valeurs = new HashSet<>();
            for (ValeurListe v : l.valeurs()) {
                if (v == null || v.identifiant() == null || !IDENTIFIANT.matcher(v.identifiant()).matches()) {
                    erreurs.add("valeur sans identifiant valide dans la liste " + l.typeObjet());
                    continue;
                }
                if (!valeurs.add(v.identifiant())) {
                    erreurs.add("valeur en double dans la liste " + l.typeObjet() + " : " + v.identifiant());
                }
                exigerTexte(erreurs, "libellé de la valeur " + v.identifiant(), v.libelle());
            }
        }
        for (GardeFou g : m.gardeFous()) {
            if (g == null || g.type() == null) {
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
