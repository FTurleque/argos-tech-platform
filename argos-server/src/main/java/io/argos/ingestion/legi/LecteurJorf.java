package io.argos.ingestion.legi;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lecture des fichiers XML des jeux de données JORF de la DILA : versions de texte ({@code TEXTE_VERSION})
 * et articles ({@code ARTICLE}), assemblés en {@link TexteJorf}.
 *
 * <p>Le XML est une entrée non fiable : toute déclaration de type de document ({@code DOCTYPE}) est
 * refusée, les DTD et les entités externes sont désactivées (aucune attaque XXE ni expansion
 * d'entités), et chaque valeur lue est bornée. Le balisage des contenus ({@code <p>}, {@code <br/>})
 * est réduit à du texte brut.
 */
public final class LecteurJorf {

    /** Longueur maximale du contenu assemblé d'un texte (caractères). */
    public static final int CONTENU_MAX = 20000;
    private static final int TITRE_MAX = 2000;
    private static final int CHAMP_MAX = 500;
    private static final int LIENS_MAX = 200;

    /** Fichier XML refusé (DOCTYPE, XML mal formé). */
    public static final class XmlRefuse extends RuntimeException {
        public XmlRefuse(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Élément lu dans un fichier : version de texte ou article. */
    public sealed interface Element permits Version, Article {
    }

    /** Version de texte, sans ses articles. */
    public record Version(TexteJorf texte) implements Element {
    }

    /** Article d'un texte du Journal officiel. */
    public record Article(String cidTexte, String numero, String contenu) implements Element {
    }

    private static final XMLInputFactory FABRIQUE = fabrique();

    private LecteurJorf() {
    }

    static XMLInputFactory fabrique() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        f.setProperty(XMLInputFactory.IS_COALESCING, true);
        return f;
    }

    /**
     * Lit un fichier XML de l'archive ; vide pour un fichier d'un autre type (structure, sommaire).
     *
     * @throws XmlRefuse pour un fichier mal formé ou qui déclare un type de document
     */
    public static Optional<Element> lire(byte[] xml, String archive) {
        XMLStreamReader r = null;
        try {
            r = FABRIQUE.createXMLStreamReader(new ByteArrayInputStream(xml));
            Lecture l = new Lecture();
            while (r.hasNext()) {
                int evenement = r.next();
                switch (evenement) {
                    case XMLStreamConstants.DTD -> throw new XmlRefuse("déclaration DOCTYPE refusée", null);
                    case XMLStreamConstants.ENTITY_REFERENCE -> throw new XmlRefuse("référence d'entité refusée", null);
                    case XMLStreamConstants.START_ELEMENT -> l.debut(r);
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> l.texte(r.getText());
                    case XMLStreamConstants.END_ELEMENT -> l.fin();
                    default -> {
                    }
                }
            }
            return l.resultat(archive);
        } catch (XMLStreamException e) {
            throw new XmlRefuse("XML mal formé : " + e.getMessage(), e);
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (XMLStreamException e) {
                    // fermeture sans effet sur le résultat
                }
            }
        }
    }

    /**
     * Assemble les versions et les articles lus : chaque texte reçoit sa notice, son nota et ses
     * articles dans l'ordre de lecture, dans la limite de {@link #CONTENU_MAX} caractères.
     */
    public static List<TexteJorf> assembler(Collection<Element> elements) {
        Map<String, TexteJorf> textes = new LinkedHashMap<>();
        Map<String, StringBuilder> articles = new LinkedHashMap<>();
        for (Element e : elements) {
            if (e instanceof Version v) {
                textes.putIfAbsent(v.texte().identifiant(), v.texte());
            } else if (e instanceof Article a) {
                StringBuilder b = articles.computeIfAbsent(a.cidTexte(), k -> new StringBuilder());
                if (b.length() < CONTENU_MAX) {
                    b.append(a.numero() == null ? "Article" : "Article " + a.numero()).append(" : ").append(a.contenu())
                            .append('\n');
                }
            }
        }
        List<TexteJorf> resultat = new ArrayList<>();
        for (TexteJorf t : textes.values()) {
            StringBuilder b = new StringBuilder(t.contenu());
            StringBuilder art = articles.get(t.identifiant());
            if (art != null) {
                if (b.length() > 0) {
                    b.append('\n');
                }
                b.append(art);
            }
            resultat.add(t.avec(borner(b.toString().strip(), CONTENU_MAX), t.archive()));
        }
        return resultat;
    }

    static String borner(String s, int max) {
        if (s == null) {
            return null;
        }
        String propre = s.replaceAll("[\\p{Cntrl}&&[^\n]]", " ").replaceAll("[ \\t]+", " ").strip();
        return propre.length() <= max ? propre : propre.substring(0, max);
    }

    private static LocalDate date(String s) {
        if (s == null || s.isBlank() || s.startsWith("2999")) {
            return null;
        }
        try {
            return LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** État de lecture d'un document. */
    private static final class Lecture {
        private final Deque<String> chemin = new ArrayDeque<>();
        private final Map<String, StringBuilder> champs = new LinkedHashMap<>();
        private final StringBuilder notice = new StringBuilder();
        private final StringBuilder contenuArticle = new StringBuilder();
        private final List<TexteJorf.CodeModifie> codes = new ArrayList<>();
        private final Set<String> articlesModifies = new LinkedHashSet<>();
        /** Champs déjà lus : seule la première occurrence d'un élément est retenue. */
        private final Set<String> termines = new LinkedHashSet<>();
        private String racine;
        private String cidContexte;
        private Map<String, String> lien;
        private final StringBuilder texteLien = new StringBuilder();

        void debut(XMLStreamReader r) {
            String nom = r.getLocalName();
            if (racine == null) {
                racine = nom;
            }
            chemin.push(nom);
            if ("LIEN".equals(nom) && codes.size() + articlesModifies.size() < LIENS_MAX) {
                lien = new LinkedHashMap<>();
                for (int i = 0; i < r.getAttributeCount(); i++) {
                    lien.put(r.getAttributeLocalName(i), r.getAttributeValue(i));
                }
                texteLien.setLength(0);
            }
            if ("TEXTE".equals(nom) && "CONTEXTE".equals(parent(1)) && cidContexte == null) {
                cidContexte = r.getAttributeValue(null, "cid");
            }
            if ("br".equalsIgnoreCase(nom) || "p".equalsIgnoreCase(nom)) {
                texte("\n");
            }
        }

        void texte(String t) {
            if (chemin.isEmpty()) {
                return;
            }
            String courant = chemin.peek();
            if (lien != null) {
                ajouter(texteLien, t, CHAMP_MAX);
                return;
            }
            if (dans("NOTICE") || dans("NOTA")) {
                ajouter(notice, t, CONTENU_MAX);
            } else if ("ARTICLE".equals(racine) && dans("BLOC_TEXTUEL")) {
                ajouter(contenuArticle, t, CONTENU_MAX);
            } else if (!termines.contains(courant)) {
                StringBuilder b = champs.computeIfAbsent(courant, k -> new StringBuilder());
                ajouter(b, t, TITRE_MAX);
            }
        }

        void fin() {
            String nom = chemin.pop();
            if (champs.containsKey(nom)) {
                termines.add(nom);
            }
            if ("LIEN".equals(nom) && lien != null) {
                enregistrerLien();
                lien = null;
            }
            if ("p".equalsIgnoreCase(nom)) {
                texte("\n");
            }
        }

        private void enregistrerLien() {
            if (!"MODIFIE".equalsIgnoreCase(lien.getOrDefault("typelien", ""))
                    || !"cible".equalsIgnoreCase(lien.getOrDefault("sens", ""))
                    || !"CODE".equalsIgnoreCase(lien.getOrDefault("naturetexte", ""))) {
                return;
            }
            String cid = lien.get("cidtexte");
            String libelle = borner(texteLien.toString(), 300);
            int coupure = libelle.indexOf(" - art.");
            if (coupure > 0) {
                libelle = libelle.substring(0, coupure).strip();
            }
            if (libelle.isEmpty() || cid == null || !cid.matches("LEGITEXT\\d{12}")) {
                return;
            }
            String code = libelle;
            if (codes.stream().noneMatch(c -> c.identifiant().equals(cid))) {
                codes.add(new TexteJorf.CodeModifie(cid, code));
            }
            String num = lien.get("num");
            if (num != null && !num.isBlank()) {
                articlesModifies.add(borner(code + ", art. " + num, 300));
            }
        }

        private boolean dans(String element) {
            return chemin.contains(element);
        }

        private String parent(int rang) {
            return chemin.stream().skip(rang).findFirst().orElse(null);
        }

        private String champ(String nom) {
            StringBuilder b = champs.get(nom);
            if (b == null) {
                return null;
            }
            String v = borner(b.toString(), TITRE_MAX);
            return v.isEmpty() ? null : v;
        }

        private static void ajouter(StringBuilder b, String t, int max) {
            if (b.length() < max) {
                b.append(t, 0, Math.min(t.length(), max - b.length()));
            }
        }

        Optional<Element> resultat(String archive) {
            if ("TEXTE_VERSION".equals(racine)) {
                String id = champ("ID");
                LocalDate publication = date(champ("DATE_PUBLI"));
                String titre = champ("TITREFULL") != null ? champ("TITREFULL") : champ("TITRE");
                if (id == null || !id.matches("[A-Z]{4}TEXT\\d{12}") || publication == null || titre == null) {
                    return Optional.empty();
                }
                return Optional.of(new Version(new TexteJorf(id, borne(champ("NATURE")), borne(champ("NUM")),
                        borne(champ("NOR")), borne(champ("MINISTERE")), titre, publication, date(champ("DATE_TEXTE")),
                        codes, List.copyOf(articlesModifies), borner(notice.toString(), CONTENU_MAX), archive)));
            }
            if ("ARTICLE".equals(racine) && cidContexte != null && cidContexte.matches("[A-Z]{4}TEXT\\d{12}")) {
                return Optional.of(new Article(cidContexte, borne(champ("NUM")),
                        borner(contenuArticle.toString(), CONTENU_MAX)));
            }
            return Optional.empty();
        }

        private static String borne(String v) {
            return v == null ? null : borner(v, CHAMP_MAX);
        }
    }
}
