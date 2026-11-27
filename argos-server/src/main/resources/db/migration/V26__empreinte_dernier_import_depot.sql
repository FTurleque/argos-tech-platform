-- Empreinte du document du dernier import réussi de chaque dépôt (INV-14, changement retenir-importer-et-paginer) :
-- SHA-256 en hexadécimal de la SBOM ou des manifestes lus. Un passage planifié qui relit un document de même empreinte
-- conserve l'inventaire sans l'écrire ni le recorréler. Une ligne sans empreinte est réécrite à son prochain import.
ALTER TABLE repository_inventory
    ADD COLUMN empreinte varchar(64)
        CONSTRAINT repository_inventory_empreinte_ck CHECK (empreinte ~ '^[0-9a-f]{64}$');
