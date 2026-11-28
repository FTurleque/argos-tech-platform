---
name: project-installation-poste
description: Installation locale d'ARGOS sur le poste du porteur (pile Docker idp-local port 8088, client desktop installé, recette R-1 à R-5)
metadata:
  type: project
---

Depuis le 2026-11-20, ARGOS tourne sur le poste du porteur : `docker compose --profile idp-local up -d --build` (postgres, keycloak 8180, argos sur 8088 car le port 8080 est pris par AgentService), secrets dans `.env` (hors Git), client desktop installé dans `%LOCALAPPDATA%\Programs\ARGOS` (construit avec `VITE_ARGOS_API_URL=http://localhost:8088` et `-c.extraMetadata.argos.apiUrl`).

**Why:** le porteur veut tester en usage réel et faire des retours ; les changes ont été archivés avec leurs vérifications réelles reportées en recette (`docs/deploiement/recette-poste.md`, R-1 à R-5).

**How to apply:** un écart de recette ouvre un nouveau change OpenSpec ; reconstruire le desktop avec powershell.exe dans le PATH (`$env:SystemRoot\System32\WindowsPowerShell\v1.0`) et l'argument `-c.extraMetadata…` entre guillemets ; ne pas modifier ADR-0027 / D-11 sans validation humaine.
