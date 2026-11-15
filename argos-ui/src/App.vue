<script setup lang="ts">
import { isDesktop } from './platform'
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-name">ARGOS</span>
        <span class="brand-motto">Observer. Corréler. Comprendre. Anticiper.</span>
      </div>
      <span class="badge">{{ isDesktop ? 'Application desktop' : 'Portail web' }}</span>
    </header>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
