package io.argos.signal.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.argos.ingestion.web.LecteurFlux.ElementFlux;
import io.argos.ingestion.web.LecteurPage;

/**
 * Empreinte de déduplication d'une actualité (WEB-05, arc42 §6.1) : un même article reçu par
 * plusieurs sources a la même empreinte et n'est restitué qu'une fois.
 *
 * <ul>
 *   <li>article avec lien : lien normalisé (schéma et hôte en minuscules, port par défaut, fragment
 *       et paramètres {@code utm_*} retirés, barre oblique finale ignorée) ;</li>
 *   <li>article sans lien : identifiant de l'élément ({@code guid} ou {@code id}) ;</li>
 *   <li>à défaut : titre et texte ;</li>
 *   <li>changement de page : adresse normalisée de la page et empreinte du nouveau contenu.</li>
 * </ul>
 */
final class Deduplication {

    private Deduplication() {
    }

    static String article(ElementFlux e) {
        if (e.lien() != null) {
            return LecteurPage.empreinte("lien:" + normaliser(e.lien()));
        }
        if (e.identifiant() != null) {
            return LecteurPage.empreinte("id:" + e.identifiant());
        }
        return LecteurPage.empreinte("contenu:" + e.titre() + "\n" + e.texte());
    }

    static String changementPage(String adressePage, String empreinteContenu) {
        return LecteurPage.empreinte("page:" + normaliser(adressePage) + "\n" + empreinteContenu);
    }

    static String normaliser(String lien) {
        try {
            URI u = new URI(lien.strip()).normalize();
            String schema = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            String hote = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            int port = u.getPort();
            boolean parDefaut = port < 0 || (port == 80 && "http".equals(schema)) || (port == 443 && "https".equals(schema));
            String chemin = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            if (chemin.length() > 1 && chemin.endsWith("/")) {
                chemin = chemin.substring(0, chemin.length() - 1);
            }
            List<String> parametres = new ArrayList<>();
            if (u.getRawQuery() != null) {
                for (String p : u.getRawQuery().split("&")) {
                    if (!p.isEmpty() && !p.toLowerCase(Locale.ROOT).startsWith("utm_")) {
                        parametres.add(p);
                    }
                }
            }
            return schema + "://" + hote + (parDefaut ? "" : ":" + port) + chemin
                    + (parametres.isEmpty() ? "" : "?" + String.join("&", parametres));
        } catch (URISyntaxException e) {
            return lien.strip();
        }
    }
}
