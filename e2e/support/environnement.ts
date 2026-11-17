// Adresses de la pile de test de bout en bout (toutes sur la boucle locale).
// Le serveur écoute sur 8090 (et non sur 8080, port par défaut de quarkus:dev) pour ne pas entrer en
// conflit avec un autre service du poste ; le Keycloak des Dev Services garde le port 8180 fixé par
// %dev.quarkus.keycloak.devservices.port, émetteur par défaut de l'IHM et du client desktop.
export const URL_IHM = process.env.ARGOS_E2E_URL_IHM ?? 'http://localhost:5173'
export const URL_API = process.env.ARGOS_E2E_URL_API ?? 'http://localhost:8090'
export const EMETTEUR = process.env.ARGOS_E2E_EMETTEUR ?? 'http://localhost:8180/realms/argos'

/** Hôtes autorisés pendant les parcours : aucune requête ne quitte le poste. */
export const HOTES_LOCAUX = new Set(['localhost', '127.0.0.1', '[::1]'])
