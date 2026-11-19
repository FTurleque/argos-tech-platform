package io.argos.vulnintel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.signal.EcheanceSynchronisation;

/**
 * Rafraîchit les index des sources d'enrichissement à chaque échéance de la synchronisation planifiée
 * (COL-09-S5) : les index CERT-FR et KEV sont invalidés et relus à la première consultation suivante,
 * donc avant l'enrichissement des signaux de cette synchronisation. NVD et EPSS sont interrogés par CVE,
 * sans index. L'invalidation ne fait aucun appel réseau : une source indisponible ne bloque ni la
 * synchronisation ni l'enregistrement des signaux (COL-04-S3).
 */
@ApplicationScoped
public class RafraichissementIndex {

    private static final Logger LOG = Logger.getLogger(RafraichissementIndex.class);

    @Inject
    CertFrSource certFr;

    @Inject
    KevSource kev;

    void surEcheance(@Observes EcheanceSynchronisation echeance) {
        certFr.invalidate();
        kev.invalidate();
        LOG.debugf("index CERT-FR et KEV invalidés pour l'échéance du %s", echeance.instant());
    }
}
