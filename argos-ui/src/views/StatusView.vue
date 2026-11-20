<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, apiBaseUrl, fetchSystemInfo, type SystemInfo } from '../api/client'
import { isDesktop, notify } from '../platform'

type State = 'loading' | 'up' | 'down'

const state = ref<State>('loading')
const info = ref<SystemInfo | null>(null)
const error = ref<string>('')
const notificationResult = ref<string>('')

async function refresh(): Promise<void> {
  state.value = 'loading'
  error.value = ''
  try {
    info.value = await fetchSystemInfo()
    state.value = 'up'
  } catch (echec) {
    info.value = null
    error.value = echec instanceof ApiError ? echec.message : 'Erreur inattendue'
    state.value = 'down'
  }
}

async function testNotification(): Promise<void> {
  const shown = await notify('ARGOS — test', 'Les alertes critiques apparaîtront ainsi sur ce poste.')
  notificationResult.value = shown ? 'Notification envoyée.' : 'Notifications refusées ou indisponibles.'
}

onMounted(refresh)
</script>

<template>
  <section class="card">
    <h1>État du serveur</h1>
    <p class="muted">Serveur visé : <code>{{ apiBaseUrl }}</code></p>

    <p v-if="state === 'loading'" class="status">Connexion en cours…</p>

    <div v-else-if="state === 'up' && info" class="status status-up">
      <strong>Connecté</strong>
      <span>{{ info.application }} · version {{ info.version }} · API {{ info.apiVersion }}</span>
    </div>

    <div v-else class="status status-down">
      <strong>Hors ligne</strong>
      <span>{{ error }}</span>
      <span class="muted">
        Démarrez la base (<code>docker compose up -d postgres</code>)
        puis le serveur (<code>mvn quarkus:dev</code> dans <code>argos-server</code>).
      </span>
    </div>

    <div class="actions">
      <button type="button" @click="refresh">Réessayer</button>
      <button type="button" class="secondary" @click="testNotification">Tester une notification</button>
    </div>
    <p v-if="notificationResult" class="muted">{{ notificationResult }}</p>
  </section>

  <section class="card">
    <h2>Socle POC</h2>
    <p class="muted">
      Cette page vérifie la chaîne {{ isDesktop ? 'desktop' : 'navigateur' }} → API REST → serveur Quarkus → PostgreSQL.
      Les écrans de veille (fil de signaux, impacts, abonnements) arriveront avec les tâches du changement
      OpenSpec « formaliser-chaine-securite-poc ».
    </p>
  </section>
</template>
