package io.argos.api.preference;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Préférences appliquées à l'utilisateur, après confrontation au catalogue (DF-AFF-1, INV-2).
 *
 * @param miseEnPage             mise en page à appliquer, toujours publiée
 * @param miseEnPageChoisie      mise en page enregistrée, absente si jamais choisie
 * @param miseEnPageIndisponible vrai si la mise en page choisie a été retirée du catalogue (AFF-04-S2)
 * @param message                information destinée à l'utilisateur, absente s'il n'y a rien à signaler
 * @param profilsActifs          profils à appliquer, tous publiés, jamais vide (INV-1)
 * @param profilsParDefaut       vrai si les profils appliqués sont ceux par défaut de la plateforme
 * @param profilsIndisponibles   profils enregistrés mais retirés du catalogue, ignorés
 */
@Schema(name = "PreferencesEffectives")
public record PreferencesEffectives(
        String miseEnPage,
        String miseEnPageChoisie,
        boolean miseEnPageIndisponible,
        String message,
        List<String> profilsActifs,
        boolean profilsParDefaut,
        List<String> profilsIndisponibles) {
}
