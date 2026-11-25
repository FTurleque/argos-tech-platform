package io.argos.inventory;

/** Issue de l'import de l'inventaire d'un dépôt ou d'un projet (INV-01, INV-07). */
public enum StatutImport {

    /** Inventaire lu et remplacé. */
    IMPORTED,

    /** Document refusé : illisible, trop gros ou invalide. */
    REJECTED,

    /** Inventaire conservé : aucune nouvelle SBOM disponible. */
    KEPT,

    /** Aucune source lisible : ni SBOM ni manifeste. */
    NONE,

    /** Échec de lecture ou de la forge ; l'inventaire précédent est conservé. */
    ERROR,

    /** Quota de la forge épuisé pendant ce passage : dépôt non interrogé. */
    DEFERRED
}
