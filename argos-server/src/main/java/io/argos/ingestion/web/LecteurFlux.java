package io.argos.ingestion.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jakarta.enterprise.context.ApplicationScoped;

import org.jsoup.Jsoup;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import io.argos.ingestion.web.RecuperationRefusee.Motif;

/**
 * Lecture d'un flux RSS (0.9x, 1.0, 2.0) ou Atom avec ROME (Apache-2.0, ADR-0028). Les déclarations
 * DOCTYPE sont refusées par ROME (protection contre les entités externes XML). Le contenu de chaque
 * élément est réduit à du texte brut : balises, scripts et styles sont retirés (WEB-04).
 */
@ApplicationScoped
public class LecteurFlux {

    /** Élément d'un flux, en texte brut ; chaque champ peut manquer sauf le titre. */
    public record ElementFlux(String identifiant, String titre, String lien, String texte, Instant publieLe) {
    }

    /** Flux lu : titre du flux et éléments, dans l'ordre du flux. */
    public record Flux(String titre, List<ElementFlux> elements) {
    }

    static final int TITRE_MAX = 1000;
    static final int TEXTE_MAX = 20_000;

    /** Lit un flux ; lève {@link RecuperationRefusee} ({@code FLUX_INVALIDE}) si le contenu n'en est pas un. */
    public Flux lire(byte[] contenu) {
        SyndFeed flux;
        try (XmlReader lecteur = new XmlReader(new ByteArrayInputStream(contenu))) {
            flux = new SyndFeedInput().build(lecteur);
        } catch (FeedException | IOException | IllegalArgumentException | IllegalStateException erreur) {
            throw new RecuperationRefusee(Motif.FLUX_INVALIDE, "L'adresse ne renvoie pas un flux RSS ou Atom valide.", erreur);
        }
        List<ElementFlux> elements = new ArrayList<>();
        for (SyndEntry entree : flux.getEntries()) {
            String titre = texte(entree.getTitle());
            String corps = corps(entree);
            if (titre.isEmpty()) {
                titre = couper(corps, 120);
            }
            if (titre.isEmpty()) {
                continue;
            }
            Date date = entree.getPublishedDate() != null ? entree.getPublishedDate() : entree.getUpdatedDate();
            elements.add(new ElementFlux(vide(entree.getUri()), couper(titre, TITRE_MAX), lienSur(entree.getLink()),
                    couper(corps, TEXTE_MAX), date == null ? null : date.toInstant()));
        }
        return new Flux(couper(texte(flux.getTitle()), 200), List.copyOf(elements));
    }

    private static String corps(SyndEntry entree) {
        StringBuilder html = new StringBuilder();
        if (entree.getContents() != null) {
            for (SyndContent contenu : entree.getContents()) {
                if (contenu != null && contenu.getValue() != null) {
                    html.append(contenu.getValue()).append('\n');
                }
            }
        }
        if (html.length() == 0 && entree.getDescription() != null && entree.getDescription().getValue() != null) {
            html.append(entree.getDescription().getValue());
        }
        return texte(html.toString());
    }

    /** HTML ou texte → texte brut, espaces normalisés. */
    static String texte(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text().strip();
    }

    /** Lien d'article affichable : seulement http(s), jamais récupéré par Argos. */
    static String lienSur(String lien) {
        if (lien == null || lien.isBlank()) {
            return null;
        }
        String lienNettoye = lien.strip();
        String minuscule = lienNettoye.toLowerCase(Locale.ROOT);
        if ((!minuscule.startsWith("http://") && !minuscule.startsWith("https://")) || lienNettoye.length() > 2000) {
            return null;
        }
        return lienNettoye;
    }

    static String couper(String valeur, int max) {
        if (valeur == null) {
            return "";
        }
        return valeur.length() <= max ? valeur : valeur.substring(0, max);
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }
}
