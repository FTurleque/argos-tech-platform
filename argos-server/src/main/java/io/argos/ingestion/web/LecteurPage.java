package io.argos.ingestion.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import io.argos.ingestion.web.RecuperationRefusee.Motif;

/**
 * Lecture d'une page surveillée avec jsoup (MIT, ADR-0028) : le texte visible est découpé en blocs
 * (titres, paragraphes, éléments de liste, cellules…) ; scripts, styles et balises sont ignorés.
 * L'empreinte SHA-256 des blocs détecte un changement de contenu (WEB-02), l'aperçu cite les
 * blocs ajoutés, à défaut les blocs retirés.
 */
@ApplicationScoped
public class LecteurPage {

    /** Instantané d'une page : titre, texte par blocs séparés d'un saut de ligne, empreinte. */
    public record Instantane(String titre, String texte, String empreinte) {
    }

    static final int TEXTE_MAX = 100_000;
    static final int APERCU_MAX = 500;
    private static final String BLOCS = "h1, h2, h3, h4, h5, h6, p, li, pre, blockquote, dt, dd, td, th, figcaption";

    /** Lit une page HTML ; lève {@link RecuperationRefusee} ({@code PAGE_INVALIDE}) sinon. */
    public Instantane lire(byte[] contenu, String typeContenu, String adresse) {
        String type = typeContenu == null ? "" : typeContenu.toLowerCase(Locale.ROOT);
        if (!type.isEmpty() && !type.startsWith("text/html") && !type.startsWith("application/xhtml+xml")) {
            throw new RecuperationRefusee(Motif.PAGE_INVALIDE, "L'adresse ne renvoie pas une page HTML.");
        }
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(contenu), null, adresse);
        } catch (IOException e) {
            throw new RecuperationRefusee(Motif.PAGE_INVALIDE, "Page illisible.", e);
        }
        document.select("script, style, noscript, template, iframe, object, embed").remove();
        Set<String> blocs = new LinkedHashSet<>();
        for (Element e : document.body().select(BLOCS)) {
            String t = e.text().strip();
            if (!t.isEmpty()) {
                blocs.add(t);
            }
        }
        if (blocs.isEmpty()) {
            String t = document.body().text().strip();
            if (!t.isEmpty()) {
                blocs.add(t);
            }
        }
        String texte = LecteurFlux.couper(String.join("\n", blocs), TEXTE_MAX);
        return new Instantane(LecteurFlux.couper(document.title().strip(), 200), texte, empreinte(texte));
    }

    /** Aperçu d'un changement : blocs ajoutés, sinon blocs retirés, borné à {@value #APERCU_MAX} caractères. */
    public static String apercu(String avant, String apres) {
        Set<String> anciens = new LinkedHashSet<>(blocs(avant));
        Set<String> nouveaux = new LinkedHashSet<>(blocs(apres));
        List<String> ajoutes = new ArrayList<>();
        for (String b : nouveaux) {
            if (!anciens.contains(b)) {
                ajoutes.add(b);
            }
        }
        if (!ajoutes.isEmpty()) {
            return LecteurFlux.couper("Ajouté : " + String.join(" / ", ajoutes), APERCU_MAX);
        }
        List<String> retires = new ArrayList<>();
        for (String b : anciens) {
            if (!nouveaux.contains(b)) {
                retires.add(b);
            }
        }
        if (!retires.isEmpty()) {
            return LecteurFlux.couper("Retiré : " + String.join(" / ", retires), APERCU_MAX);
        }
        return "Contenu réordonné.";
    }

    /** Empreinte SHA-256 hexadécimale d'un texte. */
    public static String empreinte(String texte) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(texte.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static List<String> blocs(String texte) {
        if (texte == null || texte.isEmpty()) {
            return List.of();
        }
        return List.of(texte.split("\n"));
    }
}
