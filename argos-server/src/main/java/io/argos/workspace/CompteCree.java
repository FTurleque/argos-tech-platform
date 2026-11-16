package io.argos.workspace;

/**
 * Événement CDI émis à la création d'un compte. La capacité {@code profils-veille} l'observe
 * pour activer le profil par défaut (Q-3 : {@code developpeur}, exigence PRF-01).
 */
public record CompteCree(Compte compte, String profilParDefaut) {
}
