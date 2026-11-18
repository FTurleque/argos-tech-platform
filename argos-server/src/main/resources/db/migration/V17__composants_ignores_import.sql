-- Composants ignorés faute de PURL, signalés avec l'inventaire (changement OpenSpec
-- formaliser-chaine-securite-poc ; exigence INV-01 ; décision D6 du porteur du projet, 2026-11-18).
-- L'inventaire reste fondé sur le PURL : ces composants ne sont pas des lignes de component et
-- n'alimentent pas la corrélation. Le dernier import en conserve le nombre total et la description
-- (nom, groupe, version, motif) des 100 premiers.
--
-- Les colonnes s'ajoutent à la table inventory : elles sont lisibles sous argos_app par le privilège
-- SELECT et la politique RLS inventory_lecture de la migration V4 (ADR-0016), sans nouvelle politique.

ALTER TABLE inventory
    ADD COLUMN ignored_count      integer NOT NULL DEFAULT 0 CHECK (ignored_count >= 0),
    ADD COLUMN ignored_components jsonb   NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(ignored_components) = 'array' AND jsonb_array_length(ignored_components) <= 100);
