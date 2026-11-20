package io.argos.regintel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fiche d'analyse d'un texte officiel pour un espace (REG-04 à REG-08), telle que restituée à un membre
 * de l'espace : une fiche non validée n'est restituée qu'aux experts de ses domaines (REG-05).
 *
 * @param texte              texte officiel analysé et liens vers sa publication et sa version consolidée
 * @param etatValidation     {@code EN_ATTENTE_VALIDATION}, {@code VALIDEE} ou {@code REJETEE}
 * @param etatSuivi          état du cycle de vie du texte dans l'espace (REG-06)
 * @param resume             résumé de la fiche, absent s'il reste à rédiger
 * @param resumeOrigine      {@code IA}, {@code EXPERT} ou {@code AUCUN}
 * @param mentionIa          « produit par l'IA » tant que le résumé vient du modèle, absente sinon
 * @param dateEffet          date d'entrée en vigueur retenue, absente : {@link #dateEffetLibelle} « date à confirmer »
 * @param dateEffetOrigine   {@code TEXTE} (extraite du texte) ou {@code EXPERT} (corrigée), absente sans date
 * @param dateEffetIa        date d'effet proposée par l'IA, jamais retenue sans l'expert
 * @param domaines           domaines rattachés, avec leur origine (règle, proposition IA, expert)
 * @param demande            demande d'évolution associée dans un outil de suivi externe (REG-08), absente sinon
 * @param peutExpertiser     vrai si l'utilisateur courant est expert d'un des domaines de la fiche
 * @param mentionValidation  « validée par l'expert métier » ou « en attente de validation »
 * @param mentionAvisJuridique rappel : la fiche informe et ne constitue pas un avis juridique
 * @param mentionSource      mention de la source, de sa licence (Licence Ouverte) et de la date de dernière mise à
 *                           jour des données
 */
public record FicheAnalyse(UUID id, UUID espaceId, Texte texte, String etatValidation, CycleVie.Etat etatSuivi,
        String libelleSuivi, String resume, String resumeOrigine, String mentionIa, LocalDate dateEffet,
        String dateEffetOrigine, String dateEffetLibelle, String motifDateEffet, LocalDate dateEffetIa,
        List<String> articlesModifies, List<DomaineRattache> domaines, Demande demande, Instant decideLe,
        boolean peutExpertiser, String mentionValidation, String mentionAvisJuridique, String mentionSource,
        List<EvenementHistorique> historique) {

    /** Mention de validation d'une fiche validée par un expert de ses domaines (PRF-06-S2). */
    public static final String MENTION_VALIDEE = "validée par l'expert métier";

    /** Mention de chaque fiche (ADR-0018, garde-fou AUCUN_AVIS_JURIDIQUE du profil {@code expert-metier}). */
    public static final String AVIS_JURIDIQUE = "Les fiches informent ; elles ne constituent pas un avis juridique (ADR-0018).";

    /** Mention de la source des textes (Licence Ouverte / Open Licence, Etalab). */
    public static final String SOURCE = "Source : Direction de l'information légale et administrative (DILA), jeux de données"
            + " JORF, Licence Ouverte / Open Licence 2.0 ; texte faisant foi : Journal officiel de la République française.";

    private static final Pattern ARCHIVE = Pattern.compile("JORF_(\\d{8})-\\d{6}\\.tar\\.gz");

    /**
     * Mention de la source d'une fiche : {@link #SOURCE} et date de dernière mise à jour des données, exigée
     * par la Licence Ouverte dans la mention de paternité.
     */
    public static String mentionSource(LocalDate miseAJour) {
        return miseAJour == null ? SOURCE : SOURCE + " Dernière mise à jour des données : " + miseAJour + ".";
    }

    /**
     * Date de dernière mise à jour des données d'un texte : date de l'archive DILA qui l'a apporté
     * ({@code JORF_AAAAMMJJ-HHMMSS.tar.gz}), à défaut date de sa collecte.
     */
    static LocalDate dateMiseAJour(String archive, Instant collecteLe) {
        if (archive != null) {
            Matcher correspondance = ARCHIVE.matcher(archive);
            if (correspondance.matches()) {
                try {
                    return LocalDate.parse(correspondance.group(1), DateTimeFormatter.BASIC_ISO_DATE);
                } catch (DateTimeParseException invalide) {
                    // nom d'archive mal daté : date de collecte
                }
            }
        }
        return collecteLe == null ? null : LocalDate.ofInstant(collecteLe, ZoneOffset.UTC);
    }

    public FicheAnalyse {
        articlesModifies = List.copyOf(articlesModifies);
        domaines = List.copyOf(domaines);
        historique = historique == null ? List.of() : List.copyOf(historique);
    }

    /** Texte officiel et liens. */
    public record Texte(String identifiant, String nature, String numero, String nor, String ministere, String titre,
            LocalDate datePublication, List<TexteOfficiel.Code> codesModifies, String lienPublie, String lienConsolide) {
        public Texte {
            codesModifies = List.copyOf(codesModifies);
        }
    }

    /** Domaine rattaché ; {@code confiance} n'est renseignée que pour une proposition de l'IA. */
    public record DomaineRattache(String code, String libelle, String origine, Integer confiance, String motif) {
    }

    /** Demande d'évolution associée : adresse, forge et dernier état lu (absent si la forge n'a pas répondu). */
    public record Demande(String adresse, String forge, String etat) {
    }

    /** Événement de l'historique de la fiche. */
    public record EvenementHistorique(String action, String detail, UUID auteur, Instant le) {
    }
}
