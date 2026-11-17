package io.argos.ingestion.legi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.argos.testsupport.ReglementaireFixtures;

/**
 * Contrat du connecteur JORF (tâche 2.1, REG-01) sur des fichiers enregistrés au format DILA : lecture
 * des versions de texte et des articles, codes et articles modifiés, refus des DTD et des entités
 * externes (XXE), lecture bornée des archives {@code .tar.gz}.
 */
class LecteurJorfTest {

    private static byte[] octets(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void versionEtArticleAssemblesEnUnTexte() {
        List<LecteurJorf.Element> elements = new ArrayList<>();
        LecteurJorf.lire(octets(ReglementaireFixtures.fichier("texte-carriere.xml")), "JORF_20261120-213004.tar.gz")
                .ifPresent(elements::add);
        LecteurJorf.lire(octets(ReglementaireFixtures.fichier("article-carriere.xml")), "JORF_20261120-213004.tar.gz")
                .ifPresent(elements::add);

        List<TexteJorf> textes = LecteurJorf.assembler(elements);

        assertEquals(1, textes.size());
        TexteJorf t = textes.get(0);
        assertEquals("JORFTEXT000099000001", t.identifiant());
        assertEquals("DECRET", t.nature());
        assertEquals("2026-1101", t.numero());
        assertEquals("TFPF2631001D", t.nor());
        assertEquals("Ministère de la transformation et de la fonction publiques", t.ministere());
        assertTrue(t.titre().startsWith("Décret n° 2026-1101 du 18 novembre 2026 relatif aux règles d'avancement de grade"));
        assertEquals(LocalDate.of(2026, 11, 20), t.datePublication());
        assertEquals(LocalDate.of(2026, 11, 18), t.dateSignature());
        assertEquals(List.of(new TexteJorf.CodeModifie("LEGITEXT000044416551", "Code général de la fonction publique")),
                t.codesModifies(), "un seul code, le lien de citation est ignoré");
        assertEquals(List.of("Code général de la fonction publique, art. L. 522-24",
                "Code général de la fonction publique, art. L. 522-25"), t.articlesModifies());
        assertTrue(t.contenu().contains("le texte entre en vigueur le 1er janvier 2027"), t.contenu());
        assertTrue(t.contenu().contains("Article 3 : Le présent décret entre en vigueur le 1er janvier 2027."), t.contenu());
        assertFalse(t.contenu().contains("<p>"), "balisage réduit à du texte brut");
        assertEquals("JORF_20261120-213004.tar.gz", t.archive());
    }

    @Test
    void doctypeEtEntiteExterneRefuses() {
        byte[] xxe = octets(ReglementaireFixtures.fichier("xxe.xml"));
        LecteurJorf.XmlRefuse e = assertThrows(LecteurJorf.XmlRefuse.class, () -> LecteurJorf.lire(xxe, "a"));
        assertFalse(e.getMessage().contains("root:"), "aucun contenu de fichier local");
        assertThrows(LecteurJorf.XmlRefuse.class, () -> LecteurJorf.lire(octets("<TEXTE_VERSION><META>"), "a"),
                "XML mal formé");
        assertThrows(LecteurJorf.XmlRefuse.class, () -> LecteurJorf.lire(octets("""
                <?xml version="1.0"?>
                <!DOCTYPE l [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;">]>
                <TEXTE_VERSION>&b;</TEXTE_VERSION>
                """), "a"), "expansion d'entités refusée");
    }

    @Test
    void fichierDUnAutreTypeIgnore() {
        assertTrue(LecteurJorf.lire(octets("<TEXTEKALI><ID>KALITEXT000000000001</ID></TEXTEKALI>"), "a").isEmpty());
        assertTrue(LecteurJorf.lire(octets("<TEXTE_VERSION><META><META_COMMUN><ID>pas-un-identifiant</ID>"
                + "</META_COMMUN></META></TEXTE_VERSION>"), "a").isEmpty(), "identifiant officiel invalide");
    }

    @Test
    void archiveLueAvecSesFichiersXmlSeulement() {
        Map<String, String> fichiers = new LinkedHashMap<>();
        fichiers.put("a/texte.xml", ReglementaireFixtures.fichier("texte-paie.xml"));
        fichiers.put("a/lisezmoi.txt", "ignoré");
        fichiers.put("a/" + "x".repeat(150) + ".xml", "<nom-long/>");
        byte[] gz = ReglementaireFixtures.archive(fichiers);
        Map<String, String> lus = new LinkedHashMap<>();

        new ArchiveTar(100, 1 << 20, 10 << 20).lire(new ByteArrayInputStream(gz),
                (nom, contenu) -> lus.put(nom, new String(contenu, StandardCharsets.UTF_8)));

        assertEquals(1, lus.size(), lus.keySet().toString());
        assertTrue(lus.get("a/texte.xml").contains("JORFTEXT000099000002"));
    }

    @Test
    void archiveHorsDesBornesRefusee() {
        byte[] gz = ReglementaireFixtures.archive(Map.of("a.xml", "x".repeat(4000), "b.xml", "y".repeat(4000)));
        assertThrows(ArchiveTar.ArchiveInvalide.class,
                () -> new ArchiveTar(1, 1 << 20, 1 << 20).lire(new ByteArrayInputStream(gz), (n, c) -> { }),
                "nombre d'entrées");
        assertThrows(ArchiveTar.ArchiveInvalide.class,
                () -> new ArchiveTar(100, 1 << 20, 5000).lire(new ByteArrayInputStream(gz), (n, c) -> { }),
                "volume décompressé");
        assertThrows(ArchiveTar.ArchiveInvalide.class,
                () -> new ArchiveTar(100, 1 << 20, 1 << 20).lire(new ByteArrayInputStream(octets("pas une archive")),
                        (n, c) -> { }), "archive illisible");
    }
}
