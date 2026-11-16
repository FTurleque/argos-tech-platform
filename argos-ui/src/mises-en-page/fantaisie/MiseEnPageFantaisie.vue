<script setup lang="ts">
// Mise en page « fantaisie » : magazine décalé et coloré (ADR-0023).
// Couche mise en page : aucun appel réseau, aucun filtrage (AFF-02).
import MentionsObligatoires from '../../contenu/MentionsObligatoires.vue'
import type { ProprietesMiseEnPage } from '../../contenu/modeles'

defineProps<ProprietesMiseEnPage>()
</script>

<template>
  <div class="fantaisie">
    <article
      v-for="(element, index) in elements"
      :key="element.identifiant"
      class="carte"
      :class="`teinte-${index % 3}`"
      data-element
    >
      <p class="pastilles">
        <span v-for="r in element.rubriques" :key="`${r.profil}-${r.identifiant}`" class="pastille">{{ r.libelle }}</span>
      </p>
      <h3>{{ element.titre }}</h3>
      <p>{{ element.resume }}</p>
      <MentionsObligatoires :mentions="element.mentions" />
    </article>
  </div>
</template>

<style scoped>
.fantaisie {
  font-family: 'Trebuchet MS', 'Segoe UI', system-ui, sans-serif;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 1rem;
}
.carte {
  border-radius: 18px;
  padding: 1rem 1.2rem;
  color: #1d1033;
  box-shadow: 4px 4px 0 #1d1033;
  border: 2px solid #1d1033;
}
.teinte-0 {
  background: #ffe36e;
}
.teinte-1 {
  background: #b9f0d8;
}
.teinte-2 {
  background: #ffc9de;
}
h3 {
  margin: 0.3rem 0;
  font-size: 1.15rem;
}
.pastilles {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin: 0;
}
.pastille {
  background: #1d1033;
  color: #ffffff;
  border-radius: 999px;
  padding: 0.05rem 0.6rem;
  font-size: 0.75rem;
}
</style>
