<script setup lang="ts">
// Refus explicite quand le compte n'est pas créé faute d'invitation (ACC-09-S4 ; ACC-02, Q-2).
// L'écran ne propose aucune invitation : l'inscription se fait sur invitation d'un administrateur d'espace.
import { etatSession, seDeconnecter } from '../session/session'
</script>

<template>
  <section class="card">
    <h1>Accès refusé : aucun compte ARGOS</h1>
    <template v-if="etatSession.etat === 'CONNECTE' && etatSession.compteNonCree">
      <p class="status status-down" role="alert">
        Votre identité est reconnue par le fournisseur d'identité, mais aucun compte ARGOS n'a été créé
        pour elle : l'inscription se fait sur invitation et aucune invitation en attente ne correspond à
        votre adresse e-mail vérifiée.
      </p>
      <p class="muted">
        Demandez à un administrateur d'un espace ARGOS de vous inviter, puis reconnectez-vous.
      </p>
      <div class="actions">
        <button type="button" class="secondary" @click="seDeconnecter">Se déconnecter</button>
      </div>
    </template>
    <p v-else class="muted">
      Aucun refus en cours. <RouterLink to="/connexion">Se connecter</RouterLink>
    </p>
  </section>
</template>
