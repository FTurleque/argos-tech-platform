package io.argos.ingestion.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.argos.ingestion.web.RecuperationRefusee.Motif;

/** Lecture des flux et des pages (WEB-01, WEB-02, WEB-04) : texte brut, DOCTYPE refusé, aperçu. */
class LecteursTest {

    private final LecteurFlux flux = new LecteurFlux();
    private final LecteurPage page = new LecteurPage();

    private static byte[] utf8(String texte) {
        return texte.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void fluxAtomEtRssLusEnTexteBrut() {
        LecteurFlux.Flux atom = flux.lire(utf8("""
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title><id>urn:blog</id><updated>2026-11-10T10:00:00Z</updated>
                  <entry><title>Version 2.0</title><id>urn:a1</id><link href="https://blog.exemple.test/v2"/>
                    <updated>2026-11-10T10:00:00Z</updated>
                    <content type="html">&lt;p&gt;Nouveautés&lt;/p&gt;&lt;script&gt;alert(1)&lt;/script&gt;</content></entry>
                </feed>"""));
        assertEquals("Blog", atom.titre());
        assertEquals(1, atom.elements().size());
        LecteurFlux.ElementFlux element = atom.elements().get(0);
        assertEquals("https://blog.exemple.test/v2", element.lien());
        assertFalse(element.texte().contains("<"), "balises retirées");
        assertTrue(element.texte().contains("Nouveautés"));

        LecteurFlux.Flux rss = flux.lire(utf8("""
                <rss version="2.0"><channel><title>Notes</title><link>https://exemple.test</link><description>d</description>
                <item><title>Patch</title><link>javascript:alert(1)</link><guid>p-1</guid><description>Correctif</description></item>
                </channel></rss>"""));
        assertNull(rss.elements().get(0).lien(), "lien non http(s) écarté");
    }

    /**
     * Des entités doublement encodées sont décodées en texte : le champ stocké contient alors des
     * caractères de balisage en clair ({@code <script>}). Ce texte est une donnée, jamais du HTML :
     * l'IHM doit l'échapper à l'affichage (fiche {@code veille-rss-web}, WEB-04).
     */
    @Test
    void entitesDecodeesConserveesEnTexteBrutAEchapperALAffichage() {
        LecteurFlux.Flux atom = flux.lire(utf8("""
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title><id>urn:blog</id><updated>2026-11-10T10:00:00Z</updated>
                  <entry><title type="html">Alerte &amp;lt;img src=x onerror=alert(1)&amp;gt;</title><id>urn:a2</id>
                    <updated>2026-11-10T10:00:00Z</updated>
                    <content type="html">&amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt;</content></entry>
                </feed>"""));
        LecteurFlux.ElementFlux element = atom.elements().get(0);
        assertEquals("<script>alert(1)</script>", element.texte());
        assertEquals("Alerte <img src=x onerror=alert(1)>", element.titre());
    }

    @Test
    void pageHtmlOuDoctypeRefusesCommeFlux() {
        assertEquals(Motif.FLUX_INVALIDE, assertThrows(RecuperationRefusee.class,
                () -> flux.lire(utf8("<html><body><p>Pas un flux</p></body></html>"))).motif());
        assertEquals(Motif.FLUX_INVALIDE, assertThrows(RecuperationRefusee.class,
                () -> flux.lire(utf8("""
                        <?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                        <rss version="2.0"><channel><title>&x;</title></channel></rss>"""))).motif());
    }

    @Test
    void pageLueParBlocsEtApercuDuChangement() {
        LecteurPage.Instantane avant = page.lire(utf8("<html><head><title>Notes</title><script>x()</script></head>"
                + "<body><h1>Notes de version</h1><p>Version 1.0</p></body></html>"), "text/html; charset=utf-8",
                "https://exemple.test/notes");
        LecteurPage.Instantane apres = page.lire(utf8("<html><body><h1>Notes de version</h1><p>Version 1.1 : correctifs</p>"
                + "<p>Version 1.0</p></body></html>"), "text/html", "https://exemple.test/notes");
        assertEquals("Notes", avant.titre());
        assertFalse(avant.texte().contains("x()"));
        assertNotEquals(avant.empreinte(), apres.empreinte());
        assertEquals("Ajouté : Version 1.1 : correctifs", LecteurPage.apercu(avant.texte(), apres.texte()));
        assertEquals(Motif.PAGE_INVALIDE, assertThrows(RecuperationRefusee.class,
                () -> page.lire(utf8("{}"), "application/json", "https://exemple.test/")).motif());
    }
}
