<script setup lang="ts">
// Mentions obligatoires d'un élément de veille (ADR-0023 point 4, AFF-02).
// Composant commun : chaque mise en page l'affiche tel quel et peut seulement l'habiller
// par sa propre feuille de style (classes `mentions*`).
import { computed } from 'vue'
import { urlSure } from './liens'
import type { MentionsElement } from './modeles'

const props = defineProps<{ mentions: MentionsElement }>()

// Seules les URL http et https deviennent un lien : une source externe ne peut pas injecter
// de `javascript:` dans le portail ni dans le client desktop.
const lienSource = computed(() => urlSure(props.mentions.source.url))
const dateFormatee = computed(() => formater(props.mentions.publieLe))
const validationFormatee = computed(() =>
  props.mentions.validation ? formater(props.mentions.validation.le) : '',
)

function formater(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })
}
</script>

<template>
  <dl class="mentions" data-mentions>
    <div class="mentions-ligne">
      <dt>Source</dt>
      <dd data-mention="source">
        <a v-if="lienSource" :href="lienSource" rel="noopener noreferrer" target="_blank">{{
          mentions.source.nom
        }}</a>
        <span v-else>{{ mentions.source.nom }}</span>
      </dd>
    </div>
    <div class="mentions-ligne">
      <dt>Date</dt>
      <dd data-mention="date"><time :datetime="mentions.publieLe">{{ dateFormatee }}</time></dd>
    </div>
    <div v-if="mentions.niveauConfiance" class="mentions-ligne">
      <dt>Confiance</dt>
      <dd data-mention="confiance">{{ mentions.niveauConfiance.libelle }}</dd>
    </div>
    <div v-if="mentions.resumeProduitParIA" class="mentions-ligne mentions-ia">
      <dt>Résumé</dt>
      <dd data-mention="ia">produit par l'IA, à vérifier à la source</dd>
    </div>
    <div v-if="mentions.validation" class="mentions-ligne">
      <dt>Validation</dt>
      <dd data-mention="validation">validé par {{ mentions.validation.par }} le {{ validationFormatee }}</dd>
    </div>
  </dl>
</template>

<style scoped>
.mentions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1rem;
  margin: 0.5rem 0 0;
  font-size: 0.85em;
}
.mentions-ligne {
  display: flex;
  gap: 0.35rem;
}
.mentions dt {
  font-weight: 600;
}
.mentions dt::after {
  content: ' :';
}
.mentions dd {
  margin: 0;
}
.mentions-ia dd {
  font-style: italic;
}
</style>
