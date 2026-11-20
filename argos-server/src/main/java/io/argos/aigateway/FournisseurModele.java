package io.argos.aigateway;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Fournisseur de modèles, connu de la seule passerelle (arc42 §5.5 règle 3). L'implémentation livrée
 * est {@code io.argos.aigateway.ollama.FournisseurOllama} (fournisseur local, coût nul) ; le contrat
 * reste indépendant du fournisseur (R-08).
 */
public interface FournisseurModele {

    /** Requête : modèle, consignes système, message utilisateur (données balisées) et schéma JSON de sortie. */
    record Requete(String modele, String consignes, String message, Map<String, Object> schema) {
    }

    /** Réponse brute : texte produit, modèle effectif, jetons d'entrée et de sortie, coût en euros. */
    record Reponse(String texte, String modele, int jetonsEntree, int jetonsSortie, BigDecimal coutEur) {
    }

    /** Nom court du fournisseur, tracé avec chaque appel. */
    String nom();

    /**
     * Exécute la requête. Lève {@link IOException} si le fournisseur est injoignable, répond en erreur,
     * renvoie une réponse illisible ou dépasse le délai configuré, et {@link InterruptedException} si
     * l'attente de la réponse est interrompue.
     */
    Reponse executer(Requete requete) throws IOException, InterruptedException;
}
