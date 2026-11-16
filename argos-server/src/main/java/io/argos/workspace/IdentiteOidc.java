package io.argos.workspace;

/**
 * Identité établie par le fournisseur d'identité OIDC à partir d'un jeton déjà validé.
 *
 * @param sujet       identifiant stable de la personne chez le fournisseur ({@code sub})
 * @param nom         nom affiché
 * @param email       adresse e-mail, éventuellement absente
 * @param emailVerifie {@code true} si le fournisseur atteste l'adresse
 */
public record IdentiteOidc(String sujet, String nom, String email, boolean emailVerifie) {

    /** Adresse utilisable pour rapprocher une invitation : vérifiée et normalisée, sinon {@code null}. */
    public String emailVerifieNormalise() {
        return emailVerifie && email != null && !email.isBlank() ? email.trim().toLowerCase(java.util.Locale.ROOT) : null;
    }
}
