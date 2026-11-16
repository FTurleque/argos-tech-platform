-- Module ai-gateway : passerelle unique vers les modèles d'IA (changement OpenSpec encadrer-appels-ia,
-- IAG-01 à IAG-05). Fournisseur local Ollama, coût nul (Q-1) ; plafond de charge journalier de
-- l'instance (Q-1) et quota journalier par espace (Q-2), paramétrables.
--
-- Les trois tables ne sont lues et écrites que par les modules ai-gateway et audit, sous l'utilisateur
-- de connexion : le rôle applicatif argos_app n'y a aucun droit (ADR-0016, moindre privilège). Aucune
-- donnée envoyée au modèle ni aucune sortie n'y est recopiée : seule une empreinte des entrées est
-- conservée (décision D2).

-- Registre des prompts versionnés : un texte de prompt publié sous une version n'en change plus.
-- Le serveur vérifie au démarrage que chaque prompt du code correspond à l'empreinte enregistrée.
CREATE TABLE ia_prompt (
    tache          varchar(100) NOT NULL,
    version        integer      NOT NULL CHECK (version >= 1),
    empreinte      varchar(64)  NOT NULL,
    enregistre_le  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tache, version)
);

-- Journal des appels et des refus de la passerelle (IAG-01, IAG-02, IAG-03, IAG-04).
CREATE TABLE ia_appel (
    id                 bigserial     PRIMARY KEY,
    horodatage         timestamptz   NOT NULL DEFAULT now(),
    tache              varchar(100)  NOT NULL,
    version_prompt     integer       NOT NULL CHECK (version_prompt >= 1),
    fournisseur        varchar(32)   NOT NULL,
    modele             varchar(200)  NOT NULL,
    espace_id          uuid,
    issue              varchar(32)   NOT NULL CHECK (issue IN ('PRODUIT', 'REFUS_CLASSE', 'REFUS_DONNEE_SENSIBLE',
                                                               'PLAFOND_INSTANCE', 'QUOTA_ESPACE', 'SORTIE_INVALIDE',
                                                               'INDISPONIBLE')),
    jetons_entree      integer       NOT NULL DEFAULT 0 CHECK (jetons_entree >= 0),
    jetons_sortie      integer       NOT NULL DEFAULT 0 CHECK (jetons_sortie >= 0),
    cout_eur           numeric(12,6) NOT NULL DEFAULT 0 CHECK (cout_eur >= 0),
    empreinte_entrees  varchar(64)   NOT NULL,
    donnees_masquees   integer       NOT NULL DEFAULT 0 CHECK (donnees_masquees >= 0),
    duree_ms           integer       NOT NULL DEFAULT 0 CHECK (duree_ms >= 0)
);
CREATE INDEX ia_appel_horodatage_idx ON ia_appel (horodatage);
CREATE INDEX ia_appel_espace_idx ON ia_appel (espace_id, horodatage);

-- Compteurs journaliers d'appels transmis au fournisseur : portée INSTANCE ou identifiant d'espace.
-- La réservation d'un appel incrémente atomiquement le compteur tant qu'il est sous la limite.
CREATE TABLE ia_quota_jour (
    jour    date        NOT NULL,
    portee  varchar(36) NOT NULL,
    appels  integer     NOT NULL DEFAULT 0 CHECK (appels >= 0),
    PRIMARY KEY (jour, portee)
);

REVOKE ALL ON ia_prompt, ia_appel, ia_quota_jour FROM PUBLIC;
