<script setup lang="ts">
// Mise en page « journal » : quotidien contemporain, une et colonnes (ADR-0023).
// Couche mise en page : aucun appel réseau, aucun filtrage (AFF-02).
import { computed } from 'vue'
import MentionsObligatoires from '../../contenu/MentionsObligatoires.vue'
import type { ProprietesMiseEnPage } from '../../contenu/modeles'

const props = defineProps<ProprietesMiseEnPage>()
const une = computed(() => props.elements[0])
const suite = computed(() => props.elements.slice(1))
</script>

<template>
  <div class="journal">
    <header class="titre-journal">Le Quotidien d'Argos</header>
    <article v-if="une" class="une" data-element>
      <p class="surtitre">{{ une.rubriques.map((r) => r.libelle).join(' · ') }}</p>
      <h3>{{ une.titre }}</h3>
      <p class="chapo">{{ une.resume }}</p>
      <MentionsObligatoires :mentions="une.mentions" />
    </article>
    <div class="colonnes">
      <article v-for="element in suite" :key="element.identifiant" class="article" data-element>
        <p class="surtitre">{{ element.rubriques.map((r) => r.libelle).join(' · ') }}</p>
        <h3>{{ element.titre }}</h3>
        <p>{{ element.resume }}</p>
        <MentionsObligatoires :mentions="element.mentions" />
      </article>
    </div>
  </div>
</template>

<style scoped>
.journal {
  font-family: Georgia, 'Times New Roman', serif;
  background: #ffffff;
  color: #141414;
  padding: 1rem 1.25rem;
  border-top: 4px solid #141414;
}
.titre-journal {
  font-size: 1.9rem;
  font-weight: 700;
  text-align: center;
  border-bottom: 1px solid #141414;
  padding-bottom: 0.4rem;
  margin-bottom: 1rem;
}
.une h3 {
  font-size: 1.6rem;
  margin: 0.2rem 0;
}
.chapo {
  font-size: 1.1rem;
}
.surtitre {
  margin: 0;
  font-family: system-ui, sans-serif;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #8a1f11;
}
.colonnes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
  gap: 1.25rem;
  margin-top: 1rem;
  border-top: 1px solid #c8c8c8;
  padding-top: 1rem;
}
.article h3 {
  font-size: 1.15rem;
  margin: 0.2rem 0;
}
</style>
