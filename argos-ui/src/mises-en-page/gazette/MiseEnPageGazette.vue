<script setup lang="ts">
// Mise en page « gazette » : presse imprimée du XIXe siècle (ADR-0023).
// Couche mise en page : aucun appel réseau, aucun filtrage (AFF-02).
import MentionsObligatoires from '../../contenu/MentionsObligatoires.vue'
import type { ProprietesMiseEnPage } from '../../contenu/modeles'

defineProps<ProprietesMiseEnPage>()
</script>

<template>
  <div class="gazette">
    <header class="titre-gazette">
      <span class="filet" />
      <span>La Gazette d'Argos</span>
      <span class="filet" />
    </header>
    <article v-for="element in elements" :key="element.identifiant" class="avis" data-element>
      <p v-if="element.gravite === 'critique'" class="avis-recherche">Avis de recherche</p>
      <h3>{{ element.titre }}</h3>
      <p class="rubriques">{{ element.rubriques.map((r) => r.libelle).join(' — ') }}</p>
      <p>{{ element.resume }}</p>
      <MentionsObligatoires :mentions="element.mentions" />
    </article>
  </div>
</template>

<style scoped>
.gazette {
  font-family: 'Book Antiqua', Palatino, Georgia, serif;
  background: #f3e8cf;
  color: #2b2116;
  padding: 1rem 1.25rem;
  border: 1px solid #6b5536;
}
.titre-gazette {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  font-size: 1.8rem;
  font-variant: small-caps;
  letter-spacing: 0.08em;
  margin-bottom: 1rem;
}
.filet {
  flex: 1;
  border-top: 3px double #2b2116;
}
.avis {
  border-top: 1px solid #6b5536;
  padding: 0.8rem 0;
}
.avis-recherche {
  margin: 0;
  text-align: center;
  font-size: 1.2rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.2em;
}
h3 {
  margin: 0.2rem 0;
  font-size: 1.2rem;
  text-align: center;
}
.rubriques {
  margin: 0;
  text-align: center;
  font-style: italic;
  font-size: 0.85rem;
}
</style>
