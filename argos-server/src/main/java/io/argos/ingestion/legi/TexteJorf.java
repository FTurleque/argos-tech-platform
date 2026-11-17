package io.argos.ingestion.legi;

import java.time.LocalDate;
import java.util.List;

/**
 * Texte publié au Journal officiel, lu dans une archive des jeux de données JORF de la DILA (Licence
 * Ouverte). Toutes les chaînes sont du texte brut extrait d'un contenu externe non fiable, déjà borné.
 *
 * @param identifiant      identifiant officiel du texte ({@code JORFTEXT} suivi de douze chiffres)
 * @param nature           nature du texte (par exemple {@code DECRET}, {@code ARRETE}), absente si inconnue
 * @param numero           numéro du texte, absent si inconnu
 * @param nor              numéro NOR, absent si inconnu
 * @param ministere        ministère ou autorité, absent si inconnu
 * @param titre            titre complet du texte
 * @param datePublication  date de publication au Journal officiel
 * @param dateSignature    date de signature, absente si inconnue
 * @param codesModifies    codes consolidés modifiés par le texte
 * @param articlesModifies articles modifiés, sous la forme « code, art. numéro »
 * @param contenu          notice, nota et articles du texte, en texte brut borné
 * @param archive          nom de l'archive d'origine (provenance)
 */
public record TexteJorf(String identifiant, String nature, String numero, String nor, String ministere, String titre,
        LocalDate datePublication, LocalDate dateSignature, List<CodeModifie> codesModifies,
        List<String> articlesModifies, String contenu, String archive) {

    public TexteJorf {
        codesModifies = List.copyOf(codesModifies);
        articlesModifies = List.copyOf(articlesModifies);
    }

    /** Code consolidé modifié : identifiant LEGITEXT et libellé. */
    public record CodeModifie(String identifiant, String libelle) {
    }

    /** Même texte, avec un autre contenu et une autre provenance. */
    public TexteJorf avec(String nouveauContenu, String nouvelleArchive) {
        return new TexteJorf(identifiant, nature, numero, nor, ministere, titre, datePublication, dateSignature,
                codesModifies, articlesModifies, nouveauContenu, nouvelleArchive);
    }
}
