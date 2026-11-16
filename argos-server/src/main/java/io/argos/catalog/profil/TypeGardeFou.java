package io.argos.catalog.profil;

/**
 * Garde-fous qu'un profil peut imposer (ADR-0024 point 7, exigence PRF-06).
 *
 * <p>La liste est fermée : un manifeste ne peut déclarer qu'un garde-fou que le
 * serveur sait appliquer. Un nouveau type demande une évolution du code.
 */
public enum TypeGardeFou {
    /** Un élément n'est restitué qu'après validation par un expert (ADR-0018). */
    VALIDATION_HUMAINE,
    /** L'IA résume, elle ne décide jamais d'un impact (ADR-0013). */
    IA_SANS_DECISION,
    /** Aucun contenu restitué ne constitue un avis juridique (ADR-0018). */
    AUCUN_AVIS_JURIDIQUE
}
