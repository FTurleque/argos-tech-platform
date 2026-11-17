<script setup lang="ts">
// Mentions obligatoires d'un élément de veille (ADR-0023 point 4, AFF-02).
// Composant commun : chaque mise en page l'affiche tel quel et peut seulement l'habiller
// par sa propre feuille de style (classes `mentions*`).
import { computed } from 'vue'
import { urlSure } from './liens'
import type { MentionsElement } from '../mises-en-page/modele'

const props = defineProps<{ mentions: MentionsElement }>()

// Seules les URL http et https deviennent un lien : une source externe ne peut pas injecter
// de `javascript:` dans le portail ni dans le client desktop.
const lienSource = computed(() => urlSure(props.mentions.source.url))
const dateFormatee = computed(() => formater(props.mentions.publieLe))

function formater(iso: string | null): string {
  if (!iso) return 'non communiquée'
  const date = new Date(iso)
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
}
</script>

<template>
  <dl class="mentions" data-mentions>
    <div class="mentions-ligne">
      <dt>Source</dt>
      <dd data-mention="source">
        <a v-if="lienSource" :href="lienSource" rel="noopener noreferrer" target="_blank"
          >{{ mentions.source.nom }}<span class="mentions-hors-champ"> (nouvelle fenêtre)</span></a
        >
        <span v-else>{{ mentions.source.nom }}</span>
      </dd>
    </div>
    <div class="mentions-ligne">
      <dt>Date</dt>
      <dd data-mention="date">
        <time v-if="mentions.publieLe" :datetime="mentions.publieLe">{{ dateFormatee }}</time>
        <span v-else>{{ dateFormatee }}</span>
      </dd>
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
      <dd data-mention="validation">{{ mentions.validation }}</dd>
    </div>
    <div v-if="mentions.origines.length" class="mentions-ligne">
      <dt>Origine</dt>
      <dd data-mention="origine">{{ mentions.origines.join(' · ') }}</dd>
    </div>
    <div v-if="mentions.attribution" class="mentions-ligne mentions-pleine">
      <dt>Licence</dt>
      <dd data-mention="attribution">{{ mentions.attribution }}</dd>
    </div>
    <div v-if="mentions.gardeFous.length" class="mentions-ligne mentions-pleine">
      <dt>À savoir</dt>
      <dd>
        <ul class="mentions-garde-fous">
          <li v-for="(g, i) in mentions.gardeFous" :key="i" data-mention="garde-fou">{{ g }}</li>
        </ul>
      </dd>
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
  flex: none;
  font-weight: 600;
  white-space: nowrap;
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
.mentions-pleine {
  flex-basis: 100%;
}
.mentions-garde-fous {
  margin: 0;
  padding-left: 1.1rem;
}
/* Annonce « nouvelle fenêtre » réservée aux technologies d'assistance (RGAA 13.2). */
.mentions-hors-champ {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
