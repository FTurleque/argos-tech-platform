package io.argos.alerting;

import java.util.UUID;

/**
 * Message à diffuser : alerte immédiate ({@code IMMEDIATE}) ou résumé ({@code DAILY}, {@code WEEKLY})
 * pour un destinataire et un canal.
 *
 * @param adresse e-mail du destinataire, absente si son compte n'en a pas
 */
public record MessageAlerte(UUID alerteId, String nature, UUID compteId, String adresse, String canal, String titre,
        String gravite, ContenuAlerte contenu) {
}
