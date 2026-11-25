package io.argos.commun.securite;

import java.util.Optional;

/** Détermine l'appelant de la requête en cours et son périmètre. */
public interface CallerResolver {

    /** Appelant authentifié, ou vide si la requête n'est pas authentifiée. */
    Optional<Caller> current();
}
