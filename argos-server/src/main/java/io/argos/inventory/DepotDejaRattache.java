package io.argos.inventory;

import io.argos.commun.RegleViolee;
import io.argos.scm.RepositoryRef;

/** Rattachement refusé : le projet a déjà un dépôt de même forge et de même identifiant (INV-12, réponse 409). */
public class DepotDejaRattache extends RegleViolee {

    private static final long serialVersionUID = 1L;

    DepotDejaRattache(RepositoryRef depot, Throwable cause) {
        super("DEPOT_DEJA_RATTACHE", "Le dépôt " + depot.provider() + " " + depot.repository() + " est déjà rattaché au projet.");
        initCause(cause);
    }
}
