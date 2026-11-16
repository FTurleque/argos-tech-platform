<script setup lang="ts">
// Mise en page « classique » : application de travail, liste sobre (ADR-0023).
// Couche mise en page : aucun appel réseau, aucun filtrage (AFF-02).
import MentionsObligatoires from '../../contenu/MentionsObligatoires.vue'
import type { ProprietesMiseEnPage } from '../../contenu/modeles'

defineProps<ProprietesMiseEnPage>()
</script>

<template>
  <div class="classique">
    <ul class="liste">
      <li v-for="element in elements" :key="element.identifiant" class="ligne" data-element>
        <div class="entete">
          <span v-if="element.gravite" class="gravite" :class="`gravite-${element.gravite}`">{{ element.gravite }}</span>
          <h3>{{ element.titre }}</h3>
        </div>
        <p>{{ element.resume }}</p>
        <p class="rubriques">
          <span v-for="r in element.rubriques" :key="`${r.profil}-${r.identifiant}`" class="rubrique">{{ r.libelle }}</span>
        </p>
        <MentionsObligatoires :mentions="element.mentions" />
      </li>
    </ul>
  </div>
</template>

<style scoped>
.classique {
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
}
.liste {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}
.ligne {
  background: #ffffff;
  border: 1px solid #d5dbe5;
  border-radius: 8px;
  padding: 0.9rem 1.1rem;
  color: #1c2533;
}
.entete {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}
h3 {
  margin: 0;
  font-size: 1.05rem;
}
.gravite {
  font-size: 0.75rem;
  text-transform: uppercase;
  padding: 0.1rem 0.45rem;
  border-radius: 4px;
  background: #e7ecf3;
}
.gravite-critique {
  background: #9b1c1c;
  color: #ffffff;
}
.rubriques {
  display: flex;
  gap: 0.4rem;
  margin: 0.25rem 0;
}
.rubrique {
  font-size: 0.8rem;
  border: 1px solid #9aa7b8;
  border-radius: 999px;
  padding: 0 0.5rem;
}
</style>
